import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

// Generates data/diary-vars.json by regex-scanning Quest Helper's achievement-diary
// helper sources (BSD-2-licensed, github.com/Zoinkwiz/quest-helper; reference only,
// never shipped) for each tier's task-completion vars.
//
// Run once: npx tsx scripts/gen-diary-vars.ts <path-to-questhelper-diaries-dir>
// (a directory containing one subdirectory per area, per AREA_DIRS below, each with
// <FilePrefix>Easy.java / Medium.java / Hard.java / Elite.java files)
//
// Declaration order of the `notXxx = new VarplayerRequirement(...)` assignments in
// these files is NOT reliably wiki task order: ArdougneElite.java declares its bits
// as 6,7,9,8,10,11,12,13 and KourendMedium.java declares 25,13,14,... — both
// scrambled relative to ascending bit order. Varrock (the one area with a wiki-text
// fixture to verify against) has bits in ascending declaration order, matching wiki
// order exactly. So rather than trust file order, this script sorts each tier's
// entries by their underlying numeric id -- (varp id, bit) ascending for varp-style
// tasks, varbit id ascending for varbit-style (Karamja) tasks -- which is consistent
// for both known cases and doesn't depend on a developer's variable-declaration habits.

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

// Achievement-diary tier-completion varbits, transcribed from RuneLite's
// runelite-api/src/main/java/net/runelite/api/gameval/VarbitID.java (master branch,
// fetched from raw.githubusercontent.com on 2026-09-07 -- no local fixture of this
// file exists in this repo). Karamja has no per-tier "<AREA>_DIARY_<TIER>_COMPLETE"
// varbit for its first three tiers the way every other area does (consistent with
// its Easy/Medium/Hard tasks themselves being varbit-, not varp-, based, unlike any
// other area); ATJUN_EASY_DONE/ATJUN_MED_DONE/ATJUN_HARD_DONE are the closest named
// analogues. Karamja Elite does have the standard-shaped KARAMJA_DIARY_ELITE_COMPLETE.
const TIER_VARBITS: Record<string, Record<string, number>> = {
  ARDOUGNE: { Easy: 4458, Medium: 4459, Hard: 4460, Elite: 4461 },
  FALADOR: { Easy: 4462, Medium: 4463, Hard: 4464, Elite: 4465 },
  WILDERNESS: { Easy: 4466, Medium: 4467, Hard: 4468, Elite: 4469 },
  WESTERN: { Easy: 4471, Medium: 4472, Hard: 4473, Elite: 4474 },
  KANDARIN: { Easy: 4475, Medium: 4476, Hard: 4477, Elite: 4478 },
  VARROCK: { Easy: 4479, Medium: 4480, Hard: 4481, Elite: 4482 },
  DESERT: { Easy: 4483, Medium: 4484, Hard: 4485, Elite: 4486 },
  MORYTANIA: { Easy: 4487, Medium: 4488, Hard: 4489, Elite: 4490 },
  FREMENNIK: { Easy: 4491, Medium: 4492, Hard: 4493, Elite: 4494 },
  LUMBRIDGE: { Easy: 4495, Medium: 4496, Hard: 4497, Elite: 4498 },
  KARAMJA: { Easy: 3578, Medium: 3599, Hard: 3611, Elite: 4566 },
  KOUREND: { Easy: 7925, Medium: 7926, Hard: 7927, Elite: 7928 },
};

// VarPlayerID.java constant -> numeric varp id (RuneLite master, fetched
// 2026-09-07); only the achievement-diary varps these Quest Helper sources use.
const VARP_IDS: Record<string, number> = {
  VARROCK_ACHIEVEMENT_DIARY: 1176,
  VARROCK_ACHIEVEMENT_DIARY2: 1177,
  KANDARIN_ACHIEVEMENT_DIARY: 1178,
  KANDARIN_ACHIEVEMENT_DIARY2: 1179,
  MORYTANIA_ACHIEVEMENT_DIARY: 1180,
  MORYTANIA_ACHIEVEMENT_DIARY2: 1181,
  WESTERN_ACHIEVEMENT_DIARY: 1182,
  WESTERN_ACHIEVEMENT_DIARY2: 1183,
  FREMENNIK_ACHIEVEMENT_DIARY: 1184,
  FREMENNIK_ACHIEVEMENT_DIARY2: 1185,
  FALADOR_ACHIEVEMENT_DIARY: 1186,
  FALADOR_ACHIEVEMENT_DIARY2: 1187,
  WILDERNESS_ACHIEVEMENT_DIARY: 1192,
  WILDERNESS_ACHIEVEMENT_DIARY2: 1193,
  LUMB_DRAY_ACHIEVEMENT_DIARY: 1194,
  LUMB_DRAY_ACHIEVEMENT_DIARY2: 1195,
  ARDOUNGE_ACHIEVEMENT_DIARY: 1196, // sic: RuneLite spells Ardougne "ARDOUNGE"
  ARDOUNGE_ACHIEVEMENT_DIARY2: 1197,
  DESERT_ACHIEVEMENT_DIARY: 1198,
  DESERT_ACHIEVEMENT_DIARY2: 1199,
  ATJUN_TASKS_4: 1200, // Karamja Elite (its only varp-style tier)
  KOUREND_ACHIEVEMENT_DIARY: 2085,
  KOUREND_ACHIEVEMENT_DIARY2: 2086,
};

// VarbitID.java constant -> numeric varbit id (RuneLite master, fetched 2026-09-07);
// Karamja Easy/Medium/Hard's per-task varbits (its only varbit-style tiers).
const VARBIT_IDS: Record<string, number> = {
  ATJUN_EASY_BANANA: 3566,
  ATJUN_EASY_SWING: 3567,
  ATJUN_EASY_GOLD: 3568,
  ATJUN_EASY_BOAT_SARIM: 3569,
  ATJUN_EASY_BOAT_ARDY: 3570,
  ATJUN_EASY_CAIRN: 3571,
  ATJUN_EASY_FISHING: 3572,
  ATJUN_EASY_SEAWEED: 3573,
  ATJUN_EASY_TZHAAR: 3574,
  ATJUN_EASY_JOGRE: 3575,
  ATJUN_MED_AGILITY: 3579,
  ATJUN_MED_VOLCANO: 3580,
  ATJUN_MED_CRANDOR: 3581,
  ATJUN_MED_CART: 3582,
  ATJUN_MED_CLEANUP: 3583,
  ATJUN_MED_SPIDER: 3584,
  ATJUN_MED_TOPAZ: 3585,
  ATJUN_MED_TEAK: 3586,
  ATJUN_MED_MAHOGANY: 3587,
  ATJUN_MED_KARAMBWAN: 3588,
  ATJUN_MED_MACHETTE: 3589,
  ATJUN_MED_GLIDER: 3590,
  ATJUN_MED_FARMING: 3591,
  ATJUN_MED_GRAAHK: 3592,
  ATJUN_MED_SHILO_VINES: 3593,
  ATJUN_MED_SHILO_LAVA: 3594,
  ATJUN_MED_SHILO_STAIRS: 3595,
  ATJUN_MED_KHAZARD: 3596,
  ATJUN_MED_CHARTER: 3597,
  ATJUN_HARD_FIGHTPITS: 3600,
  ATJUN_HARD_FIGHTCAVE: 3601,
  ATJUN_HARD_OOMLIE: 3602,
  ATJUN_HARD_NATURE: 3603,
  ATJUN_HARD_KARAMBWAN: 3604,
  ATJUN_HARD_DEATHWING: 3605,
  ATJUN_HARD_XBOW: 3606,
  ATJUN_HARD_PALM: 3607,
  ATJUN_HARD_DURADEL: 3608,
  ATJUN_HARD_DRAGON: 3609,
};

const VARP_TASK = /VarplayerRequirement\(VarPlayerID\.([A-Z0-9_]+),\s*false,\s*(\d+)\)/g;
const VARBIT_TASK = /VarbitRequirement\(VarbitID\.([A-Z0-9_]+),\s*(\d+)(?:,\s*Operation\.([A-Z_]+))?\)/g;

/**
 * Extracts one tier's task-completion vars from its Quest Helper java source.
 * Recognizes the achievement-diary varp form (`VarplayerRequirement(VarPlayerID.<X>_ACHIEVEMENT_DIARY[2], false, bit)`)
 * and Karamja's varbit doneMin form (`VarbitRequirement(VarbitID.ATJUN_..., v[, Operation.LESS_EQUAL])`),
 * filtered against the known-id tables above so unrelated same-shaped calls
 * (memoir/zone/reward checks) are ignored. Returns entries sorted by their
 * underlying numeric id, not source declaration order (see file header).
 */
export function parseTierJava(java: string): Entry[] {
  const varpEntries: VarpEntry[] = [...java.matchAll(VARP_TASK)]
    .filter((m) => m[1]! in VARP_IDS)
    .map((m) => ({ varp: VARP_IDS[m[1]!]!, bit: Number(m[2]) }));

  const varbitEntries: VarbitEntry[] = [...java.matchAll(VARBIT_TASK)]
    .filter((m) => m[1]! in VARBIT_IDS)
    .map((m) => {
      const threshold = Number(m[2]);
      const op = m[3];
      if (op !== undefined && op !== 'LESS_EQUAL') {
        throw new Error(`Unhandled VarbitRequirement Operation "${op}" for ${m[1]} (expected none or LESS_EQUAL)`);
      }
      return { varbit: VARBIT_IDS[m[1]!]!, doneMin: threshold + 1 };
    });

  if (varpEntries.length > 0 && varbitEntries.length > 0) {
    throw new Error('File matches both varp-style and varbit-style diary task patterns; expected exactly one style');
  }
  if (varpEntries.length === 0 && varbitEntries.length === 0) {
    throw new Error('Found no diary task vars in this file');
  }

  return varpEntries.length > 0
    ? varpEntries.sort((a, b) => a.varp - b.varp || a.bit - b.bit)
    : varbitEntries.sort((a, b) => a.varbit - b.varbit);
}

interface AreaVars {
  tierVarbits: Record<string, number>;
  [tier: string]: Entry[] | Record<string, number>;
}

function generate(questHelperDiariesDir: string): Record<string, AreaVars> {
  const result: Record<string, AreaVars> = {};

  for (const [area, { dir, filePrefix }] of Object.entries(AREA_DIRS)) {
    const tierVarbits: Record<string, number> = {};
    for (const [tier, varbit] of Object.entries(TIER_VARBITS[area]!)) {
      tierVarbits[tier.toUpperCase()] = varbit;
    }
    const areaVars: AreaVars = { tierVarbits };
    for (const tier of TIERS) {
      const path = join(questHelperDiariesDir, dir, `${filePrefix}${tier}.java`);
      const java = readFileSync(path, 'utf8');
      areaVars[tier.toUpperCase()] = parseTierJava(java);
    }
    result[area] = areaVars;
  }

  return result;
}

const isMain = process.argv[1] !== undefined && import.meta.url === pathToFileURL(process.argv[1]).href;
if (isMain) {
  const inputDir = process.argv[2];
  if (!inputDir) {
    throw new Error('Usage: gen-diary-vars.ts <path-to-questhelper-diaries-dir>');
  }
  const data = generate(inputDir);
  const outPath = join(import.meta.dirname, '..', 'data', 'diary-vars.json');
  writeFileSync(outPath, JSON.stringify(data, null, 2) + '\n');
  const tierCount = Object.keys(data).length * TIERS.length;
  console.log(`Wrote ${Object.keys(data).length} areas, ${tierCount} tiers to ${outPath}`);
}
