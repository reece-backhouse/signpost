import { readFileSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { emitJson, preserveExtensions, writeKb } from '../src/emit.js';

describe('emitJson', () => {
  it('sorts keys recursively regardless of insertion order', () => {
    const a = emitJson({ b: 1, a: { d: 2, c: 3 } });
    const b = emitJson({ a: { c: 3, d: 2 }, b: 1 });
    expect(a).toBe(b);
    expect(a).toBe('{\n  "a": {\n    "c": 3,\n    "d": 2\n  },\n  "b": 1\n}\n');
  });

  it('normalizes an en dash in a nested material source name and typographic quotes', () => {
    const value = { sources: [{ where: 'Shop \u2013 \u201cRana\u2019s\u201d', detail: '\u2014\u2010\u2011\u2012\u2015\u2212' }] };
    expect(JSON.parse(emitJson(value))).toEqual({
      sources: [{ where: `Shop - "Rana's"`, detail: '------' }],
    });
    expect(value.sources[0]!.where).toBe('Shop \u2013 \u201cRana\u2019s\u201d');
  });
});

describe('writeKb', () => {
  const outPath = join(import.meta.dirname, '..', '..', 'src', 'main', 'resources', 'kb', 'test-fixture.json');

  it('replaces stale provenance while preserving curated extensions and normalizing source text', () => {
    const old = { title: 'Old page', revid: 1, timestamp: '2026-01-01T00:00:00Z' };
    const page = { title: 'Quest page', revid: 3, timestamp: '2026-01-03T00:00:00Z' };
    const module = { title: 'Module:Questreq/data', revid: 2, timestamp: '2026-01-02T00:00:00Z', content: 'not provenance' };
    try {
      writeKb('test-fixture', { quests: [{ id: 1, reason: 'Curated' }, { id: 2 }] }, old.timestamp, [old]);
      writeKb('test-fixture', { quests: [{ id: 1, sources: [{ where: 'Shop \u2013 east' }] }] },
        page.timestamp, [page, module, page]);
      const written = JSON.parse(readFileSync(outPath, 'utf8'));
      expect(written.sources).toEqual([
        { title: module.title, revid: module.revid, timestamp: module.timestamp }, page,
      ]);
      expect(written.quests).toEqual([{ id: 1, reason: 'Curated', sources: [{ where: 'Shop - east' }] }]);
    } finally {
      rmSync(outPath, { force: true });
    }
  });
});

describe('preserveExtensions', () => {
  it('retains unknown nested fields and replaces removed generated entries', () => {
    const old = { extension: true, quests: [
      { id: 1, rewards: { xp: 50 }, items: [{ name: 'Egg', quantity: 2, future: true }] },
      { id: 2, name: 'Removed' },
    ] };
    expect(preserveExtensions(old, { quests: [{ id: 1, items: [{ name: 'Egg', quantity: 3 }] }] })).toEqual({
      extension: true, quests: [{ id: 1, rewards: { xp: 50 }, items: [{ name: 'Egg', quantity: 3, future: true }] }],
    });
  });

  it('does not preserve obsolete optional generated method data', () => {
    expect(preserveExtensions({ xpPerAction: 5, boostable: true, ticks: 3, intermediate: true, extension: 7 },
      { xpPerAction: 9 })).toEqual({ xpPerAction: 9, extension: 7 });
  });
});
