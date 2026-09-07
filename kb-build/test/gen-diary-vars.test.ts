import { describe, expect, it } from 'vitest';
import { parseTierJava } from '../scripts/gen-diary-vars.js';

describe('parseTierJava', () => {
  it('extracts varp/bit task vars, sorted by (varp, bit) ascending rather than declaration order', () => {
    // Declaration order deliberately scrambled, mirroring what ArdougneElite.java
    // and KourendMedium.java actually do (bits are not always declared ascending).
    const java = `
      notB = new VarplayerRequirement(VarPlayerID.VARROCK_ACHIEVEMENT_DIARY, false, 3);
      notA = new VarplayerRequirement(VarPlayerID.VARROCK_ACHIEVEMENT_DIARY, false, 1);
      notC = new VarplayerRequirement(VarPlayerID.VARROCK_ACHIEVEMENT_DIARY, false, 2);
      unrelated = new VarplayerRequirement(VarPlayerID.NZONE_REWARDPOINTS, 800000, Operation.GREATER_EQUAL);
    `;
    expect(parseTierJava(java)).toEqual([
      { varp: 1176, bit: 1 },
      { varp: 1176, bit: 2 },
      { varp: 1176, bit: 3 },
    ]);
  });

  it('sorts entries spanning two varps (DIARY then DIARY2) as one continuous ordinal sequence', () => {
    const java = `
      notLast = new VarplayerRequirement(VarPlayerID.KOUREND_ACHIEVEMENT_DIARY, false, 31);
      notFirst = new VarplayerRequirement(VarPlayerID.KOUREND_ACHIEVEMENT_DIARY2, false, 0);
      notMid = new VarplayerRequirement(VarPlayerID.KOUREND_ACHIEVEMENT_DIARY, false, 30);
    `;
    expect(parseTierJava(java)).toEqual([
      { varp: 2085, bit: 30 },
      { varp: 2085, bit: 31 },
      { varp: 2086, bit: 0 },
    ]);
  });

  it('extracts Karamja-style varbit doneMin vars: bare form is doneMin 1, LESS_EQUAL form is doneMin v+1', () => {
    const java = `
      notSwungOnRope = new VarbitRequirement(VarbitID.ATJUN_EASY_SWING, 0);
      notPickedBananas = new VarbitRequirement(VarbitID.ATJUN_EASY_BANANA, 4, Operation.LESS_EQUAL);
      notMinedGold = new VarbitRequirement(VarbitID.ATJUN_EASY_GOLD, 0);
      houseInKourend = new VarbitRequirement(VarbitID.POH_HOUSE_LOCATION, 8);
    `;
    expect(parseTierJava(java)).toEqual([
      { varbit: 3566, doneMin: 5 },
      { varbit: 3567, doneMin: 1 },
      { varbit: 3568, doneMin: 1 },
    ]);
  });

  it('ignores unrelated VarbitRequirement/VarplayerRequirement calls (different constant names)', () => {
    const java = `
      memoirArc = new VarbitRequirement(VarbitID.KOUREND_DIARY_ARC_TELEPORT, Operation.EQUAL, 0, "");
      notMineIron = new VarplayerRequirement(VarPlayerID.KOUREND_ACHIEVEMENT_DIARY, false, 1);
    `;
    expect(parseTierJava(java)).toEqual([{ varp: 2085, bit: 1 }]);
  });

  it('throws on an unhandled Operation for a Karamja-style varbit', () => {
    const java = `notX = new VarbitRequirement(VarbitID.ATJUN_EASY_SWING, 0, Operation.GREATER_EQUAL);`;
    expect(() => parseTierJava(java)).toThrow(/GREATER_EQUAL/);
  });

  it('throws when a file matches neither varp nor varbit task patterns', () => {
    expect(() => parseTierJava('nothing relevant here')).toThrow(/no diary task vars/i);
  });
});
