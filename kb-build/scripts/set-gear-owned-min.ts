// One-off: set explicit recommended.gearOwnedMin for boss:moons-of-peril (2) and
// boss:god-wars-dungeon (4, matching the previous implicit ceil(7/2) default) per Task 50.
// Uses the same emitJson path as `expand-milestones` so key order stays byte-identical
// everywhere else in the file. Run once with `npx tsx scripts/set-gear-owned-min.ts`.
import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { emitJson } from '../src/emit.js';

const path = join(import.meta.dirname, '..', '..', 'plugin', 'src', 'main', 'resources', 'kb', 'milestones.json');
const raw = JSON.parse(readFileSync(path, 'utf8')) as { version: number; milestones: any[] };

const targets: Record<string, number> = {
  'boss:moons-of-peril': 2,
  'boss:god-wars-dungeon': 4,
};

let updated = 0;
for (const milestone of raw.milestones) {
  const min = targets[milestone.id];
  if (min !== undefined) {
    if (milestone.recommended === null) {
      throw new Error(`${milestone.id} has no recommended profile to set gearOwnedMin on`);
    }
    milestone.recommended.gearOwnedMin = min;
    updated++;
  }
}

if (updated !== Object.keys(targets).length) {
  throw new Error(`expected to update ${Object.keys(targets).length} milestones, updated ${updated}`);
}

writeFileSync(path, emitJson({ version: raw.version, milestones: raw.milestones }));
console.log(`Set gearOwnedMin on ${updated} milestones.`);
