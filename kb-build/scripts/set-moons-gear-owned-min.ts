// One-off: lower boss:moons-of-peril's recommended.gearOwnedMin from 2 to 1 per Task 53. Live
// diagnostics showed one gearOwnedAny piece (Abyssal whip) with the account's only other piece
// (a group-ironman shared-loot Masori mask, an unrelated stage-4 gear milestone) left Moons stuck
// "not ready" at 1 of 2, while God Wars Dungeon (also ready-gated) outranked it on priority alone.
// Uses the same emitJson path as `expand-milestones` so key order stays byte-identical everywhere
// else in the file. Run once with `npx tsx scripts/set-moons-gear-owned-min.ts`.
import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { emitJson } from '../src/emit.js';

const path = join(import.meta.dirname, '..', '..', 'plugin', 'src', 'main', 'resources', 'kb', 'milestones.json');
const raw = JSON.parse(readFileSync(path, 'utf8')) as { version: number; milestones: any[] };

const id = 'boss:moons-of-peril';
const milestone = raw.milestones.find((m) => m.id === id);
if (!milestone) {
  throw new Error(`${id} not found`);
}
if (milestone.recommended === null) {
  throw new Error(`${id} has no recommended profile to set gearOwnedMin on`);
}
if (milestone.recommended.gearOwnedMin !== 2) {
  throw new Error(`expected ${id}.recommended.gearOwnedMin to be 2, was ${milestone.recommended.gearOwnedMin}`);
}
milestone.recommended.gearOwnedMin = 1;

writeFileSync(path, emitJson({ version: raw.version, milestones: raw.milestones }));
console.log(`Set ${id}.recommended.gearOwnedMin to 1.`);
