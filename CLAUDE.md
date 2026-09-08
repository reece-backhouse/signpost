# Signpost — RuneLite plugin

Personal RuneLite plugin: reads account state (quests, skills, bank, diaries,
achievements) and suggests what to focus on next, with routes to get there.

## Layout

- `tickets/` — product tickets with acceptance criteria (source of truth for scope)
- `docs/surge/index.md` — Surge Engineering Workflow artifacts (spec, grill, plan)
- repo root — the RuneLite plugin (Java 11, Gradle, `runeLiteVersion = latest.release` as the Plugin Hub
  requires; the KB quest test fails when a new RuneLite release adds a `Quest` constant, so rebuild the KB)
  Hub metadata: `runelite-plugin.properties`, `icon.png`, `LICENSE`, `README.md`; submission steps in `docs/plugin-hub.md`.
  - `snapshot/` client-thread reads → immutable `Snapshot`
  - `kb/` knowledge base model + loader (bundled JSON under `src/main/resources/kb/`)
  - `engine/` pure logic: GapEngine, StageEstimator, Ranker, SuggestSelector, WhyBuilder,
    RoutePlanner, ShortfallResolver, NextStepPicker, Engine (one `Advice` per snapshot)
  - `store/` per-account JSON persistence (`~/.runelite/next-target/<accountHash>.json`)
  - `ui/` Swing: header, search, SuggestPanel (pick one of three, Why? per goal), GoalDetailPanel (route, shortfall with gathering plans)
- `kb-build/` — TypeScript (Node 22) scripts that generate the KB JSON from the OSRS wiki
  (`npm run build-kb -- quests|diaries|methods|materials|expand-milestones|gathering`)
  - `data/` inputs: RuneLite quest list, aliases, diary var map (from Quest Helper), RuneLite sources
  - `src/main/resources/kb/milestones.json`, `priorities.json` and `data/gathering.json` are hand-curated (stage, recommended profile with gearOwnedMin, obtainedFrom, ownedIfMin for outfits, speedsUp skill, prerequisite milestone, display notes, `poh` category for house rooms; step-by-step gathering loops); after adding a milestone run `expand-milestones` to fill variant ids and add its items to materials.json

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
- Milestone readiness = entry requirements + curated `recommended` profile (skills, gear with a
  minimum owned count); a boss is "Ready now" only when both are met. Stage 1–4 per milestone;
  the account stage needs two pieces of a stage's gear; goals more than one stage above are "Later".
- Skill targets ("70 Herblore") are synthesised from upcoming goals' skill gaps; only bank-covered
  targets can be picked, uncovered ones never outrank their parent goal.
- Diagnostics: each engine run logs the top three picks and the Moons/GWD watch lines at INFO.

## Surge Engineering Workflow

- `/surge:start <slug>` to begin; artifacts under `docs/surge/` (see `docs/surge/index.md`).
- Spec → grill → plan → implement (TDD, subagents) → review.
