# Next Target Advisor — RuneLite plugin

Personal RuneLite plugin: reads account state (quests, skills, bank, diaries,
achievements) and recommends what to focus on next, with routes to get there.

## Layout

- `tickets/` — product tickets with acceptance criteria (source of truth for scope)
- `docs/surge/index.md` — Surge Engineering Workflow artifacts (specs, plans, reviews)
- `plugin/` — the RuneLite plugin (Java 11, Gradle, RuneLite plugin template layout)
- `kb-build/` — TypeScript scripts that generate `plugin/src/main/resources/kb/*.json` from the OSRS wiki
- `investigations/` — debugging notes (see user protocol)

## Conventions

- Java 11 source level (RuneLite requirement). Lombok is available via RuneLite.
- Engine code (`gap`, `rank`, `route` packages) is pure: no `Client`, no Swing, no I/O.
  It takes a `Snapshot` and `KnowledgeBase` and returns data. All logic tests live there.
- Swing panel code only renders engine output; never computes.
- Game-thread rule: anything touching `Client` runs on the client thread; engine runs off it.
- No network calls from the plugin in v1.
- Tests: JUnit 5 via Gradle. `./gradlew test` from `plugin/`.
- Commits: `type: scope subject`, imperative, no AI attribution.

## Surge Engineering Workflow

- `/surge:start <slug>` to begin; artifacts under `docs/surge/` (see `docs/surge/index.md`).
- Spec → grill → plan → implement (TDD, subagents) → review.
