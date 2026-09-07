import { parseLua, type LuaTable, type LuaValue } from './lua.js';

export interface MethodItem {
  name: string;
  quantity: number;
}

export interface Method {
  skill: string;
  name: string;
  title: string;
  levelReq: number;
  xpPerAction: number;
  materials: MethodItem[];
  outputs: MethodItem[];
  types: string[];
  members: boolean;
  boostable?: boolean;
  ticks?: number;
  intermediate?: boolean;
}

/** A parsed Bucket `recipe` row's `production_json` (field names/casing match the live API; `output` is absent for the handful of non-item rows like "Broodoo victim"). */
export interface RecipeRow {
  ticks: string;
  materials: { quantity: string; name: string }[];
  skills: { experience: string; level: string; name: string; boostable: string }[];
  members: boolean;
  output: { quantity: string; name: string };
}

function asTable(value: LuaValue | undefined, context: string): LuaTable {
  if (value === null || value === undefined || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(`Expected a Lua table for ${context}`);
  }
  return value;
}

/** Converts a Lua table using 1-based numeric-string keys ("1","2",...) into an ordered array. */
function toArray(table: LuaTable): LuaValue[] {
  return Object.keys(table)
    .filter((key) => /^\d+$/.test(key))
    .sort((a, b) => Number(a) - Number(b))
    .map((key) => table[key]!);
}

const BARBARIAN_TYPE = /barbarian/i;

/**
 * The product of an action, if any: a skill-calc row is named after the item it acts on (bury "Dragon bones",
 * burn "Yew logs", build with "Plank"), so an output that is also one of the materials is not a product at all -
 * it would make the route planner's simulated bank self-replenishing. Such a method has no reusable output.
 */
function productOutputs(name: string, quantity: number, materials: MethodItem[]): MethodItem[] {
  return materials.some((m) => m.name === name) ? [] : [{ name, quantity }];
}

/** Parses a `Module:Skill calc/<Skill>` wiki module into training methods, dropping Barbarian-mix entries (ruling 15). */
export function parseSkillCalc(lua: string, skill: string): Method[] {
  const root = asTable(parseLua(lua), 'Module:Skill calc root');
  const entries = toArray(root);

  const methods: Method[] = [];
  for (const raw of entries) {
    const entry = asTable(raw, `${skill} calc entry`);
    const name = String(entry.name);
    const types = String(entry.type)
      .split(',')
      .map((t) => t.trim());
    if (types.some((t) => BARBARIAN_TYPE.test(t))) continue;

    const materialsTable = asTable(entry.materials, `${skill} calc entry "${name}" materials`);
    const materials = toArray(materialsTable).map((rawMaterial) => {
      const material = asTable(rawMaterial, `${skill} calc entry "${name}" material`);
      return { name: String(material.name), quantity: material.quantity !== undefined ? Number(material.quantity) : 1 };
    });

    methods.push({
      skill,
      name,
      title: entry.title !== undefined ? String(entry.title) : name,
      levelReq: Number(entry.level),
      xpPerAction: Number(entry.xp),
      materials,
      outputs: productOutputs(name, 1, materials),
      types,
      members: String(entry.members) === 'Yes',
    });
  }

  return methods;
}

/** Parses one Bucket `recipe` row's `production_json` string. Returns null for the handful of rows with no real item output (e.g. "Broodoo victim", "Herbiboar"). */
export function parseRecipeRow(productionJson: string): RecipeRow | null {
  const parsed = JSON.parse(productionJson) as { output?: unknown };
  if (typeof parsed.output !== 'object' || parsed.output === null || !('name' in parsed.output)) {
    return null;
  }
  return parsed as RecipeRow;
}

function isYes(value: string): boolean {
  return value.toLowerCase() === 'yes';
}

/**
 * Attaches `boostable`/`ticks` from Bucket `recipe` rows to every method with a matching output name (a skill
 * calc can legitimately have several same-named methods, e.g. Cooking's "Redberry pie" has 4 — all of them get
 * the recipe data, not an arbitrary one), and imports 0-xp recipes (e.g. unfinished potions) not already present
 * as `intermediate: true` methods so the route planner can chain crafts (ruling 15, grill 3). Assumes every
 * entry in `methods` shares one skill.
 */
export function mergeRecipes(methods: Method[], recipes: RecipeRow[]): Method[] {
  const skill = methods[0]?.skill;
  const merged = methods.map((m) => ({ ...m }));
  const indicesByName = new Map<string, number[]>();
  merged.forEach((m, i) => {
    const indices = indicesByName.get(m.name);
    if (indices) indices.push(i);
    else indicesByName.set(m.name, [i]);
  });

  for (const recipe of recipes) {
    const skillUse = recipe.skills.find((s) => s.name === skill);
    if (!skillUse) continue;

    const existingIndices = indicesByName.get(recipe.output.name);
    if (existingIndices) {
      for (const index of existingIndices) {
        merged[index] = { ...merged[index]!, boostable: isYes(skillUse.boostable), ticks: Number(recipe.ticks) };
      }
      continue;
    }

    if (Number(skillUse.experience) === 0) {
      const materials = recipe.materials.map((m) => ({ name: m.name, quantity: Number(m.quantity) }));
      const intermediate: Method = {
        skill: skill!,
        name: recipe.output.name,
        title: recipe.output.name,
        levelReq: Number(skillUse.level),
        xpPerAction: 0,
        materials,
        outputs: productOutputs(recipe.output.name, Number(recipe.output.quantity), materials),
        types: [],
        members: recipe.members,
        boostable: isYes(skillUse.boostable),
        ticks: Number(recipe.ticks),
        intermediate: true,
      };
      merged.push(intermediate);
      indicesByName.set(intermediate.name, [merged.length - 1]);
    }
  }

  return merged;
}
