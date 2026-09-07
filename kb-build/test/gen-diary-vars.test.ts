import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { parseConstants, parseTierJava } from '../scripts/gen-diary-vars.js';

const VARP_IDS = {
  VARROCK_ACHIEVEMENT_DIARY: 1176,
  KOUREND_ACHIEVEMENT_DIARY: 2085,
  KOUREND_ACHIEVEMENT_DIARY2: 2086,
  NZONE_REWARDPOINTS: 9999, // present in source but not an achievement-diary varp
};
const VARBIT_IDS = {
  ATJUN_EASY_BANANA: 3566,
  ATJUN_EASY_SWING: 3567,
  ATJUN_EASY_GOLD: 3568,
  POH_HOUSE_LOCATION: 4321, // present in source but not an ATJUN_ task varbit
};

describe('parseConstants', () => {
  it('extracts `public static final int NAME = N;` declarations into a name -> id map', () => {
    const java = `
      package net.runelite.api.gameval;
      public final class VarPlayerID {
        public static final int VARROCK_ACHIEVEMENT_DIARY = 1176;
        public static final int VARROCK_ACHIEVEMENT_DIARY2 = 1177;
      }
    `;
    expect(parseConstants(java)).toEqual({ VARROCK_ACHIEVEMENT_DIARY: 1176, VARROCK_ACHIEVEMENT_DIARY2: 1177 });
  });
});

describe('parseTierJava', () => {
  it('extracts varp/bit task vars in declaration order, not sorted by bit', () => {
    // Declaration order deliberately out of numeric order: this must be preserved
    // as-is (a prior version of this script sorted by (varp, bit), which was found
    // to break real areas -- e.g. Ardougne Elite and Kourend Medium declare bits
    // out of numeric order but IN wiki task order; sorting scrambled them).
    const java = `
      notB = new VarplayerRequirement(VarPlayerID.VARROCK_ACHIEVEMENT_DIARY, false, 3);
      notA = new VarplayerRequirement(VarPlayerID.VARROCK_ACHIEVEMENT_DIARY, false, 1);
      notC = new VarplayerRequirement(VarPlayerID.VARROCK_ACHIEVEMENT_DIARY, false, 2);
      unrelated = new VarplayerRequirement(VarPlayerID.NZONE_REWARDPOINTS, 800000, Operation.GREATER_EQUAL);
    `;
    expect(parseTierJava(java, VARP_IDS, VARBIT_IDS)).toEqual([
      { varp: 1176, bit: 3 },
      { varp: 1176, bit: 1 },
      { varp: 1176, bit: 2 },
    ]);
  });

  it('preserves declaration order across two varps (DIARY then DIARY2), even out of numeric order', () => {
    const java = `
      notFirst = new VarplayerRequirement(VarPlayerID.KOUREND_ACHIEVEMENT_DIARY2, false, 0);
      notLast = new VarplayerRequirement(VarPlayerID.KOUREND_ACHIEVEMENT_DIARY, false, 31);
    `;
    expect(parseTierJava(java, VARP_IDS, VARBIT_IDS)).toEqual([
      { varp: 2086, bit: 0 },
      { varp: 2085, bit: 31 },
    ]);
  });

  it('extracts Karamja-style varbit doneMin vars in declaration order: bare form is doneMin 1, LESS_EQUAL form is doneMin v+1', () => {
    const java = `
      notSwungOnRope = new VarbitRequirement(VarbitID.ATJUN_EASY_SWING, 0);
      notPickedBananas = new VarbitRequirement(VarbitID.ATJUN_EASY_BANANA, 4, Operation.LESS_EQUAL);
      notMinedGold = new VarbitRequirement(VarbitID.ATJUN_EASY_GOLD, 0);
      houseInKourend = new VarbitRequirement(VarbitID.POH_HOUSE_LOCATION, 8);
    `;
    expect(parseTierJava(java, VARP_IDS, VARBIT_IDS)).toEqual([
      { varbit: 3567, doneMin: 1 },
      { varbit: 3566, doneMin: 5 },
      { varbit: 3568, doneMin: 1 },
    ]);
  });

  it('ignores unrelated VarbitRequirement/VarplayerRequirement calls (different constant names)', () => {
    const java = `
      memoirArc = new VarbitRequirement(VarbitID.KOUREND_DIARY_ARC_TELEPORT, Operation.EQUAL, 0, "");
      notMineIron = new VarplayerRequirement(VarPlayerID.KOUREND_ACHIEVEMENT_DIARY, false, 1);
    `;
    expect(parseTierJava(java, VARP_IDS, VARBIT_IDS)).toEqual([{ varp: 2085, bit: 1 }]);
  });

  it('throws when a referenced achievement-diary varp name is missing from the parsed source', () => {
    const java = `notX = new VarplayerRequirement(VarPlayerID.FALADOR_ACHIEVEMENT_DIARY, false, 1);`;
    expect(() => parseTierJava(java, VARP_IDS, VARBIT_IDS)).toThrow(/FALADOR_ACHIEVEMENT_DIARY/);
  });

  it('throws when a referenced ATJUN_ varbit name is missing from the parsed source', () => {
    const java = `notX = new VarbitRequirement(VarbitID.ATJUN_HARD_DRAGON, 0);`;
    expect(() => parseTierJava(java, VARP_IDS, VARBIT_IDS)).toThrow(/ATJUN_HARD_DRAGON/);
  });

  it('throws on an unhandled Operation for a Karamja-style varbit', () => {
    const java = `notX = new VarbitRequirement(VarbitID.ATJUN_EASY_SWING, 0, Operation.GREATER_EQUAL);`;
    expect(() => parseTierJava(java, VARP_IDS, VARBIT_IDS)).toThrow(/GREATER_EQUAL/);
  });

  it('throws when a file matches neither varp nor varbit task patterns', () => {
    expect(() => parseTierJava('nothing relevant here', VARP_IDS, VARBIT_IDS)).toThrow(/no diary task vars/i);
  });
});

describe('data/diary-vars.json sanity (parsed against committed RuneLite sources, not magic numbers)', () => {
  const dataDir = join(import.meta.dirname, '..', 'data');
  const srcDir = join(dataDir, 'runelite-src');
  const diaryVars: Record<
    string,
    { tierVarbits: Record<string, number> } & Record<string, Array<{ varp?: number; bit?: number; varbit?: number; doneMin?: number }>>
  > = JSON.parse(readFileSync(join(dataDir, 'diary-vars.json'), 'utf8'));
  const varPlayerIds = parseConstants(readFileSync(join(srcDir, 'VarPlayerID.java'), 'utf8'));
  const varbitIds = parseConstants(readFileSync(join(srcDir, 'VarbitID.java'), 'utf8'));
  const legacyVarbitIds = parseConstants(readFileSync(join(srcDir, 'Varbits.java'), 'utf8'));

  function namesForId(ids: Record<string, number>, id: number): string[] {
    return Object.entries(ids)
      .filter(([, value]) => value === id)
      .map(([name]) => name);
  }

  it('every varp used is a name ending in _ACHIEVEMENT_DIARY(2) or ATJUN_TASKS_4 in VarPlayerID.java', () => {
    for (const [area, entry] of Object.entries(diaryVars)) {
      for (const tier of ['EASY', 'MEDIUM', 'HARD', 'ELITE']) {
        for (const task of entry[tier] ?? []) {
          if (task.varp === undefined) continue;
          const names = namesForId(varPlayerIds, task.varp);
          expect(names.length, `${area} ${tier} varp ${task.varp} not found in VarPlayerID.java`).toBeGreaterThan(0);
          expect(
            names.some((n) => n.endsWith('_ACHIEVEMENT_DIARY') || n.endsWith('_ACHIEVEMENT_DIARY2') || n === 'ATJUN_TASKS_4'),
            `${area} ${tier} varp ${task.varp} (${names.join(',')}) is not an achievement-diary varp`,
          ).toBe(true);
        }
      }
    }
  });

  it('every Karamja task varbit is an ATJUN_* id in VarbitID.java', () => {
    for (const tier of ['EASY', 'MEDIUM', 'HARD']) {
      for (const task of diaryVars.KARAMJA![tier] ?? []) {
        if (task.varbit === undefined) continue;
        const names = namesForId(varbitIds, task.varbit);
        expect(names.length, `KARAMJA ${tier} varbit ${task.varbit} not found in VarbitID.java`).toBeGreaterThan(0);
        expect(names.every((n) => n.startsWith('ATJUN_'))).toBe(true);
      }
    }
  });

  it('every tierVarbit is a DIARY_* id in (legacy) Varbits.java', () => {
    for (const [area, entry] of Object.entries(diaryVars)) {
      for (const [tier, varbit] of Object.entries(entry.tierVarbits)) {
        const names = namesForId(legacyVarbitIds, varbit);
        expect(names.length, `${area} ${tier} tierVarbit ${varbit} not found in Varbits.java`).toBeGreaterThan(0);
        expect(names.every((n) => n.startsWith('DIARY_'))).toBe(true);
      }
    }
  });
});
