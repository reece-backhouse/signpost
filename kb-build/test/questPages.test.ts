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

  it('does not split on a nested template\'s own |name= line (depth-aware)', () => {
    const wikitext = [
      '{{Quest details',
      '|requirements = *{{SCP|Agility|70}}',
      '{{SomeTemplate',
      '|foo = bar',
      '}}',
      '|items = *[[Axe]]',
      '}}',
    ].join('\n');
    const params = templateParams(wikitext, 'Quest details');
    expect(params.requirements).toContain('{{SomeTemplate');
    expect(params.requirements).toContain('|foo = bar');
    expect(params.foo).toBeUndefined();
    expect(params.items).toBe('*[[Axe]]');
  });
});

describe('parseQuestPage', () => {
  const page = parseQuestPage(loadFixture('sote.txt'));

  it('parses quest points from Quest rewards', () => {
    expect(page.questPoints).toBe(4);
  });

  it('skips [[File:...]] image links in the items list (they are pictures, not items)', () => {
    const wikitext = ['{{Quest details', '|items = *[[File:Ultimate ironman chat badge.png]]', '*[[Pickaxe]]', '}}'].join('\n');
    expect(parseQuestPage(wikitext).items).toEqual([{ name: 'Pickaxe', quantity: 1 }]);
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

  it('throws instead of silently dropping a skill requirement with a non-numeric level', () => {
    const wikitext = '{{Quest details\n|requirements = *{{SCP|Agility|80+|link=yes}} {{Boostable|no}}\n}}';
    expect(() => parseQuestPage(wikitext)).toThrow(/Agility/);
  });
});
