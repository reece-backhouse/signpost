import { addMissingMaterials, type BuildMaterialsInput, type Material } from './materials.js';

export interface GatheringSkillReq {
  skill: string;
  level: number;
}

export type GatheringRisk = 'none' | 'wilderness' | 'hardcore-unsafe';
export type GatheringBringItem = string | { name: string; id?: number; quantity?: number };

export interface GatheringRequires {
  skills: GatheringSkillReq[];
  quests: string[];
  items: GatheringBringItem[];
  notes?: string;
}

/** A plan step as emitted: the plugin drops it when the account misses any of `requires`. */
export interface GatheringStep {
  text: string;
  requires: { skills: GatheringSkillReq[]; quests: string[] };
}

/** A plan step as curated: a bare string, or an object whose `requires` lists may be omitted. */
export type GatheringStepInput = string | { text: string; requires?: { skills?: GatheringSkillReq[]; quests?: string[] } };

export interface GatheringAlternativeDraft {
  title: string;
  steps: GatheringStepInput[];
  requires: GatheringRequires;
  risk?: GatheringRisk;
}

export interface GatheringAlternative extends Omit<GatheringAlternativeDraft, 'steps'> {
  steps: GatheringStep[];
}

/** One curated `data/gathering.json` plan before `id` resolution. */
export interface GatheringPlanDraft {
  item: string;
  id: number | null;
  title: string;
  requires: GatheringRequires;
  ratePerHour: number | null;
  steps: GatheringStepInput[];
  alternatives: GatheringAlternativeDraft[];
  wikiUrl: string;
  risk?: GatheringRisk;
}

/**
 * As {@link GatheringPlanDraft}, with `id` resolved (never null) and every step in object form -
 * the emitted `gathering.json` shape.
 */
export interface GatheringPlan extends Omit<GatheringPlanDraft, 'id' | 'steps' | 'alternatives'> {
  id: number;
  steps: GatheringStep[];
  alternatives: GatheringAlternative[];
}

/** Normalises a curated step to the emitted object form, so the plugin maps one shape. */
export function normaliseStep(step: GatheringStepInput): GatheringStep {
  if (typeof step === 'string') {
    return { text: step, requires: { skills: [], quests: [] } };
  }
  return { ...step, requires: { ...step.requires, skills: step.requires?.skills ?? [], quests: step.requires?.quests ?? [] } };
}

/**
 * Curated draft item names that predate a `materials.json` entry:
 * added to `materials.json` by {@link addMissingGatheringMaterials} with these ids (from the prices
 * mapping) rather than treated as unresolved.
 */
export const MISSING_GATHERING_MATERIAL_IDS: Record<string, number> = {
  'Blue dragon scales': 243,
  'Papaya fruit': 5972,
  'Ammo mould': 4,
  'Antipoison(4)': 2446,
  'Avantoe seed': 5298,
  'Bale of flax': 31045,
  'Barley seed': 5305,
  'Bow string spool': 31052,
  'Bronze pickaxe': 1265,
  'Brown apron': 1757,
  'Double ammo mould': 27012,
  'Dwarf weed seed': 5303,
  'Grape seed': 13657,
  'Harpoon': 311,
  'Harralander seed': 5294,
  'Irit seed': 5297,
  'Jangerberry seed': 5104,
  'Karambwan vessel': 3157,
  'Kwuarm seed': 5299,
  'Lantadyme seed': 5302,
  'Leaf-bladed spear': 4158,
  'Limpwurt seed': 5100,
  'Mahogany sapling': 21480,
  'Palm sapling': 5502,
  'Papaya tree seed': 5288,
  'Poison ivy seed': 5106,
  'Potato cactus seed': 22873,
  'Ranarr seed': 5295,
  'Restore potion(4)': 2430,
  'Saltpetre': 13421,
  'Silver sickle (b)': 2963,
  'Snapdragon seed': 5300,
  'Snape grass seed': 22879,
  'Staff of air': 1381,
  'Supercompost': 6034,
  'Teak sapling': 21477,
  'Teleport crystal (5)': 13102,
  'Toadflax seed': 5296,
  'Torstol seed': 5304,
  'Whiteberry seed': 5105,
  'Zamorak monk bottom': 1033,
  'Zamorak monk top': 1035,
};

/** The curated draft names the real `materials.json` entry "Steel cannonball" (id 2), not "Cannonballs". */
const ITEM_RENAMES: Record<string, string> = {
  Cannonballs: 'Steel cannonball',
};

function canonicalName(item: string): string {
  return ITEM_RENAMES[item] ?? item;
}

export function gatheringMaterialNames(plans: GatheringPlanDraft[]): string[] {
  const names = new Set<string>();
  for (const plan of plans) {
    names.add(canonicalName(plan.item));
    for (const route of [plan, ...plan.alternatives]) {
      for (const item of route.requires.items) names.add(typeof item === 'string' ? item : item.name);
    }
  }
  return [...names];
}

/**
 * Adds a `materials.json` entry (built the same way as every other material) for each
 * draft plan item in {@link MISSING_GATHERING_MATERIAL_IDS} not already present in `materials`. A
 * no-op (returns `materials` unchanged) once both have been added.
 */
export function addMissingGatheringMaterials(
  materials: Material[],
  draftPlans: GatheringPlanDraft[],
  bucketData: Omit<BuildMaterialsInput, 'names' | 'resolved'>,
): Material[] {
  const wanted = new Map<string, number>();
  for (const name of gatheringMaterialNames(draftPlans)) {
    if (Object.prototype.hasOwnProperty.call(MISSING_GATHERING_MATERIAL_IDS, name)) {
      wanted.set(name, MISSING_GATHERING_MATERIAL_IDS[name]!);
    }
  }
  return addMissingMaterials(materials, wanted, bucketData);
}

/**
 * Validates every draft plan's `item` resolves to a `materials.json` entry by exact name (after the
 * "Cannonballs" -> "Steel cannonball" rename), fills in its `id`, and normalises every step (plan and
 * alternative) to the object form. Throws, naming every unresolved
 * item, rather than silently dropping one.
 */
export function resolveGatheringPlans(draftPlans: GatheringPlanDraft[], materials: Material[]): GatheringPlan[] {
  const byName = new Map(materials.map((m) => [m.name, m]));
  const unresolved: string[] = [];
  const risk = (value: GatheringRisk | undefined, context: string): GatheringRisk => {
    if (value === undefined) return 'none';
    if (!['none', 'wilderness', 'hardcore-unsafe'].includes(value)) {
      throw new Error(`${context}: unknown risk ${JSON.stringify(value)}`);
    }
    return value;
  };
  const requires = (value: GatheringRequires, context: string): GatheringRequires => ({
    ...value,
    items: value.items.map((item) => {
      const name = typeof item === 'string' ? item : item.name;
      const material = byName.get(name);
      if (!material || material.id === null) {
        throw new Error(`${context} requires.items: unknown material ${name}`);
      }
      if (typeof item !== 'string' && item.id !== undefined && item.id !== material.id) {
        throw new Error(`${context} requires.items: mismatched id for ${name}`);
      }
      const quantity = typeof item === 'string' ? 1 : item.quantity ?? 1;
      if (!Number.isSafeInteger(quantity) || quantity <= 0 || quantity > 2_147_483_647) {
        throw new Error(`${context} requires.items: invalid quantity for ${name}`);
      }
      return { name, id: material.id, quantity };
    }),
  });

  const plans = draftPlans.map((draft): GatheringPlan | null => {
    const item = canonicalName(draft.item);
    const material = byName.get(item);
    if (!material || material.id === null) {
      unresolved.push(item);
      return null;
    }
    return {
      ...draft,
      item,
      id: material.id,
      risk: risk(draft.risk, draft.title),
      requires: requires(draft.requires, draft.title),
      steps: draft.steps.map(normaliseStep),
      alternatives: draft.alternatives.map((alt) => ({
        ...alt,
        risk: risk(alt.risk, `${draft.title} / ${alt.title}`),
        requires: requires(alt.requires, `${draft.title} / ${alt.title}`),
        steps: alt.steps.map(normaliseStep),
      })),
    };
  });

  if (unresolved.length > 0) {
    throw new Error(`Gathering plan item(s) with no materials.json entry: ${unresolved.join(', ')}`);
  }

  return plans as GatheringPlan[];
}
