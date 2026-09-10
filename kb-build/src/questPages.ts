import type { SkillReq } from './questreq.js';

export interface ItemReq {
  name: string;
  quantity: number;
}

/** One experience lamp (or tome use) awarded by a quest: `xp` in any one of `skills`, usable once that skill is at `minLevel` (0 = no floor). */
export interface QuestLamp {
  xp: number;
  skills: string[];
  minLevel: number;
}

/** Fixed skill xp (`xp[skill]`) and choice lamps, one entry per lamp, parsed conservatively from quest rewards. */
export interface QuestRewards {
  xp: Record<string, number>;
  lamps: QuestLamp[];
}

export interface QuestPage {
  items: ItemReq[];
  questPoints: number | null;
  requirements: { skills: SkillReq[]; prereqs: string[] } | null;
  rewards: QuestRewards;
}

/** The 23 skills the client knows, in wiki spelling; reward xp in any other skill (e.g. Sailing) is dropped. */
export const SKILLS = [
  'Attack', 'Strength', 'Defence', 'Ranged', 'Prayer', 'Magic', 'Runecraft', 'Construction', 'Hitpoints', 'Agility',
  'Herblore', 'Thieving', 'Crafting', 'Fletching', 'Slayer', 'Hunter', 'Mining', 'Smithing', 'Fishing', 'Cooking',
  'Firemaking', 'Woodcutting', 'Farming',
];
const COMBAT_SKILLS = ['Attack', 'Strength', 'Defence', 'Hitpoints', 'Ranged', 'Magic', 'Prayer'];

/** Matches a `[[target]]` or `[[target|display]]` wikilink; capture group 1 is the target page. */
export const WIKILINK = /\[\[([^\]|]+)(?:\|[^\]]*)?\]\]/;

/** Matches a `{{SCP|Skill|Level|...}}` token; capture groups are the skill name and level. */
export const SCP_SKILL_TOKEN = /\{\{SCP\|([^|}]+)\|([^|}]+?)(?:\|[^}]*)?\}\}/;

/** True when `text` contains an explicit `{{Boostable|yes}}` or abbreviated `{{Boostable|y}}` tag (the codebase's convention: skill requirements default to non-boostable otherwise). Diary pages use the abbreviated `y`/`n` form; quest pages spell out `yes`/`no`. */
export function boostableFromText(text: string): boolean {
  return /\{\{Boostable\|y(?:es)?\}\}/i.test(text);
}

const ITEM_LINE = new RegExp(`^\\*+${WIKILINK.source}(?:\\s*x\\s*(\\d+))?`);

/** True for a `[[File:...]]`/`[[Image:...]]` link target: an inline picture (e.g. the ironman chat badge), never an item. */
export function isImageLink(target: string): boolean {
  return /^(?:File|Image):/i.test(target.trim());
}
const SKILL_REQ_LINE = new RegExp(`^\\*(?!\\*)${SCP_SKILL_TOKEN.source}`);
const DIRECT_PREREQ_LINE = /^\*\*(?!\*)\[\[([^\]|]+)/;

/**
 * Extracts named parameters from the first `{{TemplateName ...}}` invocation in wikitext.
 * A parameter's value runs from its `|name = ` line until the next top-level `|name =` line
 * or the template's closing `}}`, so multi-line values (bullet lists, nested templates) survive intact.
 */
export function templateParams(wikitext: string, templateName: string): Record<string, string> {
  const startMarker = `{{${templateName}`;
  const start = wikitext.indexOf(startMarker);
  if (start === -1) return {};

  let depth = 1;
  let i = start + startMarker.length;
  const contentStart = i;
  while (i < wikitext.length && depth > 0) {
    if (wikitext.startsWith('{{', i)) {
      depth++;
      i += 2;
    } else if (wikitext.startsWith('}}', i)) {
      depth--;
      if (depth === 0) break;
      i += 2;
    } else {
      i++;
    }
  }
  const inner = wikitext.slice(contentStart, i);

  const params: Record<string, string> = {};
  let currentName: string | null = null;
  let currentLines: string[] = [];

  const flush = (): void => {
    if (currentName !== null) {
      params[currentName] = currentLines.join('\n').trim();
    }
  };

  // A `|name =` segment only starts a new outer parameter when it sits at the outer
  // template's own brace depth; a nested template's `|name =` line (depth > 0
  // at that point) is part of the current parameter's value instead. Several
  // params may share one line (`|name=X|qp=3|rewards=*...`), so a depth-0 line is
  // split at every `|key=` that is outside `{{...}}` and `[[...]]`.
  let nestedDepth = 0;
  for (const line of inner.split('\n')) {
    const segments = nestedDepth === 0 ? splitParams(line) : null;
    if (segments && segments.length > 0) {
      for (const [name, value] of segments) {
        flush();
        currentName = name;
        currentLines = [value];
      }
    } else if (currentName !== null) {
      currentLines.push(line);
    }
    nestedDepth += countOccurrences(line, '{{') - countOccurrences(line, '}}');
  }
  flush();

  return params;
}

/** `[name, value]` per `|name=value` segment of a line starting with `|`, cutting only at pipes outside links and templates; empty when the line isn't a param line. */
function splitParams(line: string): [string, string][] {
  if (!line.startsWith('|')) return [];
  const cuts: number[] = [];
  let depth = 0;
  for (let i = 0; i < line.length; i++) {
    if (line.startsWith('{{', i) || line.startsWith('[[', i)) {
      depth++;
      i++;
    } else if (line.startsWith('}}', i) || line.startsWith(']]', i)) {
      depth--;
      i++;
    } else if (depth === 0 && line[i] === '|' && /^\|\w+\s*=/.test(line.slice(i))) {
      cuts.push(i);
    }
  }
  const segments: [string, string][] = [];
  for (let c = 0; c < cuts.length; c++) {
    const piece = line.slice(cuts[c]!, cuts[c + 1] ?? line.length);
    const match = /^\|(\w+)\s*=(.*)$/s.exec(piece)!;
    segments.push([match[1]!, match[2]!]);
  }
  return segments;
}

function countOccurrences(text: string, needle: string): number {
  let count = 0;
  let index = text.indexOf(needle);
  while (index !== -1) {
    count++;
    index = text.indexOf(needle, index + needle.length);
  }
  return count;
}

function parseItems(value: string): ItemReq[] {
  const items: ItemReq[] = [];
  for (const line of value.split('\n')) {
    const match = ITEM_LINE.exec(line.trim());
    if (match && !isImageLink(match[1]!)) {
      items.push({ name: match[1]!.trim(), quantity: match[2] ? Number(match[2]) : 1 });
    }
  }
  return items;
}

function parseRequirements(value: string): { skills: SkillReq[]; prereqs: string[] } | null {
  if (!value.trim()) return null;

  const skills: SkillReq[] = [];
  const prereqs: string[] = [];

  for (const line of value.split('\n')) {
    const trimmed = line.trim();

    const skillMatch = SKILL_REQ_LINE.exec(trimmed);
    if (skillMatch) {
      const level = Number(skillMatch[2]);
      if (Number.isNaN(level)) {
        throw new Error(`Non-numeric skill level in requirements line: "${trimmed}"`);
      }
      skills.push({
        skill: skillMatch[1]!.trim(),
        level,
        boostable: boostableFromText(trimmed),
        ironmanOnly: false,
      });
      continue;
    }

    const prereqMatch = DIRECT_PREREQ_LINE.exec(trimmed);
    if (prereqMatch) {
      prereqs.push(prereqMatch[1]!.trim());
    }
  }

  return { skills, prereqs };
}

const SKILL_ALT = SKILLS.join('|');
const CANONICAL_SKILL = new Map(SKILLS.map((skill) => [skill.toLowerCase(), skill]));
/** `{{SCP|Skill|N}} [[Skill]] experience` (optional `|link=yes`, `[[experience]]`/`XP`, the skill word optional and unlinked), any number per line. */
const FIXED_XP_TOKEN = new RegExp(
  `\\{\\{SCP\\|(${SKILL_ALT})\\|([\\d,]+(?:\\.\\d+)?)(?:\\|link=yes)?\\}\\}\\s*(?:\\[\\[(?:${SKILL_ALT})\\]\\]|${SKILL_ALT})?\\s*(?:\\[\\[experience\\]\\]|experience|XP\\b)`,
  'gi',
);
/** A fixed-xp line must not hedge: no choice, no condition, no lamp (those go through the lamp rules or are omitted). */
const FIXED_EXCLUDE = /choice|choose|either|instead|if you|lamp|tome|depending|up to/i;
/** An xp amount: digits (commas) followed - possibly via `}} [[Skill]]` - by "experience"/"XP". */
const XP_AMOUNT = /(\d[\d,]*)(?:\|link=yes)?(?:\}\})?\s*(?:\[\[[A-Za-z]+\]\]\s*)?(?:\[\[)?(?:experience|XP)\b/gi;
const SKILL_TOKEN = new RegExp(`\\{\\{SCP\\|(${SKILL_ALT})(?:\\||\\}\\})|\\[\\[(${SKILL_ALT})(?:\\||\\]\\])`, 'gi');
const LAMP_TRIGGER =
  /lamp|tome of experience|\d+\s*[x\u00d7]\s*[\d,]+\s*\[?\[?experience|skills? of (?:your|the player's) (?:choice|choosing)|choice of (?:two|three|four|\d+) skills/i;
const LAMP_EXCLUDE = /either|apart from|except|excluding|other than|if players|if you|depending|up to/i;
const ANY_SKILL = /any skill|skills? of (?:the player's|your) (?:choosing|choice)|a chosen skill/i;
/** A named combat-skill exclusion at the end of a reward line; more complex exclusions remain unparsed. */
const COMBAT_EXCLUSION = new RegExp(`\\b(?:excluding|except|other than)\\s+(${SKILL_ALT})[.)]*\\s*$`, 'i');
const WORD_NUMBERS: Record<string, number> = { one: 1, two: 2, three: 3, four: 4, five: 5, six: 6 };

function amount(text: string): number {
  return Math.floor(Number(text.replace(/,/g, '')));
}

/** How many lamps a line awards: "4x 25,000", "Three ... lamps", "four skills of your choice", "utilised six times"; else 1. */
function lampCount(line: string): number {
  const times = /(\d+)\s*[x\u00d7]\s*[\d,]+\s*\[?\[?experience/i.exec(line);
  if (times) return Number(times[1]);
  const count = /\b(one|two|three|four|five|six|\d+)\s+(?:(?:ancient|antique|experience|magic)\s+)*(?:lamps?\b|skills?\b|times\b|lots?\b)/i.exec(line);
  if (count) return WORD_NUMBERS[count[1]!.toLowerCase()] ?? Number(count[1]);
  return 1;
}

function lampMinLevel(line: string): number | null {
  const m = /(?:above|over|at least)\s*(?:level\s*)?(\d+)/i.exec(line)
    ?? /level\s*(?:of\s*)?(\d+)/i.exec(line);
  if (m) return Number(m[1]);
  return /\blevel\b|\bminimum\b/i.test(line) ? null : 0;
}

function parseRewards(value: string): QuestRewards {
  const xp: Record<string, number> = {};
  const lamps: QuestLamp[] = [];
  const addXp = (skill: string, n: number): void => {
    xp[skill] = (xp[skill] ?? 0) + n;
  };

  for (const raw of value.split('\n')) {
    const line = raw.trim();
    if (/^\*(?!\*)/.test(line) && !FIXED_EXCLUDE.test(line)) {
      const fixed = [...line.matchAll(FIXED_XP_TOKEN)];
      for (const m of fixed) addXp(CANONICAL_SKILL.get(m[1]!.toLowerCase())!, amount(m[2]!));
      if (fixed.length > 0) continue;
    }
    // Lamp phrasing is matched on the display text ("antique lamps", "skills of your choice"),
    // skill names on the raw markup (`[[Magic]]`, `{{SCP|Magic}}`).
    const plain = line.replace(/\[\[(?:[^\]|]+\|)?([^\]]+)\]\]/g, '$1');
    const combat = /\bcombat skills?\b/i.test(plain);
    const exclusion = combat ? COMBAT_EXCLUSION.exec(plain) : null;
    const excludedSkill = exclusion ? CANONICAL_SKILL.get(exclusion[1]!.toLowerCase()) : undefined;
    if (!/^\*/.test(line) || !LAMP_TRIGGER.test(plain)
      || LAMP_EXCLUDE.test(exclusion ? plain.slice(0, exclusion.index) : plain)) continue;

    // "Four magic lamps offering a total of {{SCP|Slayer|20,000}} XP, ..." is fixed xp per skill.
    if (/total of/i.test(plain)) {
      if (!/choice|choose|chosen|choosing/i.test(plain)) {
        for (const m of line.matchAll(FIXED_XP_TOKEN)) addXp(CANONICAL_SKILL.get(m[1]!.toLowerCase())!, amount(m[2]!));
      }
      continue;
    }

    const amounts = [...new Set([...plain.matchAll(XP_AMOUNT)].map((m) => amount(m[1]!)))];
    if (amounts.length !== 1 || !Number.isSafeInteger(amounts[0]) || amounts[0]! <= 0) continue;
    const listed = [...new Set([...line.matchAll(SKILL_TOKEN)].map((m) => CANONICAL_SKILL.get((m[1] ?? m[2]!).toLowerCase())!))]
      .filter((skill) => skill !== excludedSkill);
    // Do not turn an unknown skill category into an unrestricted lamp.
    if (listed.length === 0 && /\b(?:gathering|artisan|support)\b/i.test(plain)) continue;
    const unknownSkill = [...line.matchAll(/\{\{SCP\|([^|}]+)/gi)]
      .some((m) => !CANONICAL_SKILL.has(m[1]!.toLowerCase()) && m[1]!.toLowerCase() !== 'combat');
    if (unknownSkill) continue;
    // An explicit list ("limited to Attack, Defence, ...") narrows a "skills of your choice" phrase.
    const skills = (listed.length > 0 ? listed : combat ? COMBAT_SKILLS : ANY_SKILL.test(plain) ? SKILLS : [])
      .filter((skill) => skill !== excludedSkill);
    if (skills.length === 0) continue;
    const minLevel = lampMinLevel(plain);
    if (minLevel === null) continue;
    const lamp: QuestLamp = { xp: amounts[0]!, skills, minLevel };
    const count = lampCount(plain);
    if (!Number.isSafeInteger(count) || count < 1) continue;
    for (let i = 0; i < count; i++) lamps.push({ ...lamp, skills: [...skills] });
  }
  return { xp, lamps };
}

/** Parses a quest's wikitext into its item/quest-point/xp rewards and (fallback) skill/prerequisite requirements. */
export function parseQuestPage(wikitext: string): QuestPage {
  const details = templateParams(wikitext, 'Quest details');
  const rewards = templateParams(wikitext, 'Quest rewards');
  // Miniquests such as Into the Tombs use a plain Rewards section rather than the template.
  const rewardText = rewards.rewards
    ?? /(?:^|\n)==\s*Rewards\s*==\s*\n([\s\S]*?)(?=\n==[^=]|$)/i.exec(wikitext)?.[1];

  return {
    items: details.items ? parseItems(details.items) : [],
    questPoints: rewards.qp ? Number(rewards.qp) : null,
    requirements: details.requirements ? parseRequirements(details.requirements) : null,
    rewards: rewardText ? parseRewards(rewardText) : { xp: {}, lamps: [] },
  };
}
