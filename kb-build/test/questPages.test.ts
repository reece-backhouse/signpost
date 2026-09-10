import { describe, expect, it } from 'vitest';
import { parseQuestPage, SKILLS, templateParams } from '../src/questPages.js';
import { loadFixture } from './fixtures.js';

describe('templateParams', () => {
  it('extracts params from a template, spanning multiple lines and nested templates', () => {
    const params = templateParams('{{Foo\n|a = 1\n|b = *{{Bar|x}}\nmore\n}}', 'Foo');
    expect(params.a).toBe('1');
    expect(params.b).toBe('*{{Bar|x}}\nmore');
  });

  it('splits several |key= params on one line, ignoring pipes inside links and nested templates', () => {
    const line = '{{Quest rewards\n|name=Desert Treasure|image=[[File:DT.png|centre]]|qp=3|rewards=*{{SCP|Magic|20,006.9}} [[Magic]] [[experience]]{{CiteTwitter|author=Mod Ash|url=https://x|quote=20,006.9, in fact!}}\n*Second line\n}}';
    const params = templateParams(line, 'Quest rewards');
    expect(params.name).toBe('Desert Treasure');
    expect(params.image).toBe('[[File:DT.png|centre]]');
    expect(params.qp).toBe('3');
    expect(params.rewards).toBe('*{{SCP|Magic|20,006.9}} [[Magic]] [[experience]]{{CiteTwitter|author=Mod Ash|url=https://x|quote=20,006.9, in fact!}}\n*Second line');
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

describe('parseQuestPage rewards', () => {
  it('reads fixed skill xp from the Song of the Elves page and no lamps', () => {
    const page = parseQuestPage(loadFixture('sote.txt'));
    expect(page.rewards.xp).toEqual({
      Agility: 40000,
      Construction: 40000,
      Farming: 40000,
      Herblore: 40000,
      Hunter: 40000,
      Mining: 40000,
      Smithing: 40000,
      Woodcutting: 40000,
    });
    expect(page.rewards.lamps).toEqual([]);
  });

  it('parses fixed xp, choice lamps and lamp counts conservatively from real reward lines', () => {
    const page = parseQuestPage(loadFixture('rewards.txt'));

    // Fixed xp: commas and decimals handled (floored), a trailing citation tolerated, an
    // unknown skill (Sailing) dropped; "total of" magic lamps count as fixed xp per skill.
    // A trailing remark, a lowercase or unlinked skill word, and two rewards on one line still count;
    // a conditional "(... if you ...)" remark does not (Sheep Shearer's 150 is left out).
    expect(page.rewards.xp).toEqual({
      Strength: 13750 + 30000 + 2500,
      Attack: 13750 + 2500,
      Magic: 20006 + 5000,
      Cooking: 300,
      Defence: 33000,
      Slayer: 20000,
      Thieving: 5000 + 1500,
      Crafting: 3875,
      Fishing: 5000,
    });

    const all = SKILLS;
    const combat6 = ['Magic', 'Ranged', 'Strength', 'Attack', 'Defence', 'Hitpoints'];
    const combat7 = ['Attack', 'Strength', 'Defence', 'Hitpoints', 'Ranged', 'Magic', 'Prayer'];
    expect(page.rewards.lamps).toEqual([
      // RFD: one lamp, any skill above 50
      { xp: 20000, skills: all, minLevel: 50 },
      // DS2 Ellen: 4x 25,000 in six combat skills
      ...Array(4).fill({ xp: 25000, skills: combat6, minLevel: 0 }),
      // DT2: three lamps, seven skills, level 60+
      ...Array(3).fill({ xp: 100000, skills: combat7, minLevel: 60 }),
      // Dreamy lamp ("apart from"), MM1 ("either ... OR"), Fremennik Isles ("Combat", no lamp): omitted
      // Sins of the Father tome: six uses, any skill at 60+
      ...Array(6).fill({ xp: 15000, skills: all, minLevel: 60 }),
      // Defender of Varrock: "5 Kudos" is not an xp amount
      { xp: 5000, skills: all, minLevel: 30 },
      // Legends' Quest: four skills of your choice from the listed set
      ...Array(4).fill({ xp: 30000, skills: ['Attack', 'Defence', 'Herblore'], minLevel: 0 }),
      // A Tail of Two Cats: two lamps, any skill over 30
      ...Array(2).fill({ xp: 2500, skills: all, minLevel: 30 }),
      // A Taste of Hope: a tome used three times, any skill at 35+
      ...Array(3).fill({ xp: 2500, skills: all, minLevel: 35 }),
      // The Tourist Trap: your choice of two skills from a listed set
      ...Array(2).fill({ xp: 4650, skills: ['Agility', 'Fletching', 'Smithing', 'Thieving'], minLevel: 0 }),
      // Shadow of the Storm ("other than Prayer", no lamp): omitted
      // "only receive one lamp" note (no amount) and Duradel's notes (no amount): omitted
    ]);
  });

  it('parses the Into the Tombs plain Rewards section as a level-60 combat lamp excluding Prayer', () => {
    // https://oldschool.runescape.wiki/w/Into_the_Tombs?action=raw
    const page = parseQuestPage(`==Rewards==
* An [[Antique lamp (Into the Tombs)|antique lamp]] giving 50,000 [[experience]] to any combat skill that is at least level 60, excluding [[Prayer]].
* Ability to obtain the [[Ancient key (Tombs of Amascut)|ancient key]].
* Access to further runs of [[Tombs of Amascut]]

==Transcript==
*An antique lamp granting 1,000 experience in any skill
`);
    expect(page.rewards).toEqual({
      xp: {},
      lamps: [{ xp: 50000, skills: ['Attack', 'Strength', 'Defence', 'Hitpoints', 'Ranged', 'Magic'], minLevel: 60 }],
    });
  });

  it('maps unrestricted combat-category lamps to combat skills only', () => {
    const page = parseQuestPage(`{{Quest rewards
|rewards = *An experience lamp granting 4,000 experience in any combat skill of your choice
}}`);
    expect(page.rewards.lamps).toEqual([
      { xp: 4000, skills: ['Attack', 'Strength', 'Defence', 'Hitpoints', 'Ranged', 'Magic', 'Prayer'], minLevel: 0 },
    ]);
  });

  it('keeps numeral lamp counts but rejects ambiguous categories, conditional rewards and choice totals', () => {
    const page = parseQuestPage(`{{Quest rewards
|rewards =
*3 antique lamps each granting 1,000 experience in any skill at level 20 or higher
*An experience lamp granting 4,000 experience in any artisan skill of your choice
*An experience lamp granting 5,000 experience in {{SCP|Sailing}} or {{SCP|Attack}}
*An experience lamp granting 2,000 experience in any skill at a minimum level of thirty
*If you qualify, an experience lamp granting 6,000 experience in any skill
*Two lamps offering a total of {{SCP|Attack|7,000}} XP in a skill of your choice
*Up to {{SCP|Magic|8,000}} experience depending on your level
}}`);
    expect(page.rewards).toEqual({
      xp: {},
      lamps: Array.from({ length: 3 }, () => ({ xp: 1000, skills: SKILLS, minLevel: 20 })),
    });
  });

  it('has empty rewards when the page has no Quest rewards template', () => {
    expect(parseQuestPage('nothing here').rewards).toEqual({ xp: {}, lamps: [] });
  });
});
