import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

// Generates data/runelite-quests.json from RuneLite's net.runelite.api.Quest enum source.
// Run once: npx tsx scripts/gen-runelite-quests.ts <path-to-Quest.java>
const javaPath = process.argv[2];
if (!javaPath) {
  throw new Error('Usage: gen-runelite-quests.ts <path-to-Quest.java>');
}

const ENUM_CONSTANT = /^\s*[A-Z][A-Z0-9_]*\((\d+),\s*"([^"]*)"\)/;

const source = readFileSync(javaPath, 'utf8');
const quests = source
  .split('\n')
  .map((line) => ENUM_CONSTANT.exec(line))
  .filter((match) => match !== null)
  .map((match) => ({ id: Number(match[1]), name: match[2] }));

if (quests.length === 0) {
  throw new Error(`No Quest enum constants found in ${javaPath}`);
}

const outPath = join(import.meta.dirname, '..', 'data', 'runelite-quests.json');
writeFileSync(outPath, JSON.stringify(quests, null, 2) + '\n');
console.log(`Wrote ${quests.length} quests to ${outPath}`);
