import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { buildQuests } from '../src/quests.js';
import { parseQuestreq } from '../src/questreq.js';
import { loadFixture } from './fixtures.js';

const dataDir = join(import.meta.dirname, '..', 'data');
const runeliteQuests: { id: number; name: string }[] = JSON.parse(
  readFileSync(join(dataDir, 'runelite-quests.json'), 'utf8'),
);
const aliases: Record<string, string> = JSON.parse(readFileSync(join(dataDir, 'aliases.json'), 'utf8'));

function pagesFromWikitext(entries: Record<string, string>): Map<string, { content: string; timestamp: string }> {
  return new Map(Object.entries(entries).map(([title, content]) => [title, { content, timestamp: '2026-01-01T00:00:00Z' }]));
}

describe('buildQuests', () => {
  it('resolves every RuneLite quest, with unique ids, using the real fixtures', () => {
    const questreq = parseQuestreq(loadFixture('questreq.lua'));
    const soteWikitext = loadFixture('sote.txt');
    const pages = pagesFromWikitext({ 'Song of the Elves': soteWikitext });

    const result = buildQuests({ runeliteQuests, aliases, questreq, pages });

    expect(result).toHaveLength(runeliteQuests.length);
    expect(new Set(result.map((q) => q.id)).size).toBe(runeliteQuests.length);
  });

  it('resolves Song of the Elves via questreq, with items/qp from the page', () => {
    const questreq = parseQuestreq(loadFixture('questreq.lua'));
    const soteWikitext = loadFixture('sote.txt');
    const pages = pagesFromWikitext({ 'Song of the Elves': soteWikitext });

    const result = buildQuests({ runeliteQuests, aliases, questreq, pages });
    const sote = result.find((q) => q.name === 'Song of the Elves');

    expect(sote?.source).toBe('questreq');
    expect(sote?.skills).toHaveLength(8);
    expect(sote?.questPoints).toBe(4);
    expect(sote?.items.length).toBeGreaterThan(0);
  });

  it('resolves an RFD alias via questreq', () => {
    const questreq = parseQuestreq(loadFixture('questreq.lua'));
    const pages = pagesFromWikitext({});

    const result = buildQuests({ runeliteQuests, aliases, questreq, pages });
    const pirate = result.find((q) => q.name === 'Recipe for Disaster - Pirate Pete');

    expect(pirate?.source).toBe('questreq');
    expect(pirate?.wikiTitle).toBe('Recipe for Disaster/Freeing Pirate Pete');
  });

  it('falls back to page requirements when a quest is absent from questreq', () => {
    const questreq = new Map();
    const pages = pagesFromWikitext({
      'Page Quest': '{{Quest details\n|requirements = *{{SCP|Woodcutting|10|link=yes}} {{Boostable|no}}\n|items = *[[Axe]]\n}}\n{{Quest rewards\n|qp = 1\n}}',
    });

    const result = buildQuests({
      runeliteQuests: [{ id: 999, name: 'Page Quest' }],
      aliases: {},
      questreq,
      pages,
    });

    expect(result[0]?.source).toBe('page');
    expect(result[0]?.skills).toEqual([{ skill: 'Woodcutting', level: 10, boostable: false, ironmanOnly: false }]);
    expect(result[0]?.items).toEqual([{ name: 'Axe', quantity: 1 }]);
  });

  it('leaves a miniquest with no questreq entry and no page requirements as source "none" with empty arrays', () => {
    const questreq = new Map();
    const pages = pagesFromWikitext({ 'Mini Quest': '{{Quest details\n|items = {{NA|None}}\n}}' });

    const result = buildQuests({
      runeliteQuests: [{ id: 1000, name: 'Mini Quest' }],
      aliases: {},
      questreq,
      pages,
    });

    expect(result[0]).toEqual({
      id: 1000,
      name: 'Mini Quest',
      wikiTitle: 'Mini Quest',
      skills: [],
      prereqs: [],
      prereqsStarted: [],
      prereqNotes: [],
      items: [],
      questPoints: null,
      source: 'none',
    });
  });

  it('splits a "Started:" prefixed prereq into prereqsStarted, not prereqs', () => {
    const questreq = new Map([
      ['Prereq Quest', { skills: [], prereqs: [] }],
      ['Main Quest', { skills: [], prereqs: ['Started:Prereq Quest'] }],
    ]);

    const result = buildQuests({
      runeliteQuests: [
        { id: 1, name: 'Prereq Quest' },
        { id: 2, name: 'Main Quest' },
      ],
      aliases: {},
      questreq,
      pages: new Map(),
    });

    const main = result.find((q) => q.name === 'Main Quest');
    expect(main?.prereqs).toEqual([]);
    expect(main?.prereqsStarted).toEqual(['Prereq Quest']);
    expect(main?.prereqNotes).toEqual([]);
  });

  it('moves a prereq name that is not a RuneLite quest to prereqNotes instead of dropping it', () => {
    const questreq = new Map([['Main Quest', { skills: [], prereqs: ['Not A Real Quest', 'Started:Also Not Real'] }]]);

    const result = buildQuests({
      runeliteQuests: [{ id: 2, name: 'Main Quest' }],
      aliases: {},
      questreq,
      pages: new Map(),
    });

    const main = result.find((q) => q.name === 'Main Quest');
    expect(main?.prereqs).toEqual([]);
    expect(main?.prereqsStarted).toEqual([]);
    expect(main?.prereqNotes).toEqual(['Not A Real Quest', 'Started:Also Not Real']);
  });

  it('sorts entries by id', () => {
    const result = buildQuests({
      runeliteQuests: [
        { id: 5, name: 'B' },
        { id: 1, name: 'A' },
      ],
      aliases: {},
      questreq: new Map(),
      pages: new Map(),
    });
    expect(result.map((q) => q.id)).toEqual([1, 5]);
  });
});
