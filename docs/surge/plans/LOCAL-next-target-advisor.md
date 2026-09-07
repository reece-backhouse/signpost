# LOCAL-next-target-advisor — Implementation Plan

Jira: none
Status: Implemented S1–S5 + S4.1–S4.3 (2026-09-07); S6 not started
Date: 2026-09-07
Links: docs/surge/specs/LOCAL-next-target-advisor.md (spec), docs/surge/reviews/LOCAL-next-target-advisor.md (grill), tickets/001-next-target-advisor.md

Profile applied: deep planning (complex/risky), strict review, TDD on,
subagents on, no Python, no emoji, no AI attribution.

## Toolchain (verified on this machine)

- Java 17 JDK, Gradle 9.6.1, Node 22.22.2, npm 10.9.7.
- Gradle resolves `net.runelite:client:1.12.38` and
  `net.runelite:runelite-api:1.12.38` from repo.runelite.net (spike run).
- RuneLite.app installed; `~/.runelite/profiles2` has a live profile, so the
  dev client (`gradle run`) can log in for manual ACs.

## Gates (every slice, run and read before marking done)

```
cd plugin && ./gradlew test            # JUnit 5, engine + loader + store + no-network scan
cd kb-build && npm run typecheck && npm test   # tsc --noEmit, vitest on fixtures
```

Manual dev-client checklist per slice (recorded in the slice's commit body).

## Branching

`main` local only (no remote). Each slice on `feat/LOCAL-nta-s<N>-<desc>`,
merged to `main` with `--no-ff` when its gates are green and its review
applied. No PR (no remote); the merge commit body is the PR body.

Ruling: no remote is created on the user's behalf. Add one later with
`git remote add` and push `main`.

## Slice S1 — Plugin skeleton + snapshot + store

Branch `feat/LOCAL-nta-s1-skeleton`. ACs: A1–A5, A7, A8, G1, G2.

### Task 1: Gradle project

`plugin/`: `settings.gradle`, `build.gradle` from the
example template with `runeLiteVersion = '1.12.38'` for `client` and
`runelite-api`, `options.release = 11`, Lombok 1.18.30, JUnit 5
(`junit-jupiter:5.10.2`, `useJUnitPlatform()`), `run` task,
`runelite-plugin.properties`. Generate the wrapper (`gradle wrapper`).
Gate: `./gradlew test` runs zero tests green.
### Task 2: No-network gate

`NoNetworkTest`: walks `src/main/java`, fails if any
file imports `java.net.http`, `java.net.URL`, `HttpURLConnection`,
`okhttp3`, or `java.net.Socket`. Seam: the source tree. Written first,
watched to pass on an empty tree, kept forever (G2).
### Task 3: `Snapshot`

(immutable, Lombok `@Value @Builder`): `accountHash`,
`accountType` (enum, 7 values from varbit 1777), `skills:
Map<Skill, SkillState(level, xp)>`, `quests: Map<Quest, QuestState>`,
`bank / inventory / equipment: Map<Integer, Integer>`, `itemNames:
Map<Integer, String>`, `diaryTiers: Map<DiaryTier, Boolean>`,
`diaryVarps: Map<Integer, Integer>` (1176–1199 raw), `karamjaVarbits:
Map<Integer, Integer>`, `combatAchievementTiers: Map<Integer,Boolean>`,
`bankKnown: boolean`, `bankAsOf: Instant`. Test: builder defaults and
`withBank(...)` copy. Seam: the type.
### Task 4: `SnapshotCollector.collect(Client, ItemManager, BankMemory)`

pure
read of client state; asserts `client.isClientThread()`. Not unit-tested
(needs a client); verified in dev client.
### Task 5: `AccountStore`

`load(accountHash)` / `save(AccountData)` as JSON
under `RuneLite.RUNELITE_DIR/next-target/<hash>.json`, dir injectable.
`AccountData { bank, bankAsOf, snoozes, ignores, pins, focusGoalId }`.
Tests on a temp dir: round-trip, missing file → empty data, corrupt file
→ empty data + logged warning (fail loud in log, not crash). Seam:
`AccountStore` API.
### Task 6: `NextTargetPlugin`

wiring per spec rulings 9, 18, 19, 23:
- `startUp`: load KB (stub empty in S1), add `NavigationButton` + panel.
- `GameStateChanged(LOGGED_IN)` → arm `firstTickPending`; `GameTick` with
  it set → `requestSnapshot()`.
- `ItemContainerChanged` where `containerId == InventoryID.BANK.getId()`
  → cache container items. `WidgetClosed` where `groupId ==
  InterfaceID.BANKMAIN` → `store.save(bank)`, `requestSnapshot()`.
- `StatChanged` where level changed → `requestSnapshot()`.
- `VarbitChanged` where `varpId == VarPlayer.QUEST_POINTS (101)` or `varpId`
  in 1176..1199 → `requestSnapshot()`.
- `requestSnapshot()` = `clientThread.invoke(collect → executor.submit(run
  engine with generation g) → if g current: invokeLater(panel.render))`.
- `EngineRunner`: single-thread executor + `AtomicInteger generation`.
  Test: two submits, first result discarded. Seam: `EngineRunner.submit`.
### Task 7: `NextTargetPanel`

header only: account type, quests done/total,
total level, "Bank: unknown — open your bank once" or "Bank as of
<time>", Refresh button (calls `requestSnapshot`). KB footer placeholder.
### Task 8: Dev-client checklist

(commit body): A1 type shown; A2 quest count
matches quest tab; A3 spot-check three skills; A4 open+close bank →
file written, header time updates; A5 fresh file → "unknown"; A7 hop
world → header rebuilds; A8 no client-thread warnings in log.

## Slice S2 — Knowledge base: quests + diaries

Branch `feat/LOCAL-nta-s2-kb-quests-diaries`. ACs: A6, B1–B3, B8.

### Task 9: kb-build project

`package.json` (type module, scripts `build-kb`,
`test`, `typecheck`), `tsconfig` strict, `vitest`, `tsx`. No runtime
deps (Node 22 `fetch`). `src/wiki.ts`: `fetchRevisions(titles[])`
batched 50, `bucket(query)` paginated 5000, `User-Agent:
next-target-advisor-kb-build (https://github.com/reece; reece@develp.io)`,
`maxlag=5`, serial. Tests: pagination math on a fake fetch. Seam:
`wiki.ts` exports.
### Task 10: `lua.ts`

parser for the subset used by `Module:Questreq/data` and
`Module:Skill calc/*` (nested tables, strings with escapes, numbers,
booleans, `key = value` and positional). Fixture: `questreq.lua`,
`calc_herb.lua` (from research). Tests: parses both files; SotE entry
equals expected object. Seam: `parseLua(text)`.
### Task 11: `questreq.ts`

Lua → `{name, skills[{skill, level, boostable,
ironmanOnly}], prereqs[]}`; drops `Name_of_quest`.
### Task 12: `questPages.ts`

wikitext → `{items[{name, qty}], questPoints,
requirements?}` from `{{Quest details}}` / `{{Quest rewards}}`; the
`requirements` fallback parses `{{SCP|Skill|N}}`, `{{Boostable|no}}`,
nested `[[Quest]]` links (direct = `**` depth only). Fixture: `sote.txt`.
### Task 13: `quests.ts` resolver

(ruling 13): RuneLite `Quest` name list is
committed as `data/runelite-quests.json` (generated once from
`Quest.java` by a tiny script, checked in); `data/aliases.json` for the
10 RFD names; merge order Questreq → page fallback → empty. Emits
`quests.json` sorted by id. Test: every RuneLite quest name resolves; RFD
alias resolves; a miniquest gets empty reqs with `source: "none"`.
### Task 14: `diaries.ts`

per diary page, parse tier tables → tasks
`{ordinal, text, skills[], quests[], items[], notes[], ironmanOnly}`;
`data/diary-vars.json` transcribed from Quest Helper (44 tiers:
`{diary, tier, varp | varbits[], firstBit}`) joined by ordinal. Fixture:
`varrock.txt`. Tests: Varrock Easy task 3 = Mining 15 + pickaxe item;
task 2 = quest Rune Mysteries; bit for ordinal N = N.
### Task 15: `emit.ts`

stable key order, `generatedAt` = max revision timestamp
(ruling 21). Test: two emits of the same input are byte-identical.
### Task 16: Plugin side

`kb/` model (Gson, immutable), `KnowledgeBase.load()`
from classpath. Tests: bundled JSON parses; every `Quest` constant has an
entry (B2 as a test); every diary tier has 1..N contiguous ordinals.
### Task 17: Snapshot → diary task completion

`DiaryProgress.completed(snapshot,
kb)`. Test: given varp 1176 = 0b0110, tasks 1 and 2 complete.
### Task 18: Dev-client check

(ruling 20): temporary debug log per tier
 "KB popcount vs in-game count" for all 44 tiers; recorded in commit body.

## Slice S3 — Gap engine + Focus tree

Branch `feat/LOCAL-nta-s3-gap-focus`. ACs: C1–C5, C9 (part), E1, E2, E7,
F1, F3, F4.

### Task 19: Model

`Goal {id, category, name, wikiUrl, priority}`, `Gap` sealed
set: `SkillLevel`, `QuestPrereq`, `Item`, `DiaryTask`, `DiaryTier`,
`CombatAchievementTier`; `GoalStatus {goal, gaps[], ready, bankUnknown}`.
### Task 20: Fixtures

`SnapshotBuilder`, `KbBuilder` (tiny DSL:
`quest("SotE").needs(HERBLORE, 70).after("ME2")`). One place; reused by
every engine test (grill: fixture duplication is a finding).
### Task 21: `GapEngine.evaluate(snapshot, kb) -> List<GoalStatus>`

, tests one per
AC clause:
- C1 each gap type produced;
- C2 transitive chain SotE → ME2 → ME1 with deepest incomplete flagged;
- C3 have = bank + inventory + equipment; unknown bank → `have = null`;
- C4 iron (types 1–6) drops GE source; no source left → `mustObtain`;
- C5 boostable within `kb.maxBoost(skill)` → `boostableFrom`;
- ruling 22 ironman-only req ignored for mains, applied for irons;
- ruling 16 diary tier goal with `DiaryTask` gaps.
### Task 22: Focus picker and tree panel — DEFERRED

Ruling (user, 2026-09-07): a standalone picker plus requirement tree duplicates
Quest Helper. No `FocusPanel` in S3. The goal drill-down (what is missing and
the route to it) is built in S5 as the detail view opened from a Suggest card
("Do this"), with a small search box to choose any goal. E1/E2/E7 move to S5.

### Task 23: Wiring

`Advice { statuses, focus }`; `Prefs` from store; panel mode
switch. Refresh triggers per ruling 23 (F4). Focus recompute on any
snapshot (E6 prerequisite).
### Task 24: Dev-client checklist

pick SotE, tree matches wiki reqs; complete a
level → tree updates without reselect.

## Slice S4 — Milestones + Suggest mode

Branch `feat/LOCAL-nta-s4-suggest`. ACs: B4, B5, D1–D9, F2.

### Task 25: `milestones.json`

hand-drafted (~40 entries incl. slayer targets and
bosses per ticket B4/B5) + `priorities.json`. Loader test: schema valid,
every item id exists in `materials.json`/mapping (S5 adds materials; in
S4 validate ids against prices mapping snapshot committed to kb-build).
### Task 26: `Ranker.rank(statuses, prefs)`

per ruling 14. Tests: formula on
three hand-computed goals; ready-now first; bank-unknown never ready;
snoozed/ignored excluded; pinned first.
### Task 27: `SuggestSelector.pick3(ranked)`

per ruling 17. Tests: two-category
rule; pins fill first; single-category fallback.
### Task 28: `WhyBuilder.why(status)`

(D3) templates over ranking inputs +
`unlocks`/`reason` from KB. Tests: one per template.
### Task 29: `Prefs`

snooze `{goalId, until, gapFingerprint}` (D6: expires on
time or when the gap fingerprint changes), ignore set, pins. Tests.
### Task 30: `SuggestPanel`

three cards with why + Do this / Not now / Ignore,
ranked list (10 + show more), Snoozed and Ignored sections.
### Task 31: Dev-client checklist

D4–D9 by clicking through.

## Slice S5 — Routes + material sourcing

Branch `feat/LOCAL-nta-s5-routes`. ACs: B6, B7, C6–C8, C9 (rest), E3–E6, F5.

### Task 32: kb-build `methods.ts`

`Module:Skill calc/<Skill>` for the nine
planned skills → `methods.json {skill, methods[{name, levelReq, xp,
materials[{itemId, qty}], outputs[{itemId, qty}], type[]}]}`; merge
Bucket `recipe` for 0-XP intermediates and `boostable`; drop Barbarian
type (ruling 15). Fixture `calc_herb.lua`, `ppot.txt`. Tests.
### Task 33: kb-build `materials.ts`

for every item id referenced anywhere in the
KB (ruling 7), Bucket `dropsline` / `storeline` / `locline` + prices
mapping → `materials.json {itemId, name, sources[{type, where, detail,
accountTypes}]}`. Fixture `snape.txt`. Tests.
### Task 34: `RoutePlanner.route(skill, fromXp, toXp, bank, kb)`

per ruling 15.
Tests: the ticket's Herblore example (prayer pots to 66 then brews);
switch on unlock; tie-break; materials consumed once across methods;
outputs feed later steps (grimy → clean → unf → potion); no affordable
method → empty route + uncovered xp.
### Task 35: `ShortfallResolver`

(C7): best method at current sim level →
`(item, have, need)` + sources filtered by account type; craft sources
recurse one level. Test: toadflax chain from the ticket.
### Task 36: `FocusPicker.next(status)`

E3 rule a→d. Tests: one per branch.
### Task 37: Boss gear any-of

(C8). Test.
### Task 38: Perf test

(F5): maxed-account fixture, all goals, assert engine < 1 s.
### Task 39: Panel

route checklist, shortfall + sources, wiki links (F3).
### Task 40: Dev-client checklist

SotE focus shows Herblore route from real bank.

## Slice S6 — AI narrative

Separate spec after S1–S5 (ruling 8). Not planned here.

## Ship-skill routing for this run

Tier: **Oversize** (well over one agent-day even with design settled).
Per the ship skill: this plan is the epic-level Phases 1–4; S1 is shipped in
this run; S2–S5 are their own runs against this plan. Child tickets are the
slice sections above (no tracker mapped for this workspace, so the plan is
the ledger).

## Fixture inventory (copied from research scratchpad into kb-build/test/fixtures)

`questreq.lua`, `calc_herb.lua`, `sote.txt`, `varrock.txt`, `snape.txt`,
`ppot.txt`, `VarrockEasy.java` (reference only, not shipped), first 200
rows of `mapping.json`.
