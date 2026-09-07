import { parseLua, type LuaTable } from './lua.js';

export interface SkillReq {
  skill: string;
  level: number;
  boostable: boolean;
  ironmanOnly: boolean;
}

export interface QuestReq {
  skills: SkillReq[];
  prereqs: string[];
}

/** Extracts the positional (numeric-keyed) values of a Lua table in order, e.g. {'1': a, '2': b} -> [a, b]. */
function positional(table: LuaTable): unknown[] {
  const values: unknown[] = [];
  for (let i = 1; table[String(i)] !== undefined; i++) {
    values.push(table[String(i)]);
  }
  return values;
}

/** Parses `Module:Questreq/data` Lua into a map of wiki quest title -> skill/prerequisite requirements. */
export function parseQuestreq(lua: string): Map<string, QuestReq> {
  const root = parseLua(lua) as LuaTable;
  const result = new Map<string, QuestReq>();

  for (const [name, value] of Object.entries(root)) {
    if (name === 'Name_of_quest') continue;

    const entry = value as LuaTable;
    const prereqs = positional(entry.quests as LuaTable) as string[];
    const skills = positional(entry.skills as LuaTable).map((skillEntry) => {
      const parts = positional(skillEntry as LuaTable);
      const flags = parts.slice(2) as string[];
      return {
        skill: parts[0] as string,
        level: parts[1] as number,
        boostable: flags.includes('boostable'),
        ironmanOnly: flags.includes('ironman'),
      };
    });

    result.set(name, { skills, prereqs });
  }

  return result;
}
