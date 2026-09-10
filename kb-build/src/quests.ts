import { parseQuestPage } from './questPages.js';
import type { QuestReq, SkillReq } from './questreq.js';
import type { ItemReq, QuestRewards } from './questPages.js';

export interface QuestEntry {
  id: number;
  name: string;
  wikiTitle: string;
  skills: SkillReq[];
  /** Prerequisite quest names that must be FINISHED; every name here resolves to a RuneLite quest. */
  prereqs: string[];
  /** Prerequisite quest names that only need to have been STARTED (wiki `Started:` prefix); every name here resolves to a RuneLite quest. */
  prereqsStarted: string[];
  /** Raw prerequisite text (e.g. "Started:X") that doesn't resolve to any RuneLite quest - kept for display, never evaluated. */
  prereqNotes: string[];
  items: ItemReq[];
  questPoints: number | null;
  source: 'questreq' | 'page' | 'none';
  /** Fixed skill xp and choice lamps from the wiki page's rewards; empty for an umbrella quest whose subquests carry them. */
  rewards: QuestRewards;
}

/** RuneLite quest names whose wiki page sums the rewards of subquests that are themselves quests here - their xp must not count twice. */
const UMBRELLA_QUESTS = new Set(['Recipe for Disaster']);
const NO_REWARDS: QuestRewards = { xp: {}, lamps: [] };

export interface BuildQuestsInput {
  runeliteQuests: { id: number; name: string }[];
  aliases: Record<string, string>;
  questreq: Map<string, QuestReq>;
  pages: Map<string, { content: string; timestamp: string }>;
}

const STARTED_PREFIX = 'Started:';

/**
 * Splits raw wiki prereq text into three buckets: finished-quest names, started-only quest names
 * (the wiki's `Started:` prefix), and anything that doesn't resolve to a RuneLite quest at all
 * (e.g. a Barbarian Training sub-activity) - the last kept verbatim as a note instead of dropped.
 */
function splitPrereqs(
  rawPrereqs: string[],
  reverseAliases: Map<string, string>,
  questNames: Set<string>,
): { prereqs: string[]; prereqsStarted: string[]; prereqNotes: string[] } {
  const prereqs: string[] = [];
  const prereqsStarted: string[] = [];
  const prereqNotes: string[] = [];

  for (const raw of rawPrereqs) {
    const started = raw.startsWith(STARTED_PREFIX);
    const title = started ? raw.slice(STARTED_PREFIX.length) : raw;
    const name = reverseAliases.get(title) ?? title;

    if (!questNames.has(name)) {
      prereqNotes.push(raw);
    } else if (started) {
      prereqsStarted.push(name);
    } else {
      prereqs.push(name);
    }
  }

  return { prereqs, prereqsStarted, prereqNotes };
}

/** Resolves each RuneLite quest name to its skill/item/prerequisite requirements, preferring Module:Questreq over the quest page's own requirements text. */
export function buildQuests(input: BuildQuestsInput): QuestEntry[] {
  const { runeliteQuests, aliases, questreq, pages } = input;

  // Wiki title -> RuneLite quest name, so page-parsed prereqs (wiki titles) can be
  // reported using the RuneLite name the alias exists for.
  const reverseAliases = new Map(Object.entries(aliases).map(([runeliteName, wikiTitle]) => [wikiTitle, runeliteName]));
  const questNames = new Set(runeliteQuests.map((q) => q.name));

  const entries = runeliteQuests.map(({ id, name }): QuestEntry => {
    const wikiTitle = aliases[name] ?? name;
    const page = pages.get(wikiTitle);
    const parsedPage = page ? parseQuestPage(page.content) : null;

    const req = questreq.get(wikiTitle);
    const source: QuestEntry['source'] = req ? 'questreq' : parsedPage?.requirements ? 'page' : 'none';
    const skills = req ? req.skills : (parsedPage?.requirements?.skills ?? []);
    const rawPrereqs = req ? req.prereqs : (parsedPage?.requirements?.prereqs ?? []);
    const { prereqs, prereqsStarted, prereqNotes } = splitPrereqs(rawPrereqs, reverseAliases, questNames);

    return {
      id,
      name,
      wikiTitle,
      skills,
      prereqs,
      prereqsStarted,
      prereqNotes,
      items: parsedPage?.items ?? [],
      questPoints: parsedPage?.questPoints ?? null,
      source,
      rewards: parsedPage && !UMBRELLA_QUESTS.has(name) ? parsedPage.rewards : NO_REWARDS,
    };
  });

  return entries.sort((a, b) => a.id - b.id);
}
