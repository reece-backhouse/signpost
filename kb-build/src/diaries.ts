import { SCP_SKILL_TOKEN, WIKILINK, boostableFromText } from './questPages.js';
import type { SkillReq } from './questreq.js';

export type DiaryArea =
  | 'ARDOUGNE'
  | 'DESERT'
  | 'FALADOR'
  | 'FREMENNIK'
  | 'KANDARIN'
  | 'KARAMJA'
  | 'KOUREND'
  | 'LUMBRIDGE'
  | 'MORYTANIA'
  | 'VARROCK'
  | 'WESTERN'
  | 'WILDERNESS';

export type DiaryTier = 'EASY' | 'MEDIUM' | 'HARD' | 'ELITE';

export interface DiaryTask {
  ordinal: number;
  text: string;
  skills: SkillReq[];
  quests: string[];
  items: string[];
  notes: string[];
}

export interface ParsedTier {
  area: DiaryArea;
  tier: DiaryTier;
  tasks: DiaryTask[];
}

/** Wiki page title for each diary area, in the exact form the wiki API expects. */
export const DIARY_PAGE_TITLES: Record<DiaryArea, string> = {
  ARDOUGNE: 'Ardougne Diary',
  DESERT: 'Desert Diary',
  FALADOR: 'Falador Diary',
  FREMENNIK: 'Fremennik Diary',
  KANDARIN: 'Kandarin Diary',
  KARAMJA: 'Karamja Diary',
  KOUREND: 'Kourend & Kebos Diary',
  LUMBRIDGE: 'Lumbridge & Draynor Diary',
  MORYTANIA: 'Morytania Diary',
  VARROCK: 'Varrock Diary',
  WESTERN: 'Western Provinces Diary',
  WILDERNESS: 'Wilderness Diary',
};

const TIER_LABELS: Record<DiaryTier, string> = { EASY: 'Easy', MEDIUM: 'Medium', HARD: 'Hard', ELITE: 'Elite' };

const NA_REQUIREMENT = /^\{\{NA\|None\}\}$/i;
const QUEST_COMPLETION_BULLET = /^\{\{SCP\|Quest\}\}/i;
// Wording varies ("Completion of [[X]]", "partial completion of [[X]]", "Started
// [[X]]"), so every quest link on the bullet is taken as a quest requirement
// rather than matching one specific phrase.
const QUEST_LINK = new RegExp(WIKILINK.source, 'g');
const SKILL_TOKEN_GLOBAL = new RegExp(SCP_SKILL_TOKEN.source, 'g');
const DIARY_ITEM_LINE = new RegExp(`^(?:Any\\s+)?${WIKILINK.source}\\s*$`, 'i');
const TASK_ORDINAL = /^(\d+)\.\s*(.*)$/s;

/** Strips wikitext markup down to plain text: resolves `[[a|b]]`/`[[a]]` links, drops `{{templates}}` and `''` italics. */
function stripWikitext(text: string): string {
  return text
    .replace(/\[\[[^\]|]*\|([^\]]+)\]\]/g, '$1')
    .replace(/\[\[([^\]]+)\]\]/g, '$1')
    .replace(/\{\{[^{}]*\}\}/g, '')
    .replace(/''+/g, '')
    .replace(/\s+/g, ' ')
    .trim();
}

/** Finds the full `{| ... |}` wikitable block for a given tier, by balanced-brace scanning from its `data-diary-tier="Tier"` marker. */
function extractTierBlock(wikitext: string, tierLabel: string): string {
  const marker = `data-diary-tier="${tierLabel}"`;
  const markerIdx = wikitext.indexOf(marker);
  if (markerIdx === -1) {
    throw new Error(`Diary tier table not found (no ${marker} in page)`);
  }
  const tableStart = wikitext.lastIndexOf('{|', markerIdx);
  if (tableStart === -1) {
    throw new Error(`Diary tier table not found: no {| before ${marker}`);
  }

  let depth = 1;
  let i = tableStart + 2;
  while (i < wikitext.length && depth > 0) {
    if (wikitext.startsWith('{|', i)) {
      depth++;
      i += 2;
    } else if (wikitext.startsWith('|}', i)) {
      depth--;
      i += 2;
    } else {
      i++;
    }
  }
  return wikitext.slice(tableStart, i);
}

/** Splits a tier's table block into raw per-row text, dropping the opening `{|...` line, `!` header lines, and the closing `|}`. */
function splitTableRows(tableBlock: string): string[] {
  const lines = tableBlock.split('\n').slice(1, -1).filter((line) => !line.startsWith('!'));

  const rows: string[] = [];
  let current: string[] = [];
  for (const line of lines) {
    if (line.trim() === '|-') {
      if (current.length > 0) rows.push(current.join('\n'));
      current = [];
    } else {
      current.push(line);
    }
  }
  if (current.length > 0) rows.push(current.join('\n'));
  return rows.filter((row) => row.trim().length > 0);
}

/** Splits one row's raw text into wikitable cells: a line starting with `|` opens a new cell, other lines continue the current cell. */
function splitCells(rowText: string): string[] {
  const cells: string[] = [];
  let current: string[] | null = null;
  for (const line of rowText.split('\n')) {
    if (line.startsWith('|')) {
      if (current !== null) cells.push(current.join('\n'));
      current = [line.slice(1)];
    } else if (current !== null) {
      current.push(line);
    }
  }
  if (current !== null) cells.push(current.join('\n'));
  return cells;
}

function parseTaskCell(cell: string): { ordinal: number; text: string; extraNotes: string[] } {
  const lines = cell.split('\n');
  const first = lines[0]!.trim();
  const match = TASK_ORDINAL.exec(first);
  if (!match) {
    throw new Error(`Diary task cell does not start with an ordinal: "${first}"`);
  }
  const extraNotes = lines
    .slice(1)
    .map((line) => stripWikitext(line))
    .filter((line) => line.length > 0);
  return { ordinal: Number(match[1]), text: stripWikitext(match[2]!), extraNotes };
}

function parseRequirementCell(cell: string): Omit<DiaryTask, 'ordinal' | 'text'> {
  const skills: SkillReq[] = [];
  const quests: string[] = [];
  const items: string[] = [];
  const notes: string[] = [];

  if (NA_REQUIREMENT.test(cell.trim())) {
    return { skills, quests, items, notes };
  }

  for (const rawLine of cell.split('\n')) {
    const line = rawLine.trim().replace(/^\*+\s*/, '');
    if (!line) continue;

    if (QUEST_COMPLETION_BULLET.test(line)) {
      const matches = [...line.matchAll(QUEST_LINK)];
      if (matches.length === 0) {
        // e.g. "{{SCP|Quest}} Completion of all quests" (Kandarin Elite): a real
        // gate with no single quest to link, so it becomes a note instead.
        notes.push(stripWikitext(line));
        continue;
      }
      for (const match of matches) quests.push(match[1]!.trim());
      continue;
    }

    // Some bullets use {{SCP|Skill|link=yes}} with no level at all, for prose like
    // "130 combined levels in {{SCP|Attack|link=yes}} and {{SCP|Strength|link=yes}}"
    // where the number lives in the surrounding sentence, not the template. Only
    // treat a token as a level requirement when its second field looks like an
    // attempted level (starts with a digit); otherwise it falls through to notes.
    const skillMatches = [...line.matchAll(SKILL_TOKEN_GLOBAL)].filter((m) => /^\d/.test(m[2]!));
    if (skillMatches.length > 0) {
      for (let i = 0; i < skillMatches.length; i++) {
        const skillMatch = skillMatches[i]!;
        const segEnd = i + 1 < skillMatches.length ? skillMatches[i + 1]!.index! : line.length;
        const segment = line.slice(skillMatch.index!, segEnd);
        // A level field can be a range ("29-42", varies by choice) or a "70+"
        // form; the filter above already guarantees a leading digit, so take that
        // leading number (a range's lower bound is the level the task first
        // becomes achievable at).
        const level = Number(/^\d+/.exec(skillMatch[2]!)![0]);
        skills.push({
          skill: skillMatch[1]!.trim(),
          level,
          boostable: boostableFromText(segment),
          ironmanOnly: /ironm[ae]n/i.test(segment),
        });
      }
      continue;
    }

    const itemMatch = DIARY_ITEM_LINE.exec(line);
    if (itemMatch) {
      items.push(itemMatch[1]!.trim());
      continue;
    }

    notes.push(stripWikitext(line));
  }

  return { skills, quests, items, notes };
}

function parseTier(wikitext: string, area: DiaryArea, tier: DiaryTier): ParsedTier {
  const block = extractTierBlock(wikitext, TIER_LABELS[tier]);
  const rows = splitTableRows(block);

  const tasks: DiaryTask[] = rows.map((rowText) => {
    const cells = splitCells(rowText);
    if (cells.length < 2) {
      throw new Error(`Diary row for ${area} ${tier} has ${cells.length} cell(s), expected 2: "${rowText.slice(0, 80)}"`);
    }
    const { ordinal, text, extraNotes } = parseTaskCell(cells[0]!);
    const reqs = parseRequirementCell(cells[1]!);
    return { ordinal, text, skills: reqs.skills, quests: reqs.quests, items: reqs.items, notes: [...extraNotes, ...reqs.notes] };
  });

  return { area, tier, tasks };
}

/** Parses an achievement-diary wiki page into its four tiers' tasks. */
export function parseDiaryPage(wikitext: string, area: DiaryArea): ParsedTier[] {
  return (['EASY', 'MEDIUM', 'HARD', 'ELITE'] as const).map((tier) => parseTier(wikitext, area, tier));
}

export interface DiaryVarpEntry {
  varp: number;
  bit: number;
}

export interface DiaryVarbitEntry {
  varbit: number;
  doneMin: number;
}

export type DiaryVarEntry = DiaryVarpEntry | DiaryVarbitEntry;

export interface DiaryAreaVars {
  tierVarbits: Record<DiaryTier, number>;
  EASY?: DiaryVarEntry[];
  MEDIUM?: DiaryVarEntry[];
  HARD?: DiaryVarEntry[];
  ELITE?: DiaryVarEntry[];
}

export type DiaryVarsFile = Partial<Record<DiaryArea, DiaryAreaVars>>;

export interface DiaryEntry {
  area: DiaryArea;
  tier: DiaryTier;
  tierVarbit: number;
  tasks: (DiaryTask & { completion: DiaryVarEntry })[];
}

export interface BuildDiariesInput {
  pages: Map<string, { content: string; timestamp: string }>;
  vars: DiaryVarsFile;
}

/** Fetches each area's diary page from `pages`, parses its tiers, and joins each task (by ordinal) to its completion var and tier-completion varbit from `vars`. */
export function buildDiaries(input: BuildDiariesInput): DiaryEntry[] {
  const { pages, vars } = input;
  const entries: DiaryEntry[] = [];

  // Driven by `vars`' own keys (like buildQuests is driven by its runeliteQuests
  // input) rather than a hardcoded area list, so callers control exactly which
  // areas get built.
  for (const area of Object.keys(vars) as DiaryArea[]) {
    const areaVars = vars[area]!;
    const title = DIARY_PAGE_TITLES[area];
    const page = pages.get(title);
    if (!page) {
      throw new Error(`Diary page missing from wiki response: ${title}`);
    }

    for (const parsed of parseDiaryPage(page.content, area)) {
      const varEntries = areaVars[parsed.tier];
      if (!varEntries) {
        throw new Error(`No diary-vars tasks for ${area} ${parsed.tier}`);
      }
      if (varEntries.length !== parsed.tasks.length) {
        throw new Error(
          `Diary task count mismatch for ${area} ${parsed.tier}: wiki has ${parsed.tasks.length} tasks, diary-vars has ${varEntries.length}`,
        );
      }
      const tierVarbit = areaVars.tierVarbits[parsed.tier];
      if (tierVarbit === undefined) {
        throw new Error(`No tier-completion varbit for ${area} ${parsed.tier}`);
      }

      const sortedTasks = [...parsed.tasks].sort((a, b) => a.ordinal - b.ordinal);
      const tasks = sortedTasks.map((task, index) => ({ ...task, completion: varEntries[index]! }));

      entries.push({ area, tier: parsed.tier, tierVarbit, tasks });
    }
  }

  return entries;
}
