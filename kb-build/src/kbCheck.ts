import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { KB_DIR } from './emit.js';
import { checkReport } from './maintenance.js';

try {
  const files = new Map(readdirSync(KB_DIR).filter((name) => name.endsWith('.json')).sort()
    .map((name) => [name, readFileSync(join(KB_DIR, name), 'utf8')]));
  process.stdout.write(await checkReport(files));
} catch (error) {
  console.error(`kb-check: ${error instanceof Error ? error.message : String(error)}`);
  process.exitCode = 1;
}
