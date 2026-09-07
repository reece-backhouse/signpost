import { describe, expect, it } from 'vitest';
import { mergeRecipes, parseSkillCalc, type Method, type RecipeRow } from '../src/methods.js';
import { loadFixture } from './fixtures.js';

describe('parseSkillCalc', () => {
  it('parses Prayer potion(3) from the real Herblore calc module: level, xp, materials, comma-split type', () => {
    const methods = parseSkillCalc(loadFixture('calc_herb.lua'), 'Herblore');
    const ppot = methods.find((m) => m.name === 'Prayer potion(3)');

    expect(ppot).toBeDefined();
    expect(ppot?.skill).toBe('Herblore');
    expect(ppot?.title).toBe('Prayer potion');
    expect(ppot?.levelReq).toBe(38);
    expect(ppot?.xpPerAction).toBe(87.5);
    expect(ppot?.materials).toEqual([
      { name: 'Ranarr potion (unf)', quantity: 1 },
      { name: 'Snape grass', quantity: 1 },
    ]);
    expect(ppot?.outputs).toEqual([{ name: 'Prayer potion(3)', quantity: 1 }]);
    expect(ppot?.members).toBe(true);
    expect(ppot?.types).toEqual(['Regular potions', 'Popular']);
  });

  it('excludes Barbarian-mix entries', () => {
    const methods = parseSkillCalc(loadFixture('calc_herb.lua'), 'Herblore');

    expect(methods.some((m) => m.types.some((t) => /barbarian/i.test(t)))).toBe(false);
    expect(methods.find((m) => m.name === 'Attack mix(2)')).toBeUndefined();
  });

  it('defaults title to name when the calc entry has no title', () => {
    const methods = parseSkillCalc(loadFixture('calc_herb.lua'), 'Herblore');
    const guam = methods.find((m) => m.name === 'Guam leaf');

    expect(guam?.title).toBe('Guam leaf');
    expect(guam?.types).toEqual(['Cleaning grimy herbs']);
  });
});

describe('mergeRecipes', () => {
  // Bucket `recipe` rows for Herblore, hand-built from the live production_json
  // shape (values confirmed against api.php?action=bucket) and cross-checked
  // against ppot.txt's {{Recipe}} template (ticks=2, mat1/mat2, skill1lvl=38,
  // skill1exp=87.5, output1=Prayer potion(3)). ppot.txt itself is page wikitext,
  // not a raw Bucket response, so it informed this fixture rather than being
  // parsed directly.
  const ppotRecipe: RecipeRow = {
    ticks: '2',
    materials: [
      { quantity: '1', name: 'Ranarr potion (unf)' },
      { quantity: '1', name: 'Snape grass' },
    ],
    skills: [{ experience: '87.5', level: '38', name: 'Herblore', boostable: 'Yes' }],
    members: true,
    output: { quantity: '1', name: 'Prayer potion(3)' },
  };

  // 0-xp intermediate: Ranarr potion (unf) from Ranarr weed + Vial of water.
  const unfRecipe: RecipeRow = {
    ticks: '1',
    materials: [
      { quantity: '1', name: 'Ranarr weed' },
      { quantity: '1', name: 'Vial of water' },
    ],
    skills: [{ experience: '0', level: '30', name: 'Herblore', boostable: 'Yes' }],
    members: true,
    output: { quantity: '1', name: 'Ranarr potion (unf)' },
  };

  it('attaches boostable and ticks to a method matching a recipe by output name', () => {
    const methods = parseSkillCalc(loadFixture('calc_herb.lua'), 'Herblore');

    const merged = mergeRecipes(methods, [ppotRecipe]);
    const ppot = merged.find((m) => m.name === 'Prayer potion(3)');

    expect(ppot?.boostable).toBe(true);
    expect(ppot?.ticks).toBe(2);
  });

  it('imports a 0-xp intermediate recipe as a new method with intermediate: true', () => {
    const methods = parseSkillCalc(loadFixture('calc_herb.lua'), 'Herblore');

    const merged = mergeRecipes(methods, [ppotRecipe, unfRecipe]);
    const unf = merged.find((m) => m.name === 'Ranarr potion (unf)');

    expect(unf).toEqual({
      skill: 'Herblore',
      name: 'Ranarr potion (unf)',
      title: 'Ranarr potion (unf)',
      levelReq: 30,
      xpPerAction: 0,
      materials: [
        { name: 'Ranarr weed', quantity: 1 },
        { name: 'Vial of water', quantity: 1 },
      ],
      outputs: [{ name: 'Ranarr potion (unf)', quantity: 1 }],
      types: [],
      members: true,
      boostable: true,
      ticks: 1,
      intermediate: true,
    });
  });

  it('attaches boostable and ticks to every method sharing a duplicate output name, not just one', () => {
    // Two Herblore calc entries that legitimately share a name (analogous to Cooking's
    // 4 "Redberry pie" entries) — both must receive the same recipe's boostable/ticks.
    const duplicateNamed: Method[] = [
      {
        skill: 'Herblore',
        name: 'Prayer potion(3)',
        title: 'Prayer potion',
        levelReq: 38,
        xpPerAction: 87.5,
        materials: [],
        outputs: [{ name: 'Prayer potion(3)', quantity: 1 }],
        types: ['Regular potions'],
        members: true,
      },
      {
        skill: 'Herblore',
        name: 'Prayer potion(3)',
        title: 'Prayer potion',
        levelReq: 38,
        xpPerAction: 87.5,
        materials: [],
        outputs: [{ name: 'Prayer potion(3)', quantity: 1 }],
        types: ['Regular potions', 'Popular'],
        members: true,
      },
    ];

    const merged = mergeRecipes(duplicateNamed, [ppotRecipe]);
    const matches = merged.filter((m) => m.name === 'Prayer potion(3)');

    expect(matches).toHaveLength(2);
    expect(matches.every((m) => m.boostable === true && m.ticks === 2)).toBe(true);
  });

  it('does not import an intermediate recipe for a skill other than the methods being merged', () => {
    const methods = parseSkillCalc(loadFixture('calc_herb.lua'), 'Herblore');
    const otherSkillRecipe: RecipeRow = {
      ...unfRecipe,
      skills: [{ experience: '0', level: '30', name: 'Farming', boostable: 'No' }],
    };

    const merged = mergeRecipes(methods, [otherSkillRecipe]);

    expect(merged.find((m) => m.name === 'Ranarr potion (unf)')).toBeUndefined();
  });
});
