import { describe, expect, it } from 'vitest';
import {
  buildMaterials,
  collectReferencedItems,
  resolveItemIds,
  type DropslineRow,
  type ItemIdRow,
  type LoclineRow,
  type PriceMappingEntry,
  type StorelineRow,
} from '../src/materials.js';
import type { Method } from '../src/methods.js';

describe('collectReferencedItems', () => {
  it('unions item names from method materials/outputs, quest items, diary task items, and milestone requirement/ownedIf items', () => {
    const kb = {
      methods: [
        {
          skill: 'Herblore',
          name: 'Prayer potion(3)',
          title: 'Prayer potion',
          levelReq: 38,
          xpPerAction: 87.5,
          materials: [
            { name: 'Ranarr potion (unf)', quantity: 1 },
            { name: 'Snape grass', quantity: 1 },
          ],
          outputs: [{ name: 'Prayer potion(3)', quantity: 1 }],
          types: ['Regular potions'],
          members: true,
        },
      ] as Method[],
      quests: [{ items: [{ name: 'Rune axe', quantity: 1 }] }],
      diaries: [{ tasks: [{ items: ['Amulet of glory'] }] }],
      milestones: [
        {
          requirements: { items: [{ name: 'Coins', quantity: 130000 }] },
          ownedIf: [{ name: 'Barrows gloves' }],
        },
      ],
    };

    const names = collectReferencedItems(kb);

    expect(names).toEqual(
      ['Amulet of glory', 'Barrows gloves', 'Coins', 'Prayer potion(3)', 'Ranarr potion (unf)', 'Rune axe', 'Snape grass'].sort(),
    );
  });
});

describe('resolveItemIds', () => {
  const mapping: PriceMappingEntry[] = [
    { id: 231, name: 'Snape grass', members: true, value: 10 },
    { id: 139, name: 'Prayer potion(3)', members: true, value: 152 },
  ];
  const itemIdRows: ItemIdRow[] = [{ page_name: 'Barrows gloves', id: [7462] }];

  it('resolves via the mapping first, case-insensitively', () => {
    const resolved = resolveItemIds(['snape grass'], mapping, itemIdRows);
    expect(resolved.get('snape grass')).toEqual({ id: 231, generic: false });
  });

  it('falls back to the Bucket item_id rows for items absent from the mapping', () => {
    const resolved = resolveItemIds(['Barrows gloves'], mapping, itemIdRows);
    expect(resolved.get('Barrows gloves')).toEqual({ id: 7462, generic: false });
  });

  it('marks a name unresolved by both sources as generic with a null id', () => {
    const resolved = resolveItemIds(['Any pickaxe'], mapping, itemIdRows);
    expect(resolved.get('Any pickaxe')).toEqual({ id: null, generic: true });
  });
});

describe('buildMaterials', () => {
  const methods: Method[] = [
    {
      skill: 'Herblore',
      name: 'Prayer potion(3)',
      title: 'Prayer potion',
      levelReq: 38,
      xpPerAction: 87.5,
      materials: [
        { name: 'Ranarr potion (unf)', quantity: 1 },
        { name: 'Snape grass', quantity: 1 },
      ],
      outputs: [{ name: 'Prayer potion(3)', quantity: 1 }],
      types: ['Regular potions'],
      members: true,
    },
  ];

  const mapping: PriceMappingEntry[] = [{ id: 231, name: 'Snape grass', members: true, value: 10, limit: 13000 }];

  const storelineRows: StorelineRow[] = [
    { sold_item: 'Vial', sold_by: "Jatix's Herblore Shop.", store_buy_price: '1', store_stock: '800' },
  ];

  const droplineRows: DropslineRow[] = [
    {
      item_name: 'Snape grass',
      page_name: 'Chaos druid',
      drop_json: JSON.stringify({ Rarity: '1/128', 'Dropped from': 'Chaos druid', 'League region': 'wilderness' }),
    },
    {
      item_name: 'Snape grass',
      page_name: 'Cave bug',
      drop_json: JSON.stringify({ Rarity: '1/128', 'Dropped from': 'Cave bug', 'League region': 'misthalin' }),
    },
    ...Array.from({ length: 8 }, (_, i) => ({
      item_name: 'Snape grass',
      page_name: `Monster ${i}`,
      drop_json: JSON.stringify({ Rarity: `1/${i + 2}`, 'Dropped from': `Monster ${i}`, 'League region': 'misthalin' }),
    })),
  ];

  const loclineRows: LoclineRow[] = [
    { page_name: 'Snape grass', coordinates: ['2500,3730', '2500,3731'] },
    { page_name: 'Snape grass', coordinates: ['1835,3640'] },
  ];

  it('adds a GE source for a tradeable item in the mapping (accountTypes main)', () => {
    const resolved = resolveItemIds(['Snape grass'], mapping, []);
    const materials = buildMaterials({
      names: ['Snape grass'],
      resolved,
      mapping,
      storelineRows: [],
      droplineRows: [],
      loclineRows: [],
      methods: [],
    });

    const snape = materials.find((m) => m.name === 'Snape grass');
    expect(snape?.sources).toContainEqual(expect.objectContaining({ type: 'GE', accountTypes: ['main'] }));
  });

  it('does not add a GE source for an item absent from the mapping', () => {
    const resolved = resolveItemIds(['Vial'], [], []);
    const materials = buildMaterials({
      names: ['Vial'],
      resolved,
      mapping: [],
      storelineRows,
      droplineRows: [],
      loclineRows: [],
      methods: [],
    });

    const vial = materials.find((m) => m.name === 'Vial');
    expect(vial?.sources.some((s) => s.type === 'GE')).toBe(false);
    expect(vial?.sources).toContainEqual(
      expect.objectContaining({ type: 'shop', where: "Jatix's Herblore Shop.", accountTypes: ['all'] }),
    );
  });

  it('caps drop sources at 8, preferring non-wilderness and higher rarity', () => {
    const resolved = resolveItemIds(['Snape grass'], mapping, []);
    const materials = buildMaterials({
      names: ['Snape grass'],
      resolved,
      mapping: [],
      storelineRows: [],
      droplineRows,
      loclineRows: [],
      methods: [],
    });

    const snape = materials.find((m) => m.name === 'Snape grass')!;
    const dropSources = snape.sources.filter((s) => s.type === 'drop');
    expect(dropSources).toHaveLength(8);
    expect(dropSources.some((s) => s.where === 'Chaos druid')).toBe(false);
  });

  it('adds a spawn source per locline row with a spawn-count detail', () => {
    const resolved = resolveItemIds(['Snape grass'], mapping, []);
    const materials = buildMaterials({
      names: ['Snape grass'],
      resolved,
      mapping: [],
      storelineRows: [],
      droplineRows: [],
      loclineRows,
      methods: [],
    });

    const snape = materials.find((m) => m.name === 'Snape grass')!;
    const spawnSources = snape.sources.filter((s) => s.type === 'spawn');
    expect(spawnSources).toHaveLength(2);
    expect(spawnSources.map((s) => s.detail).sort()).toEqual(['1 spawns', '2 spawns']);
  });

  it('skips a locline row with no coordinates field (live Bucket data omits it rather than sending [])', () => {
    const resolved = resolveItemIds(['Snape grass'], mapping, []);
    const materials = buildMaterials({
      names: ['Snape grass'],
      resolved,
      mapping: [],
      storelineRows: [],
      droplineRows: [],
      loclineRows: [{ page_name: 'Snape grass' } as LoclineRow],
      methods: [],
    });

    const snape = materials.find((m) => m.name === 'Snape grass')!;
    expect(snape.sources.some((s) => s.type === 'spawn')).toBe(false);
  });

  it('adds a craft source when a method outputs this item', () => {
    const resolved = resolveItemIds(['Prayer potion(3)'], [], []);
    const materials = buildMaterials({
      names: ['Prayer potion(3)'],
      resolved,
      mapping: [],
      storelineRows: [],
      droplineRows: [],
      loclineRows: [],
      methods,
    });

    const ppot = materials.find((m) => m.name === 'Prayer potion(3)')!;
    expect(ppot.sources).toContainEqual(
      expect.objectContaining({ type: 'craft', where: 'Prayer potion(3)', accountTypes: ['all'] }),
    );
  });

  it('sorts materials by name', () => {
    const resolved = resolveItemIds(['Snape grass', 'Coins'], [], []);
    const materials = buildMaterials({
      names: ['Snape grass', 'Coins'],
      resolved,
      mapping: [],
      storelineRows: [],
      droplineRows: [],
      loclineRows: [],
      methods: [],
    });

    expect(materials.map((m) => m.name)).toEqual(['Coins', 'Snape grass']);
  });
});
