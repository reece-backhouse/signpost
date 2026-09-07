import { describe, expect, it } from 'vitest';
import { parseLua, type LuaTable } from '../src/lua.js';
import { loadFixture } from './fixtures.js';

describe('parseLua', () => {
  it('parses questreq.lua without error', () => {
    const result = parseLua(loadFixture('questreq.lua')) as LuaTable;
    expect(typeof result).toBe('object');
    expect(result['Animal Magnetism']).toBeDefined();
  });

  it('parses calc_herb.lua without error', () => {
    const result = parseLua(loadFixture('calc_herb.lua')) as LuaTable;
    expect(typeof result).toBe('object');
    expect(result['1']).toBeDefined();
  });

  it('parses the Song of the Elves entry from questreq.lua', () => {
    const questreq = parseLua(loadFixture('questreq.lua')) as LuaTable;
    const sote = questreq['Song of the Elves'];
    expect(sote).toEqual({
      quests: { '1': 'Druidic Ritual', '2': 'Making History', '3': "Mourning's End Part II" },
      skills: {
        '1': { '1': 'Agility', '2': 70 },
        '2': { '1': 'Construction', '2': 70 },
        '3': { '1': 'Farming', '2': 70 },
        '4': { '1': 'Herblore', '2': 70 },
        '5': { '1': 'Hunter', '2': 70 },
        '6': { '1': 'Mining', '2': 70 },
        '7': { '1': 'Smithing', '2': 70 },
        '8': { '1': 'Woodcutting', '2': 70 },
      },
    });
  });

  it('parses a skill entry with a boostable positional string after the level number (Hard Kandarin Diary)', () => {
    const questreq = parseLua(loadFixture('questreq.lua')) as LuaTable;
    const diary = questreq['Hard Kandarin Diary'] as LuaTable;
    const skills = diary.skills as LuaTable;
    expect(skills['1']).toEqual({ '1': 'Agility', '2': 60, '3': 'boostable' });
  });

  it('parses the Prayer potion(3) entry from calc_herb.lua with level, xp, and materials', () => {
    const calc = parseLua(loadFixture('calc_herb.lua')) as LuaTable;
    const entries = Object.values(calc) as LuaTable[];
    const prayerPotion = entries.find((entry) => entry.name === 'Prayer potion(3)');
    expect(prayerPotion).toBeDefined();
    expect(prayerPotion!.level).toBe(38);
    expect(prayerPotion!.xp).toBe(87.5);
    expect(prayerPotion!.materials).toEqual({
      '1': { name: 'Ranarr potion (unf)', quantity: 1 },
      '2': { name: 'Snape grass', quantity: 1, goggles: true },
    });
  });

  it('handles an escaped quote inside a single-quoted string', () => {
    const result = parseLua(`return { name = 'Mourning\\'s End Part II' }`) as LuaTable;
    expect(result.name).toBe("Mourning's End Part II");
  });

  it('parses a decimal number', () => {
    const result = parseLua('return { xp = 87.5 }') as LuaTable;
    expect(result.xp).toBe(87.5);
  });

  it('ignores line and block comments', () => {
    const result = parseLua(
      '-- leading comment\nreturn {\n  --[[ block\ncomment ]]\n  a = 1, -- trailing\n}',
    ) as LuaTable;
    expect(result.a).toBe(1);
  });

  it('throws a descriptive error with line and column on malformed input', () => {
    expect(() => parseLua('return { a = }')).toThrow(/line \d+.*column \d+/is);
  });

  it('tolerates a leading local assignment followed by return', () => {
    const result = parseLua('local x = { a = 1 }\nreturn x') as LuaTable;
    expect(result.a).toBe(1);
  });
});
