import { fetchCurrentRevisions, sourceManifest, type WikiSource } from './wiki.js';

export const DIFF_COLLECTIONS = { quests: 'quests', diaries: 'diaries', milestones: 'milestones', gathering: 'plans' } as const;
export type DiffKind = keyof typeof DIFF_COLLECTIONS;
type JsonObject = Record<string, unknown>;

function object(value: unknown, context: string): JsonObject {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) throw new Error(`${context}: expected an object`);
  return value as JsonObject;
}

/** A legacy file is explicitly unpinned, not silently reported as up to date. */
export function parseSources(json: string, filename: string): WikiSource[] | undefined {
  const root = object(JSON.parse(json), filename);
  if (root.sources === undefined) return undefined;
  if (!Array.isArray(root.sources)) throw new Error(`${filename}.sources: expected an array`);
  return sourceManifest(root.sources.map((value, index) => {
    const row = object(value, `${filename}.sources[${index}]`);
    if (typeof row.title !== 'string' || !row.title || typeof row.revid !== 'number'
      || !Number.isSafeInteger(row.revid) || row.revid <= 0 || typeof row.timestamp !== 'string'
      || !/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ$/.test(row.timestamp) || !Number.isFinite(Date.parse(row.timestamp))) {
      throw new Error(`${filename}.sources[${index}]: expected title, positive revid and UTC timestamp`);
    }
    return { title: row.title, revid: row.revid, timestamp: row.timestamp };
  }));
}

export async function checkReport(
  files: ReadonlyMap<string, string>,
  currentRevisions: typeof fetchCurrentRevisions = fetchCurrentRevisions,
): Promise<string> {
  const manifests = [...files].sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0)
    .map(([name, json]) => ({ name, sources: parseSources(json, name) }));
  const titles = [...new Set(manifests.flatMap(({ sources }) => sources?.map((source) => source.title) ?? []))].sort();
  const current = await currentRevisions(titles);
  const lines: string[] = [];
  for (const { name, sources } of manifests) {
    lines.push(`${name}:`);
    if (sources === undefined) {
      lines.push('  UNPINNED: no source manifest; regenerate to record fetched revisions.');
    } else if (sources.length === 0) {
      lines.push('  No revision-addressed page sources recorded (curated/Bucket/prices data).');
    } else {
      let moved = false;
      for (const source of sources) {
        if (!current.has(source.title)) throw new Error(`Current revision response omitted ${source.title}`);
        const live = current.get(source.title);
        if (live === null) {
          lines.push(`  MISSING ${source.title}: recorded ${source.revid} (${source.timestamp})`);
          moved = true;
        } else if (live && live.revid !== source.revid) {
          lines.push(`  MOVED ${source.title}: ${source.revid} (${source.timestamp}) -> ${live.revid} (${live.timestamp})`);
          moved = true;
        }
      }
      if (!moved) lines.push(`  Unchanged (${sources.length} recorded revisions).`);
    }
  }
  return lines.join('\n') + '\n';
}

function entries(json: string | undefined, kind: DiffKind): Map<string, JsonObject> {
  if (json === undefined) return new Map();
  const root = object(JSON.parse(json), `${kind}.json`);
  const rows = root[DIFF_COLLECTIONS[kind]];
  if (!Array.isArray(rows)) throw new Error(`${kind}.json: expected ${DIFF_COLLECTIONS[kind]} array`);
  const result = new Map<string, JsonObject>();
  for (const value of rows) {
    const row = object(value, `${kind} entry`);
    const id = kind === 'diaries' ? `diary:${row.area}_${row.tier}`
      : kind === 'quests' ? `quest:${row.id}` : kind === 'gathering' ? `gathering:${row.id}` : String(row.id);
    if ((kind === 'diaries' && (typeof row.area !== 'string' || typeof row.tier !== 'string'))
      || (kind !== 'diaries' && typeof row.id !== 'string' && typeof row.id !== 'number')) {
      throw new Error(`${kind}.json: entry has no identifier`);
    }
    if (result.has(id)) throw new Error(`${kind}.json: duplicate entry ${id}`);
    result.set(id, row);
  }
  return result;
}

const QUEST_REQUIREMENTS = ['skills', 'prereqs', 'prereqsStarted', 'prereqNotes', 'items', 'requirements', 'combatLevel'];
const TASK_REQUIREMENTS = ['skills', 'quests', 'items', 'notes', 'requirements', 'combatLevel'];

function requirementLines(row: JsonObject, kind: DiffKind): Map<string, string> {
  const lines = new Map<string, string>();
  function flatten(value: unknown, path: string): void {
    if (value !== null && typeof value === 'object') {
      for (const [key, child] of Object.entries(value).sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0)) {
        flatten(child, Array.isArray(value) ? `${path}[${key}]` : `${path}.${key}`);
      }
      if (Object.keys(value).length === 0) lines.set(path, Array.isArray(value) ? '[]' : '{}');
    } else {
      lines.set(path, JSON.stringify(value));
    }
  }
  function fields(value: JsonObject, keys: string[], prefix = ''): void {
    for (const key of keys) if (key in value) flatten(value[key], `${prefix}${key}`);
  }
  if (kind === 'quests') fields(row, QUEST_REQUIREMENTS);
  if (kind === 'milestones') fields(row, ['requirements', 'recommended']);
  if (kind === 'diaries') {
    if (!Array.isArray(row.tasks)) throw new Error('Diary entry has no tasks array');
    for (const value of row.tasks) {
      const task = object(value, 'diary task');
      fields(task, TASK_REQUIREMENTS, `tasks[${task.ordinal}].`);
    }
  }
  if (kind === 'gathering') {
    fields(row, ['requires', 'risk']);
    const steps = (value: unknown, prefix: string): void => {
      if (!Array.isArray(value)) return;
      value.forEach((step, index) => {
        if (step !== null && typeof step === 'object' && !Array.isArray(step)) fields(object(step, prefix), ['requires'], `${prefix}[${index}].`);
      });
    };
    steps(row.steps, 'steps');
    if (Array.isArray(row.alternatives)) row.alternatives.forEach((value, index) => {
      const alternative = object(value, 'gathering alternative');
      const prefix = `alternatives[${index}].`;
      fields(alternative, ['requires', 'risk'], prefix);
      steps(alternative.steps, `${prefix}steps`);
    });
  }
  return lines;
}

/** Ignore metadata and prose-only edits; print stable, field-addressed requirement differences. */
export function diffReport(kind: DiffKind, before: string | undefined, after: string | undefined): string {
  const old = entries(before, kind);
  const working = entries(after, kind);
  const lines = [`${kind}.json:`];
  for (const id of [...new Set([...old.keys(), ...working.keys()])].sort()) {
    const previous = old.get(id);
    const next = working.get(id);
    if (!previous) lines.push(`  ADDED ${id} (${next!.name ?? next!.title ?? id})`);
    else if (!next) lines.push(`  REMOVED ${id} (${previous.name ?? previous.title ?? id})`);
    else {
      const beforeLines = requirementLines(previous, kind);
      const afterLines = requirementLines(next, kind);
      const changes: string[] = [];
      for (const path of [...new Set([...beforeLines.keys(), ...afterLines.keys()])].sort()) {
        if (beforeLines.get(path) === afterLines.get(path)) continue;
        if (beforeLines.has(path)) changes.push(`    - ${path}: ${beforeLines.get(path)}`);
        if (afterLines.has(path)) changes.push(`    + ${path}: ${afterLines.get(path)}`);
      }
      if (changes.length > 0) lines.push(`  CHANGED ${id}`, ...changes);
    }
  }
  if (lines.length === 1) lines.push('  No entry or requirement changes.');
  return lines.join('\n') + '\n';
}
