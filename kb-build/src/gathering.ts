import { buildMaterials, type BuildMaterialsInput, type Material } from './materials.js';

export interface GatheringSkillReq {
  skill: string;
  level: number;
}

export interface GatheringRequires {
  skills: GatheringSkillReq[];
  quests: string[];
  items: string[];
  notes?: string;
}

export interface GatheringAlternative {
  title: string;
  steps: string[];
  requires: GatheringRequires;
}

/** One curated `data/gathering.json` plan before `id` resolution (ruling 28). */
export interface GatheringPlanDraft {
  item: string;
  id: number | null;
  title: string;
  requires: GatheringRequires;
  ratePerHour: number | null;
  steps: string[];
  alternatives: GatheringAlternative[];
  wikiUrl: string;
}

/** As {@link GatheringPlanDraft}, with `id` resolved (never null) - the emitted `gathering.json` shape. */
export interface GatheringPlan extends Omit<GatheringPlanDraft, 'id'> {
  id: number;
}

/**
 * Curated draft item names that predate a `materials.json` entry (ruling 28 / gathering-notes.md):
 * added to `materials.json` by {@link addMissingGatheringMaterials} with these ids (from the prices
 * mapping) rather than treated as unresolved.
 */
export const MISSING_GATHERING_MATERIAL_IDS: Record<string, number> = {
  'Blue dragon scales': 243,
  'Papaya fruit': 5972,
};

/** The curated draft names the real `materials.json` entry "Steel cannonball" (id 2), not "Cannonballs". */
const ITEM_RENAMES: Record<string, string> = {
  Cannonballs: 'Steel cannonball',
};

function canonicalName(item: string): string {
  return ITEM_RENAMES[item] ?? item;
}

/**
 * Adds a `materials.json` entry (built the same way as every other material - ruling 28) for each
 * draft plan item in {@link MISSING_GATHERING_MATERIAL_IDS} not already present in `materials`. A
 * no-op (returns `materials` unchanged) once both have been added.
 */
export function addMissingGatheringMaterials(
  materials: Material[],
  draftPlans: GatheringPlanDraft[],
  bucketData: Omit<BuildMaterialsInput, 'names' | 'resolved'>,
): Material[] {
  const have = new Set(materials.map((m) => m.name));
  const names = [...new Set(draftPlans.map((p) => canonicalName(p.item)))].filter(
    (name) => !have.has(name) && Object.prototype.hasOwnProperty.call(MISSING_GATHERING_MATERIAL_IDS, name),
  );
  if (names.length === 0) {
    return materials;
  }

  const resolved = new Map(names.map((name) => [name, { id: MISSING_GATHERING_MATERIAL_IDS[name]!, generic: false }]));
  const added = buildMaterials({ ...bucketData, names, resolved });
  return [...materials, ...added].sort((a, b) => a.name.localeCompare(b.name));
}

/**
 * Validates every draft plan's `item` resolves to a `materials.json` entry by exact name (after the
 * "Cannonballs" -> "Steel cannonball" rename) and fills in its `id`. Throws, naming every unresolved
 * item, rather than silently dropping one (ruling 28: fail loud).
 */
export function resolveGatheringPlans(draftPlans: GatheringPlanDraft[], materials: Material[]): GatheringPlan[] {
  const byName = new Map(materials.map((m) => [m.name, m]));
  const unresolved: string[] = [];

  const plans = draftPlans.map((draft): GatheringPlan | null => {
    const item = canonicalName(draft.item);
    const material = byName.get(item);
    if (!material || material.id === null) {
      unresolved.push(item);
      return null;
    }
    return { ...draft, item, id: material.id };
  });

  if (unresolved.length > 0) {
    throw new Error(`Gathering plan item(s) with no materials.json entry: ${unresolved.join(', ')}`);
  }

  return plans as GatheringPlan[];
}
