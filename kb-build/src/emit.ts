import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { sourceManifest, type WikiSource } from './wiki.js';

/** RuneScape's font lacks typographic punctuation used in wiki display strings. */
export function asciiPunctuation(value: string): string {
  return value.replace(/[\u2010-\u2015\u2212]/g, '-')
    .replace(/[\u2018\u2019\u201a\u201b\u2032]/g, "'")
    .replace(/[\u201c\u201d\u201e\u201f\u2033]/g, '"');
}

/** Deep-clones `value` with object keys sorted recursively, so JSON.stringify output is stable regardless of insertion order. */
function sortKeys(value: unknown): unknown {
  if (typeof value === 'string') return asciiPunctuation(value);
  if (Array.isArray(value)) {
    return value.map(sortKeys);
  }
  if (value !== null && typeof value === 'object') {
    const sorted: Record<string, unknown> = {};
    for (const key of Object.keys(value as Record<string, unknown>).sort()) {
      sorted[key] = sortKeys((value as Record<string, unknown>)[key]);
    }
    return sorted;
  }
  return value;
}

/** Serializes `obj` to JSON with recursively sorted keys, 2-space indent, and a trailing newline. */
export function emitJson(obj: unknown): string {
  return JSON.stringify(sortKeys(obj), null, 2) + '\n';
}

export const KB_DIR = join(import.meta.dirname, '..', '..', 'src', 'main', 'resources', 'kb');

function entryKey(value: unknown): string | undefined {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) return undefined;
  if ('area' in value && 'tier' in value) return `diary:${value.area}:${value.tier}`;
  if ('id' in value && value.id !== null) return `id:${value.id}`;
  if ('ordinal' in value) return `ordinal:${value.ordinal}`;
  if ('name' in value) return `name:${'skill' in value ? value.skill : ''}:${value.name}`;
  if ('title' in value) return `title:${value.title}`;
  if ('skill' in value) return `skill:${value.skill}`;
  if ('text' in value) return `text:${value.text}`;
  return undefined;
}

/** Replace generated arrays (including removals), retaining extension fields on matching entries. */
export function preserveExtensions(previous: unknown, generated: unknown): unknown {
  if (Array.isArray(generated)) {
    const old = new Map<string, unknown>();
    for (const entry of Array.isArray(previous) ? previous : []) {
      const key = entryKey(entry);
      if (key !== undefined) old.set(key, entry);
    }
    return generated.map((entry) => {
      const key = entryKey(entry);
      return key === undefined ? entry : preserveExtensions(old.get(key), entry);
    });
  }
  if (generated === null || typeof generated !== 'object') return generated;
  const result: Record<string, unknown> = previous !== null && typeof previous === 'object' && !Array.isArray(previous)
    ? { ...previous } : {};
  // Optional fields owned by the method generator must disappear when no longer emitted.
  if ('xpPerAction' in generated) {
    for (const key of ['boostable', 'ticks', 'intermediate']) delete result[key];
  }
  for (const [key, value] of Object.entries(generated)) result[key] = preserveExtensions(result[key], value);
  return result;
}

/** Writes the generated payload without discarding unrelated top-level extension fields. */
export function writeKb(name: string, payload: unknown, generatedAt: string | undefined, sources: Iterable<WikiSource> = []): void {
  const outPath = join(KB_DIR, `${name}.json`);
  const previous = existsSync(outPath) ? JSON.parse(readFileSync(outPath, 'utf8')) : {};
  mkdirSync(dirname(outPath), { recursive: true });
  const merged = preserveExtensions(previous, payload);
  writeFileSync(outPath, emitJson({ ...(merged as Record<string, unknown>), version: 1, generatedAt, sources: sourceManifest(sources) }));
}
