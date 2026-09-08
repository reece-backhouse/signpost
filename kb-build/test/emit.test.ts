import { readFileSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { emitJson, writeKb } from '../src/emit.js';

describe('emitJson', () => {
  it('sorts keys recursively regardless of insertion order', () => {
    const a = emitJson({ b: 1, a: { d: 2, c: 3 } });
    const b = emitJson({ a: { c: 3, d: 2 }, b: 1 });
    expect(a).toBe(b);
    expect(a).toBe('{\n  "a": {\n    "c": 3,\n    "d": 2\n  },\n  "b": 1\n}\n');
  });

  it('is byte-identical across two emits of the same object', () => {
    const obj = { quests: [{ id: 2, name: 'B' }, { id: 1, name: 'A' }] };
    expect(emitJson(obj)).toBe(emitJson(obj));
  });
});

describe('writeKb', () => {
  const outPath = join(import.meta.dirname, '..', '..', 'plugin', 'src', 'main', 'resources', 'kb', 'test-fixture.json');

  it('writes version, generatedAt, and payload to src/main/resources/kb/<name>.json', () => {
    writeKb('test-fixture', { quests: [] }, '2026-01-01T00:00:00Z');
    const written = JSON.parse(readFileSync(outPath, 'utf8'));
    expect(written).toEqual({ version: 1, generatedAt: '2026-01-01T00:00:00Z', quests: [] });
    rmSync(outPath);
  });
});
