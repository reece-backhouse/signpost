# Signpost — RuneLite plugin

RuneLite plugin: reads account state (quests, skills, bank, diaries,
achievements) and suggests what to focus on next, with routes to get there.

## Layout

- repo root — the RuneLite plugin (Java 11, Gradle, `runeLiteVersion = latest.release` as the Plugin Hub
  requires; the KB quest test fails when a new RuneLite release adds a `Quest` constant, so rebuild the KB)
  Hub metadata: `runelite-plugin.properties`, `icon.png`, `LICENSE`, `README.md`; submission steps in `docs/plugin-hub.md`.
  - `snapshot/` client-thread reads → immutable `Snapshot`
  - `kb/` knowledge base model + loader (bundled JSON under `src/main/resources/kb/`)
  - `engine/` pure logic: GapEngine, StageEstimator, Ranker, SuggestSelector, WhyBuilder,
    RoutePlanner, ShortfallResolver, NextStepPicker, Engine (one `Advice` per snapshot)
  - `store/` per-account JSON persistence (`~/.runelite/next-target/<accountHash>.json`)
  - `ui/` Swing: header, search, SuggestPanel (pick one of three, Why? per goal, post-action status line, empty-state reasons, Done strip + Achieved this session), GoalDetailPanel (route, shortfall with gathering plans)
- `kb-build/` — TypeScript (Node 22) scripts that generate the KB JSON from the OSRS wiki
  (`npm run build-kb -- quests|diaries|methods|materials|expand-milestones|gathering`)
  - `data/` inputs: RuneLite quest list, aliases, diary var map (from Quest Helper), RuneLite sources
  - `src/main/resources/kb/milestones.json`, `priorities.json` and `data/gathering.json` are hand-curated (stage, recommended gear-role groups, obtainedFrom, ownedIfMin for outfits, speedsUp skill, prerequisite milestone, display notes, `poh` category for house rooms; step-by-step gathering loops); after adding a milestone run `expand-milestones` to fill variant ids and add its items to materials.json

## Conventions

- Engine code is pure: no `Client`, no Swing, no I/O. It takes `Snapshot` + `KnowledgeBase`
  (+ `AccountData` prefs) and returns data. All logic tests live there (JUnit 5).
- Swing panels only render `Advice`; every mutation goes through `NextTargetPlugin` actions,
  which serialise account-data changes on the engine executor.
- Anything touching `Client` runs on the client thread; the engine runs on a single-thread
  executor with a generation guard; the panel updates on the EDT.
- No network calls from the plugin (`NoNetworkTest` scans imports). Wiki links via `LinkBrowser`.
- Fail loud: malformed KB data throws at load, naming the entry and field.
- Tests: `./gradlew test`; `cd kb-build && npm run typecheck && npm test`.
- Commits: `type: scope subject`, imperative, files added individually, no AI attribution.

## Running the dev client

`./gradlew run` launches RuneLite with the plugin loaded (`--developer-mode`).
Jagex accounts: install RuneLite launcher ≥ 2.6.3, `launchctl setenv RUNELITE_ARGS
--insecure-write-credentials` (or add it in `RuneLite --configure`), launch once via the
Jagex launcher, then the dev client reads `~/.runelite/credentials.properties`. Delete that
file when done. On macOS JDK 17 the `run` task needs `--add-exports java.desktop/com.apple.eawt`
(already in build.gradle). Sideloading into the launcher-run client is not possible
(RuneLite disables sideloading outside developer mode).

## Known data caveats

- Diary per-task bits come from Quest Helper's declaration order; the game's per-tier COUNT
  varbit is trusted when it disagrees (Desert Medium's Pollnivneach task uses an unknown
  ironman variable).
- Milestone readiness = entry requirements + curated `recommended` profile (skills and every
  required gear role). `GearCatalog`/`GearComparison` share actual-item variants and role-aware
  replacement coverage across readiness, boss rewards, standalone gear and ladders; coverage
  never supplies crafting ingredients. Armour completion counts distinct pieces, not variants.
  Stage 1–4 per milestone; the account stage needs two pieces of a stage's gear; goals more than
  one stage above are "Later".
- `GoalObjective` ranks known remaining gains (major/useful/situational) and concrete unfinished
  CAs independently of entry readiness. Situational-only goals do not enter the ready-primary
  tier. Partial/offline snapshots use `confirmedAbsentItems`; unseen items are not inferred missing.
  The progression regression fixture is `src/test/resources/fixtures/progression-account.json`.
- Skill targets ("70 Herblore") are synthesised from upcoming goals' skill gaps; only bank-covered
  targets can be picked, uncovered ones never outrank their parent goal.
- Diagnostics: each engine run logs the top three picks and the Moons/GWD watch lines at INFO.
- Group ironman shared storage (`INV_GROUP_TEMP`, persisted when `SHARED_BANK` closes) is summed with
  the bank for ownership, routes and shortfalls; unseen storage counts as empty, not unknown.
  Config "Count group storage" (default on) gates the read. `AccountData.version` is 2.
