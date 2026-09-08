# LOCAL-next-target-advisor — Spec

Jira: none
Status: Implemented (S1–S5, S4.1–S4.3, S4.6–S4.7, S7); S6 AI narrative not started
Date: 2026-09-07
Links: tickets/001-next-target-advisor.md, docs/surge/reviews/LOCAL-next-target-advisor.md (grill), docs/surge/plans/LOCAL-next-target-advisor.md (plan)

## Problem

Deciding "what next" in OSRS means cross-referencing quest requirements,
diary tasks, gear progression, your bank and your levels across a dozen wiki
tabs. The ticket asks for a RuneLite side panel that does that cross-reference
from live account state and gives (1) ranked suggestions with a reason and
(2) a route to any goal you pick, including sourcing missing materials.

## What already exists (verified)

Nothing in this workspace: empty directory before this work. Verified against
RuneLite `master` on 2026-09-07:

- `Quest.getState(Client)` → `QuestState {NOT_STARTED, IN_PROGRESS, FINISHED}`.
- `Client.getRealSkillLevel(Skill)`, `getSkillExperience(Skill)`.
- `Client.getItemContainer(InventoryID.BANK|INVENTORY|EQUIPMENT)`.
- `Varbits.ACCOUNT_TYPE` (1777, gameval `VarbitID.IRONMAN`).
- Diary tier varbits `Varbits.DIARY_<AREA>_<TIER>`; per-task completion in
  `VarPlayer` bitfields (Karamja: per-task varbits). See Data sources.
- `Varbits.COMBAT_ACHIEVEMENT_TIER_*`.

Plugin template, panel API and wiki data access: see "Data sources" below.

## Architecture

Two deliverables in one repo:

```
plugin/                      RuneLite external plugin (Java 11, Gradle)
  src/main/java/dev/reece/nta/
    NextTargetPlugin.java    wiring: events → Snapshot → engine (off-thread) → panel
    snapshot/                Snapshot record + SnapshotCollector (client thread only)
    kb/                      KnowledgeBase loader (Gson from bundled JSON) + model types
    engine/                  PURE: GapEngine, Ranker, RoutePlanner, WhyBuilder, FocusPicker
    store/                   per-account persistence (bank snapshot, snoozes, pins, focus)
    ui/                      Swing PluginPanel; renders engine output only
  src/main/resources/kb/     quests.json, diaries.json, milestones.json, methods.json, materials.json
kb-build/                    TypeScript (Node 22) scripts producing resources/kb/*.json
```

Data flow:

```
RuneLite events ─► SnapshotCollector (client thread) ─► Snapshot (immutable)
                                                         │
KnowledgeBase (loaded once at startup) ──────────────────┤
                                                         ▼
                              Engine.run(snapshot, kb, prefs) on executor
                                                         ▼
                              Advice { suggestions[], focusPlan? }
                                                         ▼
                              SwingUtilities.invokeLater → Panel.render(advice)
```

Engine is a pure function of `(Snapshot, KnowledgeBase, Prefs)`. Every AC in
groups C, D, E is testable with plain JUnit and hand-built fixtures.

## Scope → acceptance criteria

Ticket AC groups map to slices (each slice is a separate Surge plan section
and a separate branch/PR; see Slicing):

| Slice | Ticket ACs | Deliverable |
|---|---|---|
| S1 Skeleton + snapshot | A1–A5, A7, A8, G1, G2 | Plugin builds, loads in dev client, panel shows account header; bank persisted per account; no-network gate |
| S2 KB: quests + diaries | A6, B1–B3, B8 | `kb-build` produces quests.json, diaries.json (incl. diary bit map); loader + model in plugin; per-tier dev-client bit check |
| S3 Gap engine (engine only) | C1–C5, C9 (chains, iron filter, unknown bank, boostable), F1, F4 | Pure engine; no new panel (user ruling: a tree alone duplicates Quest Helper) |
| S4 Milestones + Suggest | B4, B5, D1–D9, F2 | Suggest mode: pick-one-of-three, why, snooze/ignore/pin |
| S5 Routes + sourcing + goal detail | B6, B7, C6–C8, C9 (route, shortfall recursion, boss gear), E1–E7, F3, F5 | Skill routes from bank with exact XP maths, shortfall + sources; goal detail view opened from Suggest, with a search box to pick any goal |
| S6 AI narrative | H1–H5 | Bedrock toggle (deferred; separate spec) |

## Decisions (rulings)

Each `Ruling:` is a decision made on the user's behalf with the recommended
option; cost-if-wrong noted.

1. **Ruling: sideloaded personal plugin, not Plugin Hub** — the ticket's
   decision 5; Plugin Hub review blocks Bedrock and slows iteration — cost if
   wrong: a later Hub submission needs the AI slice removed and a review pass;
   no architectural change.
2. **Ruling: repo layout is `plugin/` + `kb-build/` in one git repo** — one
   repo, two toolchains; simplest for a solo project — cost if wrong: split
   later, no code change.
3. **Ruling: kb-build is TypeScript on Node 22** — user profile: TypeScript
   primary, no Python for application code — cost if wrong: none.
4. **Ruling: KB JSON is committed to the repo** (not regenerated on every
   build) — reproducible plugin builds without network; regenerate
   deliberately with `npm run build-kb` — cost if wrong: stale data until
   regenerated; `generatedAt` is shown in the panel footer (B8).
5. **Ruling: milestones, slayer targets, bosses, and methods are hand-curated
   JSON that I draft from wiki progression pages; the user edits** — answers
   ticket open questions 2 and 5 — cost if wrong: JSON edits.
6. **Ruling: planned skills = Herblore, Prayer, Crafting, Smithing, Cooking,
   Fletching, Construction, Magic, Firemaking** — answers open question 4 —
   cost if wrong: add a methods table for another skill.
7. **Ruling: materials table covers only items referenced by methods.json,
   quests.json, diaries.json, milestones.json** — answers open question 6 —
   cost if wrong: an unreferenced item shows a bare wiki link.
8. **Ruling: Bedrock (S6) is a separate spec after S1–S5 ship** — open
   question 3 is deferred; nothing in S1–S5 depends on it.
9. **Ruling: engine runs on a single-thread executor; at most one run in
   flight, latest request wins** — events can burst (bank close fires many
   `ItemContainerChanged`); debounce 1 tick — cost if wrong: minor UI lag.
10. **Ruling: persistence is a JSON file per account hash under
    `RuneLite.RUNELITE_DIR/next-target/`**, not ConfigManager — bank
    snapshots are too large for config values — cost if wrong: migrate file
    to profile config later.
11. **Ruling: item names come from `ItemManager.getItemComposition` on the
    client thread at snapshot time and are stored in the Snapshot**, so the
    engine and panel never touch the client — cost if wrong: slightly larger
    snapshot.
12. **Ruling: no boosts are read from the client (temporary boosted levels
    ignored); C5 "boostable from N" uses the KB's max-boost-per-skill table** —
    deterministic and testable — cost if wrong: none for correctness.

26. **Ruling (user, 2026-09-07): no standalone Focus picker/tree screen** — it
    duplicates Quest Helper; the product is Suggest (what next, why) and the
    route (how, from your bank, with exact XP). S3 ships the engine only; the
    goal detail view lands in S5 as the drill-down from a Suggest card, with a
    search box for choosing any goal — cost: none.

27. **Ruling (user feedback, 2026-09-07 15:19): readiness needs a recommended
    profile and an account stage.** Entry requirements alone made ToB/CoX
    "Ready now" for a mid-game account. Slice S4.1: milestones carry `stage`
    (1 early, 2 mid, 3 late, 4 endgame) and bosses/gear carry `recommended`
    (skills, combat level, gear owned) treated as gaps; `StageEstimator`
    derives the account stage from combat level, total level, quest count and
    owned key milestones; goals more than one stage above the account never
    enter the top three (score ×0.05, listed under "Later"); a boss is ready
    only when entry and recommended are both met; quest priorities are
    curated in priorities.json (hub quests 8–9, filler 3) — cost: curation
    accuracy, all editable JSON.

28. **Ruling (user request, 2026-09-07): skill targets are goals; gathering
    plans for shortfalls.** The engine synthesises "<level> <Skill>" goals
    from the lowest unmet level any stage-appropriate goal requires (priority
    from the parent, ready when the bank covers the route); a curated
    `gathering.json` gives step-by-step loops for common secondaries and
    herbs, shown in the shortfall before the raw source list — cost: curation.

29. **Ruling (user request, 2026-09-07): every suggestion carries a "Why?"
    explanation** — the gap engine records met requirements as well as gaps;
    the why builder emits a short list of lines (met gear/stats with names
    and counts, what is missing with have/need, the parent goal a skill
    target serves, whether the bank covers the route); cards show it
    collapsed, the detail view in full — cost: none.

30. **Ruling (live run + user ruling, 2026-09-08 11:45): the shortfall
    always shows the fastest method; when its materials are not all
    obtainable it also offers the best fully-obtainable method as an
    alternative.** A GIM at Herblore 62 was told "Weapon poison: Kwuarm potion
    (unf) need 2,590" — the highest-xp method at that level, with a drop-only
    herb no iron can farm. `Shortfall.method` stays the highest-xp candidate
    at the route's final level (never hidden); `Shortfall.alternative` is the
    highest-xp candidate whose every material is obtainable, resolved with
    its own items, set only when it differs from the primary (null when the
    primary is itself obtainable or nothing is). A material is obtainable
    when the simulated bank covers the full need for that candidate, it has
    a curated gathering plan, a non-iron account can buy it (shop or GE
    source), or it has a craft source whose intermediate's ingredients are
    all obtainable by the same rules (one level, no deeper recursion).
    `Shortfall` also carries `xpShort` and `actionsNeeded` so the UI can
    print "~403,000 xp: Prayer potion(3) ×4,600" — cost: an iron may be
    offered a lower-xp alternative when the KB lacks a plan for a gatherable
    herb; fix by curating gathering.json.

Rulings from the grill (docs/surge/reviews/LOCAL-next-target-advisor.md):

13. **Ruling: quest name resolver in kb-build is three-step** — (a) committed
    `aliases.json` for the 10 Recipe for Disaster subquest names, (b) fallback
    to `{{Quest details |requirements=}}` wikitext parsed for `{{SCP|Skill|N}}`
    + `{{Boostable}}` + quest links, (c) miniquests/new quests absent from
    both get an entry with empty reqs and `source: "none"`. B2's build-fail
    applies to the merged result. The `Name_of_quest` stub is dropped —
    grill 1 — cost if wrong: a quest shows no reqs until aliased.
14. **Ruling: ranking formula** — `score = priority × closeness`,
    `closeness = 1 / (1 + unmet + xpDelta / 250000)`; ties: priority desc,
    then name asc. Default priority by category: quest 5, diary tier 4,
    milestone/boss/slayer_target from their JSON; `priorities.json` (hand
    curated) overrides any goal by id. `Item.have = unknown` counts as 0
    unmet and labels the goal "bank unknown", never "Ready now" — grill 2 —
    cost if wrong: tune two constants.
15. **Ruling: route simulation loop** — at each step pick the highest-XP
    method with `levelReq ≤ simLevel` (ties: lower levelReq, then name);
    take `min(affordable, ceil((xpAtNextUnlock − simXp) / xpPerAction))`
    actions, so a newly unlocked better method is switched to; consume
    materials from and add outputs to a simulated bank; stop when the target
    XP is reached or no method is affordable. Barbarian-mix type is excluded.
    0-XP intermediate recipes (e.g. unfinished potions, cleaning is 
    non-zero) are imported from Bucket `recipe` into methods.json so the C7
    craft recursion has a target — grill 3 — cost if wrong: route quality.
16. **Ruling: diary goal = tier; tasks are gap nodes** — `DiaryTask` gap
    carries its own skill/quest/item reqs; tasks appear in the Focus picker
    as shortcuts to their tier — grill 4.
17. **Ruling: D4 category rule** — categories `quest | diary | milestone |
    slayer_target | boss`; pins fill slots first; if slots 1–2 share a
    category, slot 3 is the top goal of another category, else top overall;
    fewer than two categories open → plain top three — grill 5.
18. **Ruling: bank persistence signal** — keep the latest BANK container from
    `ItemContainerChanged(95)`; on `WidgetClosed` with `groupId ==
    InterfaceID.BANKMAIN (12)` persist it, stamp `bankAsOf`, request a
    snapshot. A4 reads "persisted within one tick of bank close". Ruling 9's
    "burst on close" premise was wrong; debounce stays — grill 6.
19. **Ruling: threading contract** — panel actions and `ConfigChanged`
    (EDT) re-run the engine on the executor against the cached Snapshot;
    only Refresh/login/bank-close/level-up/quest-change call
    `clientThread.invoke` to rebuild the Snapshot; store I/O runs on the
    executor; first snapshot after login is taken on the first `GameTick`,
    not on `GameStateChanged`; a generation counter discards stale engine
    results — grill 7.
20. **Ruling: diary bit map** — bit N (1-based) of the tier's VarPlayer =
    wiki ordinal N; all 44 tier classes transcribed from Quest Helper into
    `diaries.json`; S2 includes a dev-client check per tier that popcount of
    the KB bit set equals the completed count in the diary tab — grill 8.
21. **Ruling: `generatedAt` = max wiki revision timestamp fetched**, so B1
    byte-identical reruns and B8 both hold — grill 9.
22. **Ruling: ironman-only reqs modelled** — `ironmanOnly: boolean` on skill
    reqs in quests.json/diaries.json; applied for account types 1–6; GE
    excluded for types 1–6 (group irons have no GE) — grill 10.
23. **Ruling: snapshot triggers** — `StatChanged` with level change,
    `VarbitChanged` where `varpId == VarPlayer.QUEST_POINTS` (101) or `varpId` in
    the diary VarPlayer range, bank close, first GameTick after login,
    Refresh. Not every VarbitChanged/inventory change — grill 11.
24. **Ruling: one pinned `runeLiteVersion` (1.12.38) for `client` and
    `runelite-api`**; bump deliberately — grill 12.
25. **Ruling: schema holes** — diary tasks carry `notes[]` (displayed, never
    evaluated) for non-skill reqs like kudos; calc `type` split on comma;
    paths are `kb-build/` and `plugin/src/main/resources/kb/`;
    milestones.json is hand-curated (ticket B1 wording amended) — grill 13.

## Data sources (verified 2026-09-07)

### RuneLite plugin side

- Template: github.com/runelite/example-plugin. Gradle, `compileOnly
  net.runelite:client:latest.release` from https://repo.runelite.net, Java
  release 11, Lombok 1.18.30, `run` task launches `RuneLite.main` with
  `--developer-mode` after `ExternalPluginManager.loadBuiltin(Plugin.class)`.
  `runelite-plugin.properties` declares `plugins=<main class>`.
- `net.runelite:runelite-api:1.12.38` is published standalone (contains
  `Quest`, `Skill`, `QuestState`, `events.*`, `gameval.VarbitID`,
  `gameval.VarPlayerID`), so engine tests can depend on it without the
  full client.
- `Quest.getState(Client)` calls `client.runScript(QUEST_STATUS_GET, id)`,
  which **must run on the client thread**. Same for item names via
  `ItemManager.getItemComposition(id).getName()`. Snapshot collection is
  therefore a `clientThread.invoke(...)` that produces an immutable
  `Snapshot` (ruling 11 stands).
- `Client.getAccountHash()` (from `OAuthApi`) is the per-account key.
- Panel: `PluginPanel` (width 225, wraps in a scroll pane), `NavigationButton`
  builder (`icon`, `tooltip`, `panel`, `priority`), `ClientToolbar.addNavigation`.
- Events (in `net.runelite.api.events`): `ItemContainerChanged(containerId,
  itemContainer)`, `StatChanged(skill, xp, level, boostedLevel)`,
  `VarbitChanged(varpId, varbitId, value)`, `GameStateChanged(gameState)`.
- Persistence precedent: Bank Memory plugin stores Gson JSON per account
  hash via `ConfigManager`; we use a file under `RuneLite.RUNELITE_DIR`
  (ruling 10) because bank snapshots are large.
- **Diary per-task completion**: NOT varbits for most diaries. Each diary
  has two `VarPlayer` bitfields (e.g. `VarPlayerID.VARROCK_ACHIEVEMENT_DIARY
  = 1176`, `..._DIARY2 = 1177`) with bit N = task N in wiki order per tier;
  Karamja is the exception and uses per-task varbits (`VarbitID.ATJUN_*`).
  Tier completion is `Varbits.DIARY_<AREA>_<TIER>`. The bit mapping is not
  on the wiki; it is encoded in Quest Helper's diary helpers (BSD-2) and
  must be transcribed into `diaries.json` and verified in the dev client
  against the in-game diary tab. This corrects ticket A6/B3 wording.

### OSRS wiki side (kb-build)

Semantic MediaWiki and Cargo are gone from the wiki. The replacement is the
**Bucket API**: `api.php?action=bucket&format=json&query=bucket('<table>')
.select(...).where('field','value').limit(5000).offset(N).run()`. Equality
`where` only; 5000 rows per call. Schemas at `Bucket:<name>` pages.

| Need | Source | Shape |
|---|---|---|
| Quest skill reqs, boostable/ironman flags, direct prereq quests | `Module:Questreq/data` (Lua table, 182 quests) via `action=query&prop=revisions&rvslots=main&titles=Module:Questreq/data` | `['Song of the Elves'] = { quests = {...}, skills = { {'Agility',70}, {'Herblore',70,'boostable'} } }` |
| Quest enumeration + items + quest points | Bucket `quest` (229 rows incl. miniquests) for the list; per-quest wikitext `{{Quest details \|items=...}}` and `{{Quest rewards \|qp=N}}` batched 50 titles per `prop=revisions` call | items are `*[[Steel full helm]]`, `*[[Limestone brick]] x 8` |
| Diary tasks + reqs | Per-diary page wikitext: `{\| class="wikitable ... diary-table" data-diary-name data-diary-tier` rows with `{{SCP\|Skill\|N}}` and `{{SCP\|Quest}} Completion of [[X]]` | ordinal in task cell = bit index |
| Training methods per skill | `Module:Skill calc/<Skill>` (exists for all nine planned skills) | `{ name, title, level, xp, materials = { {name, quantity} }, members, type }` |
| Per-recipe boostable/ticks/tools | Bucket `recipe` (`production_json`) | `{materials[], skills[{name,level,experience,boostable}], output}` |
| Item sources | Bucket `dropsline` (drop_json), `storeline` (sold_by, price, stock), `locline` (spawn coords) keyed by item name | verified for Snape grass / Vial of water |
| Item ids | `https://prices.runescape.wiki/api/v1/osrs/mapping` (tradeables, 4662 rows) + Bucket `item_id` dump for untradeables | `{id, name, members, limit, ...}` |
| Etiquette | Descriptive `User-Agent` with contact; serial requests; batch titles; `maxlag=5` | no explicit rate limit |

Cross-check: Quest Helper (BSD-2) encodes the same requirements in Java and
is used only as a validation oracle in kb-build tests, never as a source.
Coverage gap to handle: 182 Questreq entries vs RuneLite `Quest` enum;
B2 fails the build on any enum constant without an entry, listing them.

## Out of scope

As per ticket "Out of scope (v1)" plus: GIM shared storage, boosted-level
tracking, any network call from the plugin, XP-rate/time estimates, GP cost.

## Test approach

- Engine (`engine/`): JUnit 5, fixtures built with small builders
  (`SnapshotBuilder`, `KbBuilder`). Seams = the public methods named in the
  ticket: `GapEngine.gaps(snapshot, kb)`, `Ranker.rank(goals, prefs)`,
  `WhyBuilder.why(goal)`, `FocusPicker.next(gap)`, `RoutePlanner.route(skill,
  from, to, bank, kb)`. One test per AC clause in C/D/E.
- KB loader: one round-trip test that the bundled JSON parses into the model
  and that every `Quest` enum constant has an entry (B2 build-fail rule is a
  test).
- kb-build (TypeScript, Vitest): parser tests on saved wikitext fixtures
  (no network in tests); one snapshot test per output file on fixtures.
- Snapshot/UI: not unit tested; verified manually in the dev client per
  slice with a checklist in the plan (A1–A8 are manual ACs).
- Gates: `./gradlew test` in `plugin/`, `npm test` and `npm run typecheck` in
  `kb-build/`.

## Slicing

S1 → S2 → S3 form the first usable product ("what am I missing for X").
S4 and S5 are independent of each other after S3. S6 last. Each slice is
its own plan section, branch `feat/LOCAL-nta-s<N>-<desc>`, and PR-shaped
commit range on `main` (no remote yet; see open items).

## Open items for the user

Batched into one question at grill time. Candidates: none blocking; all six
ticket questions were ruled above.
