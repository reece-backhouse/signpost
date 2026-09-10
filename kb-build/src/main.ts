import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { buildDiaries, DIARY_PAGE_TITLES, type DiaryEntry, type DiaryVarsFile } from './diaries.js';
import { emitJson, writeKb } from './emit.js';
import { expandMilestoneItemIds, type ItemIdRow as ExpandItemIdRow, type MilestoneLike } from './expandItems.js';
import { addMissingGatheringMaterials, gatheringMaterialNames, MISSING_GATHERING_MATERIAL_IDS, resolveGatheringPlans, type GatheringPlanDraft } from './gathering.js';
import { addMissingMaterials, buildMaterials, collectReferencedItems, resolveItemIds, type DropslineRow, type ItemIdRow, type LoclineRow, type Material, type StorelineRow } from './materials.js';
import { mergeRecipes, parseRecipeRow, parseSkillCalc, type Method, type RecipeRow } from './methods.js';
import { buildQuests, type QuestEntry } from './quests.js';
import { parseQuestreq } from './questreq.js';
import { bucket, fetchPriceMapping, fetchRevisions, sourceManifest, type WikiSource } from './wiki.js';

const dataDir = join(import.meta.dirname, '..', 'data');
const kbDir = join(import.meta.dirname, '..', '..', 'src', 'main', 'resources', 'kb');

// the nine planned skills with a `Module:Skill calc/<Skill>` wiki module.
const METHOD_SKILLS = [
  'Herblore',
  'Prayer',
  'Crafting',
  'Smithing',
  'Cooking',
  'Fletching',
  'Construction',
  'Magic',
  'Firemaking',
];

async function buildQuestsCommand(): Promise<void> {
  const runeliteQuests: { id: number; name: string }[] = JSON.parse(
    readFileSync(join(dataDir, 'runelite-quests.json'), 'utf8'),
  );
  const aliases: Record<string, string> = JSON.parse(readFileSync(join(dataDir, 'aliases.json'), 'utf8'));

  const questreqRevisions = await fetchRevisions(['Module:Questreq/data']);
  const questreqPage = questreqRevisions.get('Module:Questreq/data');
  if (!questreqPage) {
    throw new Error('Module:Questreq/data missing from wiki response');
  }
  const questreq = parseQuestreq(questreqPage.content);

  const wikiTitles = [...new Set(runeliteQuests.map((q) => aliases[q.name] ?? q.name))];
  const pages = await fetchRevisions(wikiTitles);

  const quests = buildQuests({ runeliteQuests, aliases, questreq, pages });

  // ISO 8601 revision timestamps (fixed-width, UTC) sort lexicographically in chronological order.
  const timestamps = [questreqPage.timestamp, ...[...pages.values()].map((p) => p.timestamp)].sort();
  const generatedAt = timestamps.at(-1)!;

  writeKb('quests', { quests }, generatedAt, sourceManifest(questreqRevisions.values(), pages.values()));

  logSummary(quests);
}

function logSummary(quests: QuestEntry[]): void {
  const counts = new Map<string, number>();
  for (const quest of quests) {
    counts.set(quest.source, (counts.get(quest.source) ?? 0) + 1);
  }

  console.log(`Built ${quests.length} quests:`);
  for (const [source, count] of counts) {
    console.log(`  ${source}: ${count}`);
  }

  const none = quests.filter((quest) => quest.source === 'none');
  if (none.length > 0) {
    console.log(`Quests with source "none" (${none.length}): ${none.map((quest) => quest.name).join(', ')}`);
  }
}

async function buildDiariesCommand(): Promise<void> {
  const vars: DiaryVarsFile = JSON.parse(readFileSync(join(dataDir, 'diary-vars.json'), 'utf8'));

  const titles = Object.values(DIARY_PAGE_TITLES);
  const pages = await fetchRevisions(titles);

  const diaries = buildDiaries({ pages, vars });

  const generatedAt = [...pages.values()].map((p) => p.timestamp).sort().at(-1)!;

  writeKb('diaries', { diaries }, generatedAt, pages.values());

  logDiarySummary(diaries);
}

function logDiarySummary(diaries: DiaryEntry[]): void {
  console.log(`Built ${diaries.length} diary tiers:`);
  for (const entry of diaries) {
    console.log(`  ${entry.area} ${entry.tier}: ${entry.tasks.length} tasks`);
  }
}

async function buildMethodsCommand(): Promise<void> {
  const calcTitles = METHOD_SKILLS.map((skill) => `Module:Skill calc/${skill}`);
  const pages = await fetchRevisions(calcTitles);

  const allMethods: Method[] = [];
  for (const skill of METHOD_SKILLS) {
    const title = `Module:Skill calc/${skill}`;
    const page = pages.get(title);
    if (!page) {
      throw new Error(`Wiki page missing from response: ${title}`);
    }

    const baseMethods = parseSkillCalc(page.content, skill);

    const rawRecipes = await bucket<{ page_name: string; production_json: string }>(
      `bucket('recipe').select('page_name','production_json','uses_skill').where('uses_skill','${skill}')`,
    );
    const recipes = rawRecipes
      .map((row) => parseRecipeRow(row.production_json))
      .filter((row): row is RecipeRow => row !== null);

    allMethods.push(...mergeRecipes(baseMethods, recipes));
  }

  allMethods.sort((a, b) => a.skill.localeCompare(b.skill) || a.levelReq - b.levelReq || a.name.localeCompare(b.name));

  // generatedAt is the max wiki revision timestamp fetched.
  const generatedAt = [...pages.values()].map((p) => p.timestamp).sort().at(-1)!;

  writeKb('methods', { methods: allMethods }, generatedAt, pages.values());
  logMethodsSummary(allMethods);
}

function logMethodsSummary(methods: Method[]): void {
  const bySkill = new Map<string, { total: number; intermediate: number }>();
  for (const method of methods) {
    const entry = bySkill.get(method.skill) ?? { total: 0, intermediate: 0 };
    entry.total++;
    if (method.intermediate) entry.intermediate++;
    bySkill.set(method.skill, entry);
  }

  console.log(`Built ${methods.length} methods:`);
  for (const [skill, { total, intermediate }] of bySkill) {
    console.log(`  ${skill}: ${total} (${intermediate} intermediate)`);
  }
}

/** Reads src/main/resources/kb/<name>.json, already-built by this pipeline. */
function readKb<T>(name: string): T & { generatedAt: string; sources?: WikiSource[] } {
  const path = join(kbDir, `${name}.json`);
  if (!existsSync(path)) {
    throw new Error(`${path} does not exist; run 'npm run build-kb -- ${name}' first`);
  }
  return JSON.parse(readFileSync(path, 'utf8')) as T & { generatedAt: string; sources?: WikiSource[] };
}

interface MilestoneEntry {
  requirements: { items: { name: string }[] };
  ownedIf: { name: string }[];
}

async function buildMaterialsCommand(): Promise<void> {
  const methodsFile = readKb<{ methods: Method[] }>('methods');
  const quests = readKb<{ quests: QuestEntry[] }>('quests');
  const diaries = readKb<{ diaries: DiaryEntry[] }>('diaries');
  const milestones = readKb<{ milestones: MilestoneEntry[] }>('milestones');
  const gathering: { plans: GatheringPlanDraft[] } = JSON.parse(readFileSync(join(dataDir, 'gathering.json'), 'utf8'));

  const names = [...new Set([...collectReferencedItems({
    methods: methodsFile.methods,
    quests: quests.quests,
    diaries: diaries.diaries,
    milestones: milestones.milestones,
  }), ...gatheringMaterialNames(gathering.plans)])];

  const mapping = await fetchPriceMapping();

  const rawItemIdRows = await bucket<{ page_name: string; id: string[] }>("bucket('item_id').select('page_name','id')");
  const itemIdRows: ItemIdRow[] = rawItemIdRows.map((row) => ({ page_name: row.page_name, id: row.id.map(Number) }));

  const resolved = resolveItemIds(names, mapping, itemIdRows);
  for (const name of names) {
    const id = MISSING_GATHERING_MATERIAL_IDS[name];
    if (id !== undefined && resolved.get(name)?.id === null) resolved.set(name, { id, generic: false });
  }
  const unresolved = names.filter((name) => resolved.get(name)?.id === null);

  const storelineRows = await bucket<StorelineRow>("bucket('storeline').select('sold_item','sold_by','store_buy_price','store_stock')");
  const droplineRows = await bucket<DropslineRow>("bucket('dropsline').select('item_name','page_name','drop_json')");
  const loclineRows = await bucket<LoclineRow>("bucket('locline').select('page_name','coordinates')");

  console.log(
    `Fetched Bucket rows: item_id=${itemIdRows.length}, storeline=${storelineRows.length}, dropsline=${droplineRows.length}, locline=${loclineRows.length}, mapping=${mapping.length}`,
  );

  const materials = buildMaterials({ names, resolved, mapping, storelineRows, droplineRows, loclineRows, methods: methodsFile.methods });

  // generatedAt: mapping/Bucket fetch time is not deterministic, so reuse the max
  // wiki revision timestamp already carried by methods/quests/diaries.
  const generatedAt = [methodsFile.generatedAt, quests.generatedAt, diaries.generatedAt].sort().at(-1)!;

  // Bucket and prices endpoints do not return page revision ids. Carry only genuine
  // page provenance from the generated inputs; never label current metadata as a Bucket snapshot.
  writeKb('materials', { materials }, generatedAt, sourceManifest(
    methodsFile.sources ?? [], quests.sources ?? [], diaries.sources ?? [], milestones.sources ?? [],
  ));
  logMaterialsSummary(materials, unresolved);
}

function logMaterialsSummary(materials: Material[], unresolved: string[]): void {
  const generic = materials.filter((m) => m.generic).length;
  const sourceCounts = new Map<string, number>();
  for (const material of materials) {
    for (const source of material.sources) {
      sourceCounts.set(source.type, (sourceCounts.get(source.type) ?? 0) + 1);
    }
  }

  console.log(`Built ${materials.length} materials (${generic} generic/unresolved):`);
  for (const [type, count] of sourceCounts) {
    console.log(`  ${type} sources: ${count}`);
  }
  if (unresolved.length > 0) {
    console.log(`Unresolved item names (${unresolved.length}): ${unresolved.join(', ')}`);
  }
}

async function expandMilestonesCommand(): Promise<void> {
  const path = join(kbDir, 'milestones.json');
  const raw = JSON.parse(readFileSync(path, 'utf8')) as { version: number; milestones: MilestoneLike[]; sources?: WikiSource[] };

  const rawItemIdRows = await bucket<{ page_name: string; id: string[] }>("bucket('item_id').select('page_name','id')");
  const itemIdRows: ExpandItemIdRow[] = rawItemIdRows.map((row) => ({ page_name: row.page_name, id: row.id.map(Number) }));

  const { milestones, summary } = expandMilestoneItemIds(raw.milestones, itemIdRows);

  writeFileSync(path, emitJson({ ...raw, milestones, sources: sourceManifest(raw.sources ?? []) }));

  console.log(`Processed ${summary.itemsProcessed} milestone item entries (ownedIf + recommended.gear alternatives + requirements.items).`);
  console.log(`Names with >1 id (${summary.multiId.length}): ${summary.multiId.join(', ')}`);
  console.log(`Unresolved names (${summary.unresolved.length}): ${summary.unresolved.join(', ') || 'none'}`);

  // Every milestone ownedIf/requirement item must resolve in materials.json (the plugin's item
  // gap wiki links and shortfall sources come from there); add the ones a newly curated entry
  // introduced, keyed by the entry's own primary id, rather than regenerating the whole file.
  const materialsFile = readKb<{ materials: Material[] }>('materials');
  const wanted = new Map<string, number>();
  for (const milestone of milestones) {
    for (const owned of milestone.ownedIf) wanted.set(owned.name, owned.id);
    for (const item of milestone.requirements.items) wanted.set(item.name, item.id);
  }
  const have = new Set(materialsFile.materials.map((m) => m.name));
  const missingNames = [...wanted.keys()].filter((name) => !have.has(name));
  if (missingNames.length > 0) {
    const mapping = await fetchPriceMapping();
    const storelineRows = await bucket<StorelineRow>("bucket('storeline').select('sold_item','sold_by','store_buy_price','store_stock')");
    const droplineRows = await bucket<DropslineRow>("bucket('dropsline').select('item_name','page_name','drop_json')");
    const loclineRows = await bucket<LoclineRow>("bucket('locline').select('page_name','coordinates')");
    const methodsFile = readKb<{ methods: Method[] }>('methods');
    const materials = addMissingMaterials(materialsFile.materials, wanted, { mapping, storelineRows, droplineRows, loclineRows, methods: methodsFile.methods });
    writeFileSync(join(kbDir, 'materials.json'), emitJson({ ...materialsFile, materials,
      sources: sourceManifest(materialsFile.sources ?? [], raw.sources ?? [], methodsFile.sources ?? []),
    }));
  }
  console.log(`Materials added for milestone items: ${missingNames.join(', ') || 'none'}`);
}

async function buildGatheringCommand(): Promise<void> {
  const draftPath = join(dataDir, 'gathering.json');
  const draft: { version: number; plans: GatheringPlanDraft[] } = JSON.parse(readFileSync(draftPath, 'utf8'));

  const materialsFile = readKb<{ materials: Material[] }>('materials');
  let materials = materialsFile.materials;

  const have = new Set(materials.map((m) => m.name));
  const missingNames = gatheringMaterialNames(draft.plans).filter((name) => !have.has(name));

  if (missingNames.length > 0) {
    const mapping = await fetchPriceMapping();
    const storelineRows = await bucket<StorelineRow>("bucket('storeline').select('sold_item','sold_by','store_buy_price','store_stock')");
    const droplineRows = await bucket<DropslineRow>("bucket('dropsline').select('item_name','page_name','drop_json')");
    const loclineRows = await bucket<LoclineRow>("bucket('locline').select('page_name','coordinates')");

    materials = addMissingGatheringMaterials(materials, draft.plans, {
      mapping,
      storelineRows,
      droplineRows,
      loclineRows,
      methods: [],
    });

    writeFileSync(join(kbDir, 'materials.json'), emitJson({ ...materialsFile, materials }));
  }

  const plans = resolveGatheringPlans(draft.plans, materials);
  writeKb('gathering', { ...draft, plans }, undefined, materialsFile.sources ?? []);

  console.log(`Built ${plans.length} gathering plans (materials added: ${missingNames.join(', ') || 'none'}).`);
}

const command = process.argv[2];
if (command === 'quests') {
  await buildQuestsCommand();
} else if (command === 'diaries') {
  await buildDiariesCommand();
} else if (command === 'methods') {
  await buildMethodsCommand();
} else if (command === 'materials') {
  await buildMaterialsCommand();
} else if (command === 'expand-milestones') {
  await expandMilestonesCommand();
} else if (command === 'gathering') {
  await buildGatheringCommand();
} else {
  console.error(`Unknown command: ${String(command)}. Usage: npm run build-kb -- quests|diaries|methods|materials|expand-milestones|gathering`);
  process.exit(1);
}
