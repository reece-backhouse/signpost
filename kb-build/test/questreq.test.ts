import { describe, expect, it } from 'vitest';
import { parseQuestreq } from '../src/questreq.js';
import { loadFixture } from './fixtures.js';

describe('parseQuestreq', () => {
  const questreq = parseQuestreq(loadFixture('questreq.lua'));

  it('drops the Name_of_quest template stub', () => {
    expect(questreq.has('Name_of_quest')).toBe(false);
  });

  it('parses Song of the Elves skills and prereqs', () => {
    const sote = questreq.get('Song of the Elves');
    expect(sote).toEqual({
      prereqs: ['Druidic Ritual', 'Making History', "Mourning's End Part II"],
      skills: [
        { skill: 'Agility', level: 70, boostable: false, ironmanOnly: false },
        { skill: 'Construction', level: 70, boostable: false, ironmanOnly: false },
        { skill: 'Farming', level: 70, boostable: false, ironmanOnly: false },
        { skill: 'Herblore', level: 70, boostable: false, ironmanOnly: false },
        { skill: 'Hunter', level: 70, boostable: false, ironmanOnly: false },
        { skill: 'Mining', level: 70, boostable: false, ironmanOnly: false },
        { skill: 'Smithing', level: 70, boostable: false, ironmanOnly: false },
        { skill: 'Woodcutting', level: 70, boostable: false, ironmanOnly: false },
      ],
    });
  });

  it('marks a boostable skill requirement (Between a Rock...)', () => {
    const entry = questreq.get('Between a Rock...');
    expect(entry?.skills).toEqual([
      { skill: 'Defence', level: 30, boostable: false, ironmanOnly: false },
      { skill: 'Mining', level: 40, boostable: true, ironmanOnly: false },
      { skill: 'Smithing', level: 50, boostable: true, ironmanOnly: false },
    ]);
  });

  it('marks an ironman-only skill requirement (Animal Magnetism)', () => {
    const entry = questreq.get('Animal Magnetism');
    expect(entry?.skills).toContainEqual({
      skill: 'Prayer',
      level: 31,
      boostable: false,
      ironmanOnly: true,
    });
    expect(entry?.prereqs).toEqual(['Ernest the Chicken', 'Priest in Peril', 'The Restless Ghost']);
  });
});
