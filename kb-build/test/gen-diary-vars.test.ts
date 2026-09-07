import { describe, expect, it } from 'vitest';
import { parseTierJava } from '../scripts/gen-diary-vars.js';

describe('parseTierJava', () => {
  it('extracts varp/bit task vars in declaration order, not sorted by bit', () => {
    // Declaration order deliberately out of numeric order: this must be preserved
    // as-is. Real areas (Ardougne Elite, Kourend Medium) declare bits out of
    // numeric order but in wiki task order; a prior version of this script sorted
    // by (varp, bit), which was found to break both against the real wiki build.
    const java = `
      notB = new VarplayerRequirement(VarPlayerID.VARROCK_ACHIEVEMENT_DIARY, false, 3);
      notA = new VarplayerRequirement(VarPlayerID.VARROCK_ACHIEVEMENT_DIARY, false, 1);
      notC = new VarplayerRequirement(VarPlayerID.VARROCK_ACHIEVEMENT_DIARY, false, 2);
      unrelated = new VarplayerRequirement(VarPlayerID.NZONE_REWARDPOINTS, 800000, Operation.GREATER_EQUAL);
    `;
    expect(parseTierJava(java)).toEqual([
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
    expect(parseTierJava(java)).toEqual([
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
    expect(parseTierJava(java)).toEqual([
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
