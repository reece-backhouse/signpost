import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

// Generates data/diary-vars.json by regex-scanning Quest Helper's achievement-diary
// helper sources (BSD-2-licensed, github.com/Zoinkwiz/quest-helper; reference only,
// never shipped) for each tier's task-completion vars, and looks up every numeric id
// it uses (varp/varbit ids, tier-completion varbit ids) in RuneLite's own constants
// sources -- data/runelite-src/{VarPlayerID,VarbitID,Varbits}.java (committed
// reference data, fetched from raw.githubusercontent.com/runelite/runelite/master at
// commit ac79ed8bd8926bec7bf172aa291574b4d944b0e7, 2026-09-07) -- rather than a
// hand-transcribed table, so every id is checked against source instead of trusted.
//
// Run once: npx tsx scripts/gen-diary-vars.ts <path-to-questhelper-diaries-dir> <VarPlayerID.java> <VarbitID.java> <Varbits.java>
//
// Entries are emitted in the SAME ORDER the `new VarplayerRequirement(...)` /
// `new VarbitRequirement(...)` calls appear in each tier's source file. An earlier
// version of this script sorted by (varp, bit) ascending, reasoning that
// declaration order looked scrambled for Ardougne Elite and Kourend Medium relative
// to bit order. That reasoning was wrong: checked against the real wiki (via the
// built diaries.json), declaration order matches wiki task order exactly for both,
// and the numeric sort actually swapped Ardougne Elite tasks 3/4 and misordered
// nearly all of Kourend Medium. Declaration order is trusted as-is.

export type VarpEntry = { varp: number; bit: number };
export type VarbitEntry = { varbit: number; doneMin: number };
export type Entry = VarpEntry | VarbitEntry;

const AREA_DIRS: Record<string, { dir: string; filePrefix: string }> = {
  ARDOUGNE: { dir: 'ardougne', filePrefix: 'Ardougne' },
  DESERT: { dir: 'desert', filePrefix: 'Desert' },
  FALADOR: { dir: 'falador', filePrefix: 'Falador' },
  FREMENNIK: { dir: 'fremennik', filePrefix: 'Fremennik' },
  KANDARIN: { dir: 'kandarin', filePrefix: 'Kandarin' },
  KARAMJA: { dir: 'karamja', filePrefix: 'Karamja' },
  KOUREND: { dir: 'kourend', filePrefix: 'Kourend' },
  LUMBRIDGE: { dir: 'lumbridgeanddraynor', filePrefix: 'Lumbridge' },
  MORYTANIA: { dir: 'morytania', filePrefix: 'Morytania' },
  VARROCK: { dir: 'varrock', filePrefix: 'Varrock' },
  WESTERN: { dir: 'westernprovinces', filePrefix: 'Western' },
  WILDERNESS: { dir: 'wilderness', filePrefix: 'Wilderness' },
};

const TIERS = ['Easy', 'Medium', 'Hard', 'Elite'] as const;

const CONST_DECL = /public static final int ([A-Za-z0-9_]+) = (\d+);/g;

/** Parses `public static final int NAME = N;` declarations from a RuneLite constants source file into a name -> id map. */
export function parseConstants(java: string): Record<string, number> {
  const ids: Record<string, number> = {};
  for (const match of java.matchAll(CONST_DECL)) {
    ids[match[1]!] = Number(match[2]);
  }
  return ids;
}

function lookup(ids: Record<string, number>, name: string, source: string): number {
  const id = ids[name];
  if (id === undefined) {
    throw new Error(`${name} not found in ${source}`);
  }
  return id;
}

const VARP_TASK = /VarplayerRequirement\(VarPlayerID\.([A-Z0-9_]+),\s*false,\s*(\d+)\)/g;
const VARBIT_TASK = /VarbitRequirement\(VarbitID\.([A-Z0-9_]+),\s*(\d+)(?:,\s*Operation\.([A-Z_]+))?\)/g;

/**
 * Extracts one tier's task-completion vars, in source declaration order (see file
 * header). Recognizes the achievement-diary varp form
 * (`VarplayerRequirement(VarPlayerID.<X>_ACHIEVEMENT_DIARY[2]|ATJUN_TASKS_4, false, bit)`)
 * and Karamja's varbit doneMin form (`VarbitRequirement(VarbitID.ATJUN_..., v[, Operation.LESS_EQUAL])`).
 * Unrelated same-shaped calls (memoir/zone/reward checks) are filtered out by name
 * shape; every remaining name is looked up in `varPlayerIds`/`varbitIds` (parsed from
 * RuneLite source via parseConstants) and THROWS if missing.
 */
export function parseTierJava(java: string, varPlayerIds: Record<string, number>, varbitIds: Record<string, number>): Entry[] {
  const varpEntries: VarpEntry[] = [...java.matchAll(VARP_TASK)]
    .filter((m) => m[1]!.endsWith('_ACHIEVEMENT_DIARY') || m[1]!.endsWith('_ACHIEVEMENT_DIARY2') || m[1] === 'ATJUN_TASKS_4')
    .map((m) => ({ varp: lookup(varPlayerIds, m[1]!, 'VarPlayerID.java'), bit: Number(m[2]) }));

  const varbitEntries: VarbitEntry[] = [...java.matchAll(VARBIT_TASK)]
    .filter((m) => m[1]!.startsWith('ATJUN_'))
    .map((m) => {
      const threshold = Number(m[2]);
      const op = m[3];
      if (op !== undefined && op !== 'LESS_EQUAL') {
        throw new Error(`Unhandled VarbitRequirement Operation "${op}" for ${m[1]} (expected none or LESS_EQUAL)`);
      }
      return { varbit: lookup(varbitIds, m[1]!, 'VarbitID.java'), doneMin: threshold + 1 };
    });

  if (varpEntries.length > 0 && varbitEntries.length > 0) {
    throw new Error('File matches both varp-style and varbit-style diary task patterns; expected exactly one style');
  }
  if (varpEntries.length === 0 && varbitEntries.length === 0) {
    throw new Error('Found no diary task vars in this file');
  }

  return varpEntries.length > 0 ? varpEntries : varbitEntries;
}

interface AreaVars {
  tierVarbits: Record<string, number>;
  [tier: string]: Entry[] | Record<string, number>;
}

function generate(
  questHelperDiariesDir: string,
  varPlayerIds: Record<string, number>,
  varbitIds: Record<string, number>,
  legacyVarbitIds: Record<string, number>,
): Record<string, AreaVars> {
  const result: Record<string, AreaVars> = {};

  for (const [area, { dir, filePrefix }] of Object.entries(AREA_DIRS)) {
    const tierVarbits: Record<string, number> = {};
    for (const tier of TIERS) {
      tierVarbits[tier.toUpperCase()] = lookup(legacyVarbitIds, `DIARY_${area}_${tier.toUpperCase()}`, 'Varbits.java');
    }
    const areaVars: AreaVars = { tierVarbits };
    for (const tier of TIERS) {
      const path = join(questHelperDiariesDir, dir, `${filePrefix}${tier}.java`);
      const java = readFileSync(path, 'utf8');
      areaVars[tier.toUpperCase()] = parseTierJava(java, varPlayerIds, varbitIds);
    }
    result[area] = areaVars;
  }

  return result;
}

const isMain = process.argv[1] !== undefined && import.meta.url === pathToFileURL(process.argv[1]).href;
if (isMain) {
  const [inputDir, varPlayerIdPath, varbitIdPath, varbitsPath] = process.argv.slice(2);
  if (!inputDir || !varPlayerIdPath || !varbitIdPath || !varbitsPath) {
    throw new Error(
      'Usage: gen-diary-vars.ts <path-to-questhelper-diaries-dir> <VarPlayerID.java> <VarbitID.java> <Varbits.java>',
    );
  }
  const varPlayerIds = parseConstants(readFileSync(varPlayerIdPath, 'utf8'));
  const varbitIds = parseConstants(readFileSync(varbitIdPath, 'utf8'));
  const legacyVarbitIds = parseConstants(readFileSync(varbitsPath, 'utf8'));

  const data = generate(inputDir, varPlayerIds, varbitIds, legacyVarbitIds);
  const outPath = join(import.meta.dirname, '..', 'data', 'diary-vars.json');
  writeFileSync(outPath, JSON.stringify(data, null, 2) + '\n');
  const tierCount = Object.keys(data).length * TIERS.length;
  console.log(`Wrote ${Object.keys(data).length} areas, ${tierCount} tiers to ${outPath}`);
}
