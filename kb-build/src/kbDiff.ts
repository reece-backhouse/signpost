import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { KB_DIR } from './emit.js';
import { DIFF_COLLECTIONS, diffReport, type DiffKind } from './maintenance.js';

try {
  const root = join(import.meta.dirname, '..', '..');
  const tracked = new Set(execFileSync('git', ['ls-tree', '-r', '--name-only', 'HEAD', '--', 'src/main/resources/kb/'],
    { cwd: root, encoding: 'utf8' }).trim().split('\n'));
  for (const kind of Object.keys(DIFF_COLLECTIONS) as DiffKind[]) {
    const path = `src/main/resources/kb/${kind}.json`;
    const before = tracked.has(path) ? execFileSync('git', ['show', `HEAD:${path}`],
      { cwd: root, encoding: 'utf8', maxBuffer: 32 * 1024 * 1024 }) : undefined;
    const workingPath = join(KB_DIR, `${kind}.json`);
    const after = existsSync(workingPath) ? readFileSync(workingPath, 'utf8') : undefined;
    process.stdout.write(diffReport(kind, before, after));
  }
} catch (error) {
  console.error(`kb-diff: ${error instanceof Error ? error.message : String(error)}`);
  process.exitCode = 1;
}
