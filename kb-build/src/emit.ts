import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

/** Deep-clones `value` with object keys sorted recursively, so JSON.stringify output is stable regardless of insertion order. */
function sortKeys(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(sortKeys);
  }
  if (value !== null && typeof value === 'object') {
    const sorted: Record<string, unknown> = {};
    for (const key of Object.keys(value as Record<string, unknown>).sort()) {
      sorted[key] = sortKeys((value as Record<string, unknown>)[key]);
    }
    return sorted;
  }
  return value;
}

/** Serializes `obj` to JSON with recursively sorted keys, 2-space indent, and a trailing newline. */
export function emitJson(obj: unknown): string {
  return JSON.stringify(sortKeys(obj), null, 2) + '\n';
}

const KB_DIR = join(import.meta.dirname, '..', '..', 'plugin', 'src', 'main', 'resources', 'kb');

/** Writes `{ version: 1, generatedAt, ...payload }` to plugin/src/main/resources/kb/<name>.json. */
export function writeKb(name: string, payload: unknown, generatedAt: string): void {
  const outPath = join(KB_DIR, `${name}.json`);
  mkdirSync(dirname(outPath), { recursive: true });
  writeFileSync(outPath, emitJson({ version: 1, generatedAt, ...(payload as Record<string, unknown>) }));
}
