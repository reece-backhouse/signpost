import { parseQuestPage } from './questPages.js';
import type { QuestReq, SkillReq } from './questreq.js';
import type { ItemReq } from './questPages.js';

export interface QuestEntry {
  id: number;
  name: string;
  wikiTitle: string;
  skills: SkillReq[];
  prereqs: string[];
  items: ItemReq[];
  questPoints: number | null;
  source: 'questreq' | 'page' | 'none';
}

export interface BuildQuestsInput {
  runeliteQuests: { id: number; name: string }[];
  aliases: Record<string, string>;
  questreq: Map<string, QuestReq>;
  pages: Map<string, { content: string; timestamp: string }>;
}

/** Resolves each RuneLite quest name to its skill/item/prerequisite requirements, preferring Module:Questreq over the quest page's own requirements text. */
export function buildQuests(input: BuildQuestsInput): QuestEntry[] {
  const { runeliteQuests, aliases, questreq, pages } = input;

  // Wiki title -> RuneLite quest name, so page-parsed prereqs (wiki titles) can be
  // reported using the RuneLite name the alias exists for.
  const reverseAliases = new Map(Object.entries(aliases).map(([runeliteName, wikiTitle]) => [wikiTitle, runeliteName]));

  const entries = runeliteQuests.map(({ id, name }): QuestEntry => {
    const wikiTitle = aliases[name] ?? name;
    const page = pages.get(wikiTitle);
    const parsedPage = page ? parseQuestPage(page.content) : null;

    const req = questreq.get(wikiTitle);
    const source: QuestEntry['source'] = req ? 'questreq' : parsedPage?.requirements ? 'page' : 'none';
    const skills = req ? req.skills : (parsedPage?.requirements?.skills ?? []);
    const rawPrereqs = req ? req.prereqs : (parsedPage?.requirements?.prereqs ?? []);
    const prereqs = rawPrereqs.map((prereqTitle) => reverseAliases.get(prereqTitle) ?? prereqTitle);

    return {
      id,
      name,
      wikiTitle,
      skills,
      prereqs,
      items: parsedPage?.items ?? [],
      questPoints: parsedPage?.questPoints ?? null,
      source,
    };
  });

  return entries.sort((a, b) => a.id - b.id);
}
