import type { SkillReq } from './questreq.js';

export interface ItemReq {
  name: string;
  quantity: number;
}

export interface QuestPage {
  items: ItemReq[];
  questPoints: number | null;
  requirements: { skills: SkillReq[]; prereqs: string[] } | null;
}

const ITEM_LINE = /^\*+\[\[([^\]|]+)(?:\|[^\]]*)?\]\](?:\s*x\s*(\d+))?/;
const SKILL_REQ_LINE = /^\*(?!\*)\{\{SCP\|([^|}]+)\|([^|}]+)/;
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

  for (const line of inner.split('\n')) {
    const match = /^\|(\w+)\s*=(.*)$/.exec(line);
    if (match) {
      flush();
      currentName = match[1]!;
      currentLines = [match[2]!];
    } else if (currentName !== null) {
      currentLines.push(line);
    }
  }
  flush();

  return params;
}

function parseItems(value: string): ItemReq[] {
  const items: ItemReq[] = [];
  for (const line of value.split('\n')) {
    const match = ITEM_LINE.exec(line.trim());
    if (match) {
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
      if (!Number.isNaN(level)) {
        skills.push({
          skill: skillMatch[1]!.trim(),
          level,
          boostable: /\{\{Boostable\|yes\}\}/i.test(trimmed),
          ironmanOnly: false,
        });
      }
      continue;
    }

    const prereqMatch = DIRECT_PREREQ_LINE.exec(trimmed);
    if (prereqMatch) {
      prereqs.push(prereqMatch[1]!.trim());
    }
  }

  return { skills, prereqs };
}

/** Parses a quest's wikitext into its item/quest-point rewards and (fallback) skill/prerequisite requirements. */
export function parseQuestPage(wikitext: string): QuestPage {
  const details = templateParams(wikitext, 'Quest details');
  const rewards = templateParams(wikitext, 'Quest rewards');

  return {
    items: details.items ? parseItems(details.items) : [],
    questPoints: rewards.qp ? Number(rewards.qp) : null,
    requirements: details.requirements ? parseRequirements(details.requirements) : null,
  };
}
