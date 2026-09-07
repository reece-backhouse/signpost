import { describe, expect, it } from 'vitest';
import { parseQuestPage, templateParams } from '../src/questPages.js';
import { loadFixture } from './fixtures.js';

describe('templateParams', () => {
  it('extracts params from a template, spanning multiple lines and nested templates', () => {
    const params = templateParams('{{Foo\n|a = 1\n|b = *{{Bar|x}}\nmore\n}}', 'Foo');
    expect(params.a).toBe('1');
    expect(params.b).toBe('*{{Bar|x}}\nmore');
  });

  it('returns an empty object when the template is absent', () => {
    expect(templateParams('no templates here', 'Foo')).toEqual({});
  });
});

describe('parseQuestPage', () => {
  const page = parseQuestPage(loadFixture('sote.txt'));

  it('parses quest points from Quest rewards', () => {
    expect(page.questPoints).toBe(4);
  });

  it('parses items, including a plain link and an "x N" quantity', () => {
    expect(page.items).toContainEqual({ name: 'Steel full helm', quantity: 1 });
    expect(page.items).toContainEqual({ name: 'Limestone brick', quantity: 8 });
    expect(page.items).toHaveLength(26);
  });

  it('parses the requirements fallback: 8 non-boostable skills and direct (**) prereqs only', () => {
    expect(page.requirements).not.toBeNull();
    expect(page.requirements!.skills).toEqual([
      { skill: 'Agility', level: 70, boostable: false, ironmanOnly: false },
      { skill: 'Construction', level: 70, boostable: false, ironmanOnly: false },
      { skill: 'Farming', level: 70, boostable: false, ironmanOnly: false },
      { skill: 'Herblore', level: 70, boostable: false, ironmanOnly: false },
      { skill: 'Hunter', level: 70, boostable: false, ironmanOnly: false },
      { skill: 'Mining', level: 70, boostable: false, ironmanOnly: false },
      { skill: 'Smithing', level: 70, boostable: false, ironmanOnly: false },
      { skill: 'Woodcutting', level: 70, boostable: false, ironmanOnly: false },
    ]);
    expect(page.requirements!.prereqs).toEqual(["Mourning's End Part II", 'Making History', 'Druidic Ritual']);
  });
});
