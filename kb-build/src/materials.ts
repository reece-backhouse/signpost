import type { Method } from './methods.js';
import type { PriceMappingEntry } from './wiki.js';

export type { PriceMappingEntry } from './wiki.js';

export interface ItemIdRow {
  page_name: string;
  id: number[];
}

export interface StorelineRow {
  sold_item: string;
  sold_by: string;
  store_buy_price: string;
  store_stock: string;
}

export interface DropslineRow {
  item_name: string;
  page_name: string;
  /** JSON-encoded; carries at least `Rarity`, `Dropped from`, `League region`. */
  drop_json: string;
}

export interface LoclineRow {
  page_name: string;
  /** Absent (not `[]`) on the handful of Bucket rows with no recorded spawn coordinates. */
  coordinates?: string[];
}

interface DropJson {
  Rarity: string;
  'Dropped from': string;
  'League region'?: string;
}

export type SourceType = 'GE' | 'shop' | 'drop' | 'spawn' | 'craft';
export type AccountType = 'all' | 'main';

export interface MaterialSource {
  type: SourceType;
  where: string;
  detail: string;
  accountTypes: AccountType[];
}

export interface Material {
  name: string;
  id: number | null;
  generic: boolean;
  sources: MaterialSource[];
}

interface QuestLike {
  items: { name: string }[];
}
interface DiaryTaskLike {
  items: string[];
}
interface DiaryLike {
  tasks: DiaryTaskLike[];
}
interface MilestoneLike {
  requirements: { items: { name: string }[] };
  ownedIf: { name: string }[];
}

export interface ReferencedItemsInput {
  methods: Method[];
  quests: QuestLike[];
  diaries: DiaryLike[];
  milestones: MilestoneLike[];
}

/** Unions item names referenced anywhere in the KB (ruling 7): method materials/outputs, quest rewards, diary task items, milestone requirement/ownedIf items. */
export function collectReferencedItems(kb: ReferencedItemsInput): string[] {
  const names = new Set<string>();

  for (const method of kb.methods) {
    for (const material of method.materials) names.add(material.name);
    for (const output of method.outputs) names.add(output.name);
  }
  for (const quest of kb.quests) {
    for (const item of quest.items) names.add(item.name);
  }
  for (const diary of kb.diaries) {
    for (const task of diary.tasks) {
      for (const name of task.items) names.add(name);
    }
  }
  for (const milestone of kb.milestones) {
    for (const item of milestone.requirements.items) names.add(item.name);
    for (const owned of milestone.ownedIf) names.add(owned.name);
  }

  return [...names].sort();
}

export interface ResolvedItem {
  id: number | null;
  generic: boolean;
}

/**
 * Resolves each name to an item id: exact case-insensitive match against the prices mapping first, then the
 * Bucket `item_id` rows (first *finite* id — a few real rows, e.g. removed Easter event items, carry only
 * non-numeric historical ids like "hist30710", which `main.ts` turns into `NaN` via `Number(...)`). A name
 * resolved by neither is emitted `{ id: null, generic: true }` rather than dropped — expected for prose item
 * references like "Any pickaxe".
 */
export function resolveItemIds(names: string[], mapping: PriceMappingEntry[], itemIdRows: ItemIdRow[]): Map<string, ResolvedItem> {
  const byMappingName = new Map(mapping.map((entry) => [entry.name.toLowerCase(), entry.id]));
  const byPageName = new Map(itemIdRows.map((row) => [row.page_name.toLowerCase(), row.id.find((id) => Number.isFinite(id))]));

  const resolved = new Map<string, ResolvedItem>();
  for (const name of names) {
    const key = name.toLowerCase();
    // `!= null` (not `!== undefined`): a lookup can yield an explicit `null`/`NaN`-filtered-away
    // id for a real row, which must count as unresolved too, not silently treated as resolved.
    const fromMapping = byMappingName.get(key);
    if (fromMapping != null) {
      resolved.set(name, { id: fromMapping, generic: false });
      continue;
    }
    const fromBucket = byPageName.get(key);
    if (fromBucket != null) {
      resolved.set(name, { id: fromBucket, generic: false });
      continue;
    }
    resolved.set(name, { id: null, generic: true });
  }
  return resolved;
}

function groupByLowerKey<T>(rows: T[], keyOf: (row: T) => string): Map<string, T[]> {
  const groups = new Map<string, T[]>();
  for (const row of rows) {
    const key = keyOf(row).toLowerCase();
    const group = groups.get(key);
    if (group) group.push(row);
    else groups.set(key, [row]);
  }
  return groups;
}

/** Converts a Bucket drop rarity like "1/128" or "Always" to a 0-1 probability for sorting by commonness. */
function rarityToProbability(rarity: string): number {
  const fraction = /^(\d+(?:\.\d+)?)\s*\/\s*(\d+(?:\.\d+)?)$/.exec(rarity.trim());
  if (fraction) return Number(fraction[1]) / Number(fraction[2]);
  if (/always/i.test(rarity)) return 1;
  return 0;
}

const DROP_CAP = 8;

export interface BuildMaterialsInput {
  names: string[];
  resolved: Map<string, ResolvedItem>;
  mapping: PriceMappingEntry[];
  storelineRows: StorelineRow[];
  droplineRows: DropslineRow[];
  loclineRows: LoclineRow[];
  methods: Method[];
}

/** Builds `materials.json`'s `materials` array: every KB-referenced item id/generic flag plus its GE/shop/drop/spawn/craft sources. */
export function buildMaterials(input: BuildMaterialsInput): Material[] {
  const { names, resolved, mapping, storelineRows, droplineRows, loclineRows, methods } = input;

  const mappingByName = new Set(mapping.map((entry) => entry.name.toLowerCase()));
  const storeByItem = groupByLowerKey(storelineRows, (row) => row.sold_item);
  const dropsByItem = groupByLowerKey(droplineRows, (row) => row.item_name);
  const spawnsByItem = groupByLowerKey(loclineRows, (row) => row.page_name);
  const craftsByOutput = new Map<string, Method[]>();
  for (const method of methods) {
    for (const output of method.outputs) {
      const key = output.name.toLowerCase();
      const group = craftsByOutput.get(key);
      if (group) group.push(method);
      else craftsByOutput.set(key, [method]);
    }
  }

  const materials = names.map((name): Material => {
    const res = resolved.get(name);
    if (!res) {
      throw new Error(`No id resolution for referenced item "${name}"`);
    }

    const sources: MaterialSource[] = [];
    const key = name.toLowerCase();

    if (mappingByName.has(key)) {
      const entry = mapping.find((m) => m.name.toLowerCase() === key)!;
      sources.push({
        type: 'GE',
        where: 'Grand Exchange',
        detail: entry.limit !== undefined ? `buy limit ${entry.limit}` : '',
        accountTypes: ['main'],
      });
    }

    for (const row of storeByItem.get(key) ?? []) {
      sources.push({
        type: 'shop',
        where: row.sold_by,
        detail: `${row.store_buy_price} gp, stock ${row.store_stock}`,
        accountTypes: ['all'],
      });
    }

    const drops = (dropsByItem.get(key) ?? [])
      .map((row) => {
        const dj = JSON.parse(row.drop_json) as DropJson;
        return {
          where: dj['Dropped from'] || row.page_name,
          detail: dj.Rarity,
          wilderness: (dj['League region'] ?? '').toLowerCase().includes('wilderness'),
          probability: rarityToProbability(dj.Rarity),
        };
      })
      .sort((a, b) => Number(a.wilderness) - Number(b.wilderness) || b.probability - a.probability)
      .slice(0, DROP_CAP);
    for (const drop of drops) {
      sources.push({ type: 'drop', where: drop.where, detail: drop.detail, accountTypes: ['all'] });
    }

    for (const row of spawnsByItem.get(key) ?? []) {
      // A handful of live locline rows carry no `coordinates` field at all (Bucket
      // omits empty array fields rather than returning `[]`); skip those.
      if (!row.coordinates || row.coordinates.length === 0) continue;
      sources.push({ type: 'spawn', where: row.page_name, detail: `${row.coordinates.length} spawns`, accountTypes: ['all'] });
    }

    for (const method of craftsByOutput.get(key) ?? []) {
      sources.push({
        type: 'craft',
        where: method.name,
        detail: method.materials.map((m) => `${m.quantity}x ${m.name}`).join(', '),
        accountTypes: ['all'],
      });
    }

    return { name, id: res.id, generic: res.generic, sources };
  });

  for (const material of materials) {
    if ((material.id === null) !== material.generic) {
      throw new Error(`Material "${material.name}" violates the id/generic invariant: id=${material.id}, generic=${material.generic}`);
    }
  }

  return materials.sort((a, b) => a.name.localeCompare(b.name));
}
