import { describe, expect, it } from 'vitest';
import {
  addMissingGatheringMaterials,
  resolveGatheringPlans,
  gatheringMaterialNames,
  type GatheringPlanDraft,
  type GatheringRisk,
} from '../src/gathering.js';
import type { Material } from '../src/materials.js';

function draft(overrides: Partial<GatheringPlanDraft> = {}): GatheringPlanDraft {
  return {
    item: 'Snape grass',
    id: null,
    title: 'Pick snape grass',
    requires: { skills: [], quests: [], items: [], notes: '' },
    ratePerHour: 950,
    steps: ['Go to the snape grass patch.'],
    alternatives: [],
    wikiUrl: 'https://oldschool.runescape.wiki/w/Snape_grass',
    ...overrides,
  };
}

function material(name: string, id: number | null): Material {
  return { name, id, generic: id === null, wikiUrl: `https://oldschool.runescape.wiki/w/${name.replace(/ /g, '_')}`, sources: [] };
}

describe('resolveGatheringPlans', () => {
  it('fills in the id for every plan whose item resolves to a materials.json entry', () => {
    const plans = resolveGatheringPlans(
      [draft({ item: 'Snape grass' }), draft({ item: 'Bow string' })],
      [material('Snape grass', 231), material('Bow string', 1777)],
    );

    expect(plans).toEqual([
      expect.objectContaining({ item: 'Snape grass', id: 231 }),
      expect.objectContaining({ item: 'Bow string', id: 1777 }),
    ]);
  });

  it('throws, naming every unresolved item, rather than dropping it silently', () => {
    expect(() => resolveGatheringPlans([draft({ item: 'Snape grass' }), draft({ item: 'Nonexistent thing' })], [material('Snape grass', 231)]))
      .toThrow(/Nonexistent thing/);
  });

  it('renames "Cannonballs" to the real materials.json entry "Steel cannonball" before resolving', () => {
    const plans = resolveGatheringPlans([draft({ item: 'Cannonballs' })], [material('Steel cannonball', 2)]);

    expect(plans).toEqual([expect.objectContaining({ item: 'Steel cannonball', id: 2 })]);
  });

  it('normalises bare-string steps to the object form with empty requires', () => {
    const plans = resolveGatheringPlans(
      [draft({ steps: ['Go to the patch.'], alternatives: [{ title: 'Alt', steps: ['Alt step.'], requires: { skills: [], quests: [], items: [] } }] })],
      [material('Snape grass', 231)],
    );

    expect(plans[0]!.steps).toEqual([{ text: 'Go to the patch.', requires: { skills: [], quests: [] } }]);
    expect(plans[0]!.alternatives[0]!.steps).toEqual([{ text: 'Alt step.', requires: { skills: [], quests: [] } }]);
    expect(plans[0]!.risk).toBe('none');
    expect(plans[0]!.alternatives[0]!.risk).toBe('none');
  });

  it('keeps object-form steps and fills in whichever requires list is missing', () => {
    const plans = resolveGatheringPlans(
      [draft({ steps: [
        { text: 'Guild patch.', requires: { skills: [{ skill: 'Farming', level: 65 }] } },
        { text: 'Weiss patch.', requires: { quests: ['Making Friends with My Arm'] } },
        { text: 'Plain.' },
      ] })],
      [material('Snape grass', 231)],
    );

    expect(plans[0]!.steps).toEqual([
      { text: 'Guild patch.', requires: { skills: [{ skill: 'Farming', level: 65 }], quests: [] } },
      { text: 'Weiss patch.', requires: { skills: [], quests: ['Making Friends with My Arm'] } },
      { text: 'Plain.', requires: { skills: [], quests: [] } },
    ]);
  });

  it('treats a materials.json entry with a null id as unresolved', () => {
    expect(() => resolveGatheringPlans([draft({ item: 'Blue dragon scales' })], [material('Blue dragon scales', null)]))
      .toThrow(/Blue dragon scales/);
  });

  it('resolves named tools and explicit ids for both primary and alternative requirements', () => {
    const plan = draft({
      risk: 'wilderness',
      requires: { skills: [], quests: [], items: ['Knife', { name: 'Law rune', id: 563, quantity: 27 }] },
      alternatives: [{
        title: 'Trek',
        risk: 'hardcore-unsafe',
        steps: ['Complete a trek.'],
        requires: { skills: [], quests: [], items: [{ name: 'Spade' }] },
      }],
    });
    const resolved = resolveGatheringPlans([plan],
      [material('Snape grass', 231), material('Knife', 946), material('Law rune', 563), material('Spade', 952)])[0]!;
    expect(resolved.requires.items).toEqual([{ name: 'Knife', id: 946, quantity: 1 }, { name: 'Law rune', id: 563, quantity: 27 }]);
    expect(resolved.alternatives[0]!.requires.items).toEqual([{ name: 'Spade', id: 952, quantity: 1 }]);
    expect(resolved.risk).toBe('wilderness');
    expect(resolved.alternatives[0]!.risk).toBe('hardcore-unsafe');
  });

  it.each(['Missing tool', { name: 'Missing tool', id: 946 }])('rejects an unknown bring-item name even with an explicit id: %j', (item) => {
    expect(() => resolveGatheringPlans(
      [draft({ requires: { skills: [], quests: [], items: [item] } })],
      [material('Snape grass', 231), material('Knife', 946)],
    )).toThrow(/Pick snape grass requires.items: unknown material Missing tool/);
  });

  it('rejects a known bring item whose material has no usable item id', () => {
    expect(() => resolveGatheringPlans(
      [draft({ requires: { skills: [], quests: [], items: ['Knife'] } })],
      [material('Snape grass', 231), material('Knife', null)],
    )).toThrow(/requires.items: unknown material Knife/);
  });

  it('rejects mismatched explicit ids rather than ticking a different item', () => {
    expect(() => resolveGatheringPlans(
      [draft({ requires: { skills: [], quests: [], items: [{ name: 'Knife', id: 952 }] } })],
      [material('Snape grass', 231), material('Knife', 946), material('Spade', 952)],
    )).toThrow(/requires.items: mismatched id for Knife/);
  });

  it('names the offending alternative when its required item is unknown', () => {
    expect(() => resolveGatheringPlans(
      [draft({ alternatives: [{
        title: 'Boat route', steps: ['Take a boat.'], requires: { skills: [], quests: [], items: ['Mystery ticket'] },
      }] })],
      [material('Snape grass', 231)],
    )).toThrow(/Pick snape grass \/ Boat route requires.items: unknown material Mystery ticket/);
  });

  it.each([0, -1, 1.5, 2_147_483_648])('rejects bring quantities that cannot be loaded as positive Java integers: %s', (quantity) => {
    expect(() => resolveGatheringPlans(
      [draft({ requires: { skills: [], quests: [], items: [{ name: 'Knife', quantity }] } })],
      [material('Snape grass', 231), material('Knife', 946)],
    )).toThrow(/requires.items: invalid quantity for Knife/);
  });

  it('rejects unknown risk labels on either a plan or an alternative', () => {
    const unknownRisk = 'safe-ish' as GatheringRisk;
    expect(() => resolveGatheringPlans([draft({ risk: unknownRisk })], [material('Snape grass', 231)]))
      .toThrow(/Pick snape grass: unknown risk/);
    expect(() => resolveGatheringPlans([draft({ alternatives: [{
      title: 'Boat route', steps: ['Take a boat.'], requires: { skills: [], quests: [], items: [] }, risk: unknownRisk,
    }] })], [material('Snape grass', 231)]))
      .toThrow(/Pick snape grass \/ Boat route: unknown risk/);
  });
});

describe('addMissingGatheringMaterials', () => {
  const bucketData = { mapping: [], storelineRows: [], droplineRows: [], loclineRows: [], methods: [] };

  it('includes tools from primary and alternative routes when finding materials to resolve', () => {
    const plans = [draft({
      requires: { skills: [], quests: [], items: ['Staff of air'] },
      alternatives: [{
        title: 'Mine', steps: ['Mine.'], requires: { skills: [], quests: [], items: [{ name: 'Bronze pickaxe', id: 1265 }, 'Staff of air'] },
      }],
    })];
    expect(gatheringMaterialNames(plans)).toEqual(['Snape grass', 'Staff of air', 'Bronze pickaxe']);
    expect(addMissingGatheringMaterials([material('Snape grass', 231)], plans, bucketData))
      .toEqual(expect.arrayContaining([expect.objectContaining({ name: 'Staff of air', id: 1381 }), expect.objectContaining({ name: 'Bronze pickaxe', id: 1265 })]));
  });

  it('adds Blue dragon scales and Papaya fruit with their fixed ids when the draft references them and materials.json does not have them', () => {
    const materials = addMissingGatheringMaterials(
      [material('Snape grass', 231)],
      [draft({ item: 'Blue dragon scales' }), draft({ item: 'Papaya fruit' })],
      bucketData,
    );

    const scales = materials.find((m) => m.name === 'Blue dragon scales');
    const papaya = materials.find((m) => m.name === 'Papaya fruit');
    expect(scales).toEqual(expect.objectContaining({ id: 243, generic: false }));
    expect(papaya).toEqual(expect.objectContaining({ id: 5972, generic: false }));
    expect(materials).toHaveLength(3);
  });

  it('picks up a GE source when the mapping has the item', () => {
    const materials = addMissingGatheringMaterials(
      [],
      [draft({ item: 'Blue dragon scales' })],
      { ...bucketData, mapping: [{ id: 243, name: 'Blue dragon scales', members: true, value: 1000 }] },
    );

    const scales = materials.find((m) => m.name === 'Blue dragon scales')!;
    expect(scales.sources).toEqual([{ type: 'GE', where: 'Grand Exchange', detail: '', accountTypes: ['main'] }]);
  });

  it('is a no-op once both materials are already present', () => {
    const materials = [material('Blue dragon scales', 243), material('Papaya fruit', 5972), material('Snape grass', 231)];
    const result = addMissingGatheringMaterials(materials, [draft({ item: 'Blue dragon scales' }), draft({ item: 'Papaya fruit' })], bucketData);

    expect(result).toBe(materials);
  });

  it('does not add a material the draft never references, even if it were in the missing-id table', () => {
    const materials = addMissingGatheringMaterials([], [draft({ item: 'Snape grass' })], bucketData);
    expect(materials).toEqual([]);
  });
});
