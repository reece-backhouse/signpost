import { describe, expect, it } from 'vitest';
import { buildDiaries, parseDiaryPage, DIARY_PAGE_TITLES } from '../src/diaries.js';
import { loadFixture } from './fixtures.js';

describe('parseDiaryPage', () => {
  const tiers = parseDiaryPage(loadFixture('varrock.txt'), 'VARROCK');

  it('parses all 4 tiers', () => {
    expect(tiers.map((t) => t.tier)).toEqual(['EASY', 'MEDIUM', 'HARD', 'ELITE']);
  });

  it('tags every task with the given area', () => {
    expect(tiers.every((t) => t.area === 'VARROCK')).toBe(true);
  });

  it('Easy has 14 tasks, Medium 13, Hard 10, Elite 5', () => {
    expect(tiers.map((t) => t.tasks.length)).toEqual([14, 13, 10, 5]);
  });

  it('Easy task 3: text contains "Iron", skill Mining 15, item pickaxe', () => {
    const easy = tiers[0]!;
    const task3 = easy.tasks.find((t) => t.ordinal === 3)!;
    expect(task3.text).toContain('Iron');
    expect(task3.skills).toEqual([{ skill: 'Mining', level: 15, boostable: false, ironmanOnly: false }]);
    expect(task3.items).toEqual(['pickaxe']);
  });

  it('Easy task 2 has quest "Rune Mysteries" and a note from its italic continuation line', () => {
    const easy = tiers[0]!;
    const task2 = easy.tasks.find((t) => t.ordinal === 2)!;
    expect(task2.quests).toEqual(['Rune Mysteries']);
    expect(task2.notes).toContainEqual(expect.stringContaining('right-click him to teleport'));
  });

  it('Easy task 1 has {{NA|None}} requirements -> empty skills/quests/items/notes', () => {
    const easy = tiers[0]!;
    const task1 = easy.tasks.find((t) => t.ordinal === 1)!;
    expect(task1.skills).toEqual([]);
    expect(task1.quests).toEqual([]);
    expect(task1.items).toEqual([]);
    expect(task1.notes).toEqual([]);
  });

  it('Medium task 3 has two "completion of" quests from one bullet (full + partial)', () => {
    const medium = tiers[1]!;
    const task3 = medium.tasks.find((t) => t.ordinal === 3)!;
    expect(task3.quests).toEqual(["Gertrude's Cat", 'Garden of Tranquillity']);
  });

  it('Medium task 2 has a quest-points skill requirement ({{SCP|Quest|32}} -> skill "Quest")', () => {
    const medium = tiers[1]!;
    const task2 = medium.tasks.find((t) => t.ordinal === 2)!;
    expect(task2.skills).toEqual([{ skill: 'Quest', level: 32, boostable: false, ironmanOnly: false }]);
  });

  it('Hard task 6 has two skills from one bullet ("Woodcutting 60 and Firemaking 60")', () => {
    const hard = tiers[2]!;
    const task6 = hard.tasks.find((t) => t.ordinal === 6)!;
    expect(task6.skills).toEqual([
      { skill: 'Woodcutting', level: 60, boostable: false, ironmanOnly: false },
      { skill: 'Firemaking', level: 60, boostable: false, ironmanOnly: false },
    ]);
  });

  it('Hard task 1 has the fixture\'s one ironman-only case: a second Hunter requirement embedded in the item bullet ("2 dashing kebbit fur (requires {{SCP|Hunter|69}} ... Ironman)")', () => {
    // The requirement bullet scan is not anchored to the start of a bullet line,
    // so it also finds {{SCP|...}} tokens embedded mid-bullet (needed for lines
    // like "Woodcutting 60 and Firemaking 60" that carry two skills). That means
    // this fixture's one "ironman" bullet -- an item requirement with a
    // conditional embedded skill clause -- surfaces as a second Hunter skill req
    // rather than being classified as a plain note. The "2 dashing kebbit fur"
    // item text itself is not separately captured once the bullet is classified
    // as a skill requirement (a known, documented simplification).
    const hard = tiers[2]!;
    const task1 = hard.tasks.find((t) => t.ordinal === 1)!;
    expect(task1.skills).toEqual([
      { skill: 'Hunter', level: 66, boostable: false, ironmanOnly: false },
      { skill: 'Hunter', level: 69, boostable: true, ironmanOnly: true },
    ]);
  });

  it('marks a skill requirement ironmanOnly when its own bullet mentions "ironman" (synthetic minimal-page case)', () => {
    const emptyTier = (label: string) =>
      `{| class="wikitable lighttable qc-active diary-table" data-diary-tier="${label}"\n! Task\n! Requirements\n|-\n|1. Do a thing.\n|{{NA|None}}\n|}`;
    const wikitext = [
      '==Easy==',
      '{| class="wikitable lighttable qc-active diary-table" data-diary-tier="Easy"',
      '! Task',
      '! Requirements',
      '|-',
      '|1. Do a thing.',
      '|',
      '*{{SCP|Hunter|69|link=y}}, required for Ironmen',
      '|}',
      emptyTier('Medium'),
      emptyTier('Hard'),
      emptyTier('Elite'),
    ].join('\n');
    const [easy] = parseDiaryPage(wikitext, 'VARROCK');
    expect(easy!.tasks[0]!.skills).toEqual([{ skill: 'Hunter', level: 69, boostable: false, ironmanOnly: true }]);
  });

  it('throws when a tier table is missing', () => {
    expect(() => parseDiaryPage('no tables here', 'VARROCK')).toThrow(/Easy/);
  });
});

describe('DIARY_PAGE_TITLES', () => {
  it('has all 12 areas', () => {
    expect(Object.keys(DIARY_PAGE_TITLES).sort()).toEqual(
      [
        'ARDOUGNE',
        'DESERT',
        'FALADOR',
        'FREMENNIK',
        'KANDARIN',
        'KARAMJA',
        'KOUREND',
        'LUMBRIDGE',
        'MORYTANIA',
        'VARROCK',
        'WESTERN',
        'WILDERNESS',
      ].sort(),
    );
  });
});

describe('buildDiaries', () => {
  const varrockWikitext = loadFixture('varrock.txt');

  function minimalVars(overrides: Partial<Record<string, unknown>> = {}) {
    const base: Record<string, unknown> = {
      VARROCK: {
        tierVarbits: { EASY: 4479, MEDIUM: 4480, HARD: 4481, ELITE: 4482 },
        EASY: Array.from({ length: 14 }, (_, i) => ({ varp: 1176, bit: i + 1 })),
        MEDIUM: Array.from({ length: 13 }, (_, i) => ({ varp: 1176, bit: i + 1 })),
        HARD: Array.from({ length: 10 }, (_, i) => ({ varp: 1176, bit: i + 1 })),
        ELITE: Array.from({ length: 5 }, (_, i) => ({ varp: 1176, bit: i + 1 })),
      },
    };
    return { ...base, ...overrides };
  }

  it('joins ordinal N to vars[N-1] and attaches the tier-completion varbit', () => {
    const pages = new Map([['Varrock Diary', { content: varrockWikitext, timestamp: '2024-01-01T00:00:00Z' }]]);
    const entries = buildDiaries({ pages, vars: minimalVars() as never });
    const easy = entries.find((e) => e.area === 'VARROCK' && e.tier === 'EASY')!;
    expect(easy.tierVarbit).toBe(4479);
    const task3 = easy.tasks.find((t) => t.ordinal === 3)!;
    expect(task3.completion).toEqual({ varp: 1176, bit: 3 });
  });

  it('throws naming area/tier and both counts on a var-count mismatch', () => {
    const pages = new Map([['Varrock Diary', { content: varrockWikitext, timestamp: '2024-01-01T00:00:00Z' }]]);
    const vars = minimalVars({
      VARROCK: {
        tierVarbits: { EASY: 4479, MEDIUM: 4480, HARD: 4481, ELITE: 4482 },
        EASY: [{ varp: 1176, bit: 1 }], // wrong: wiki has 14 tasks
        MEDIUM: [],
        HARD: [],
        ELITE: [],
      },
    });
    let error: unknown;
    try {
      buildDiaries({ pages, vars: vars as never });
    } catch (e) {
      error = e;
    }
    const message = (error as Error)?.message ?? '';
    expect(message).toContain('VARROCK');
    expect(message).toContain('EASY');
    expect(message).toContain('14');
    expect(message).toContain('1');
  });
});
