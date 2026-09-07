import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { writeKb } from './emit.js';
import { buildQuests, type QuestEntry } from './quests.js';
import { parseQuestreq } from './questreq.js';
import { fetchRevisions } from './wiki.js';

const dataDir = join(import.meta.dirname, '..', 'data');

async function buildQuestsCommand(): Promise<void> {
  const runeliteQuests: { id: number; name: string }[] = JSON.parse(
    readFileSync(join(dataDir, 'runelite-quests.json'), 'utf8'),
  );
  const aliases: Record<string, string> = JSON.parse(readFileSync(join(dataDir, 'aliases.json'), 'utf8'));

  const questreqRevisions = await fetchRevisions(['Module:Questreq/data']);
  const questreqPage = questreqRevisions.get('Module:Questreq/data');
  if (!questreqPage) {
    throw new Error('Module:Questreq/data missing from wiki response');
  }
  const questreq = parseQuestreq(questreqPage.content);

  const wikiTitles = [...new Set(runeliteQuests.map((q) => aliases[q.name] ?? q.name))];
  const pages = await fetchRevisions(wikiTitles);

  const quests = buildQuests({ runeliteQuests, aliases, questreq, pages });

  // ISO 8601 revision timestamps (fixed-width, UTC) sort lexicographically in chronological order.
  const timestamps = [questreqPage.timestamp, ...[...pages.values()].map((p) => p.timestamp)].sort();
  const generatedAt = timestamps.at(-1)!;

  writeKb('quests', { quests }, generatedAt);

  logSummary(quests);
}

function logSummary(quests: QuestEntry[]): void {
  const counts = new Map<string, number>();
  for (const quest of quests) {
    counts.set(quest.source, (counts.get(quest.source) ?? 0) + 1);
  }

  console.log(`Built ${quests.length} quests:`);
  for (const [source, count] of counts) {
    console.log(`  ${source}: ${count}`);
  }

  const none = quests.filter((quest) => quest.source === 'none');
  if (none.length > 0) {
    console.log(`Quests with source "none" (${none.length}): ${none.map((quest) => quest.name).join(', ')}`);
  }
}

const command = process.argv[2];
if (command === 'quests') {
  await buildQuestsCommand();
} else {
  console.error(`Unknown command: ${String(command)}. Usage: npm run build-kb -- quests`);
  process.exit(1);
}
