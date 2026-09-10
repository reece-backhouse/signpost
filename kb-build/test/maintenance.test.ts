import { describe, expect, it, vi } from 'vitest';
import { checkReport, diffReport, parseSources } from '../src/maintenance.js';
import type { WikiSource } from '../src/wiki.js';

const pinned: WikiSource = { title: 'Module:Questreq/data', revid: 10, timestamp: '2026-01-01T00:00:00Z' };

describe('kb-check', () => {
  it('groups moved pages by generated file, checks shared sources once, and exposes unpinned data', async () => {
    const files = new Map([
      ['quests.json', JSON.stringify({ sources: [pinned] })],
      ['legacy.json', '{}'],
      ['diaries.json', JSON.stringify({ sources: [pinned] })],
      ['curated.json', '{"sources":[]}'],
    ]);
    const readOnlyCopy = [...files];
    const live = vi.fn(async (titles: string[]) => {
      expect(titles).toEqual([pinned.title]);
      return new Map([[pinned.title, { ...pinned, revid: 12, timestamp: '2026-01-03T00:00:00Z' }]]);
    });
    const report = await checkReport(files, live);
    expect(report).toContain('diaries.json:\n  MOVED Module:Questreq/data: 10 (2026-01-01T00:00:00Z) -> 12 (2026-01-03T00:00:00Z)');
    expect(report).toContain('quests.json:\n  MOVED Module:Questreq/data:');
    expect(report).toContain('legacy.json:\n  UNPINNED:');
    expect(report).toContain('curated.json:\n  No revision-addressed page sources');
    expect([...files]).toEqual(readOnlyCopy);
  });

  it('distinguishes a deleted source from an unchanged one', async () => {
    const gone = { ...pinned, title: 'Deleted' };
    const report = await checkReport(new Map([['quests.json', JSON.stringify({ sources: [pinned, gone] })]]),
      async () => new Map<string, WikiSource | null>([[pinned.title, pinned], [gone.title, null]]));
    expect(report).toContain('MISSING Deleted: recorded 10');
    expect(report).not.toContain('MOVED Module:Questreq/data');
    expect(report).not.toContain('Unchanged');
  });

  it('never reports omitted live metadata as unchanged', async () => {
    await expect(checkReport(new Map([['quests.json', JSON.stringify({ sources: [pinned] })]]),
      async () => new Map())).rejects.toThrow(/omitted Module:Questreq\/data/);
  });

  it('reports unchanged only when every recorded revision matches', async () => {
    const report = await checkReport(new Map([['quests.json', JSON.stringify({ sources: [pinned] })]]),
      async () => new Map([[pinned.title, pinned]]));
    expect(report).toContain('Unchanged (1 recorded revisions).');
    expect(report).not.toContain('MOVED');
  });

  it('rejects fabricated or incomplete metadata naming the generated file', () => {
    expect(() => parseSources(JSON.stringify({ sources: [{ title: 'A', timestamp: pinned.timestamp }] }), 'quests.json'))
      .toThrow(/quests.json.sources\[0\]/);
    expect(() => parseSources('{"sources":{}}', 'diaries.json')).toThrow(/diaries.json.sources/);
  });
});

describe('kb-diff', () => {
  it('reports quest additions/removals and skill/prerequisite/item changes while ignoring timestamps and reward prose', () => {
    const before = { generatedAt: 'old', quests: [
      { id: 1, skills: [{ skill: 'Magic', level: 40 }], prereqs: ['Old'], items: [{ name: 'Egg', quantity: 1 }] },
      { id: 2, name: 'Removed' },
    ] };
    const after = { generatedAt: 'new', quests: [
      { id: 3, name: 'Added' },
      { id: 1, skills: [{ skill: 'Magic', level: 50 }], prereqs: ['New'], items: [{ name: 'Egg', quantity: 2 }], rewards: { xp: 500 } },
    ] };
    const report = diffReport('quests', JSON.stringify(before), JSON.stringify(after));
    expect(report).toContain('ADDED quest:3 (Added)');
    expect(report).toContain('REMOVED quest:2 (Removed)');
    expect(report).toContain('CHANGED quest:1');
    expect(report).toContain('- skills[0].level: 40');
    expect(report).toContain('+ skills[0].level: 50');
    expect(report).toContain('+ prereqs[0]: "New"');
    expect(report).toContain('+ items[0].quantity: 2');
    expect(report).not.toContain('generatedAt');
    expect(report).not.toContain('rewards');
  });

  it('addresses diary requirements by area, tier and task ordinal rather than task order', () => {
    const before = { diaries: [{ area: 'VARROCK', tier: 'EASY', tasks: [
      { ordinal: 2, skills: [{ skill: 'Mining', level: 15 }] }, { ordinal: 1, skills: [] },
    ] }] };
    const after = { diaries: [{ area: 'VARROCK', tier: 'EASY', tasks: [
      { ordinal: 1, skills: [] }, { ordinal: 2, skills: [{ skill: 'Mining', level: 20 }] },
    ] }] };
    const report = diffReport('diaries', JSON.stringify(before), JSON.stringify(after));
    expect(report).toContain('CHANGED diary:VARROCK_EASY');
    expect(report).toContain('+ tasks[2].skills[0].level: 20');
    expect(report).not.toContain('tasks[1]');
  });

  it('includes milestone recommendations and nested gathering alternative and step gates', () => {
    expect(diffReport('milestones', JSON.stringify({ milestones: [{ id: 'boss:test', requirements: {}, recommended: { gear: [] } }] }),
      JSON.stringify({ milestones: [{ id: 'boss:test', requirements: {}, recommended: { gear: [{ role: 'melee-weapon', alternatives: [{ id: 4151 }] }] } }] })))
      .toContain('recommended.gear');
    const plan = { id: 1, requires: { items: [] }, steps: [], alternatives: [{ title: 'Other', requires: { quests: [] }, steps: [] }] };
    const changed = { ...plan, risk: 'wilderness', alternatives: [{ title: 'Other', requires: { quests: ['Quest'] },
      steps: [{ text: 'Teleport', requires: { skills: [{ skill: 'Magic', level: 37 }] } }] }] };
    const report = diffReport('gathering', JSON.stringify({ plans: [plan] }), JSON.stringify({ plans: [changed] }));
    expect(report).toContain('CHANGED gathering:1');
    expect(report).toContain('+ alternatives[0].requires.quests[0]: "Quest"');
    expect(report).toContain('+ alternatives[0].steps[0].requires.skills[0].level: 37');
    expect(report).toContain('+ risk: "wilderness"');
  });

  it('reports removed gates instead of hiding them when optional fields disappear', () => {
    const before = { plans: [{ id: 7, requires: { quests: ['Quest'] },
      steps: [{ text: 'Teleport', requires: { items: [{ name: 'Law rune', quantity: 1 }] } }] }] };
    const after = { plans: [{ id: 7, requires: {},
      steps: [{ text: 'Walk' }] }] };
    const report = diffReport('gathering', JSON.stringify(before), JSON.stringify(after));
    expect(report).toContain('- requires.quests[0]: "Quest"');
    expect(report).toContain('- steps[0].requires.items[0].name: "Law rune"');
    expect(report).not.toContain('+ steps[0].requires');
    expect(report).not.toContain('Teleport');
  });

  it('reports whole-file additions and deletions and rejects duplicate identifiers', () => {
    const json = JSON.stringify({ plans: [{ id: 4, title: 'Plan', requires: {} }] });
    expect(diffReport('gathering', undefined, json)).toContain('ADDED gathering:4');
    expect(diffReport('gathering', json, undefined)).toContain('REMOVED gathering:4');
    expect(() => diffReport('quests', undefined, '{"quests":[{"id":1},{"id":1}]}')).toThrow(/duplicate entry quest:1/);
  });

  it('ignores entry and object-key order and non-requirement text edits', () => {
    expect(diffReport('milestones', '{"milestones":[{"id":"a","reason":"Old","requirements":{"skills":[],"items":[]}}]}',
      '{"milestones":[{"requirements":{"items":[],"skills":[]},"reason":"New","id":"a"}]}'))
      .toContain('No entry or requirement changes.');
  });
});
