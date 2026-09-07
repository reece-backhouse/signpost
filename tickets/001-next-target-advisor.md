# RL-001 — Next Target Advisor (RuneLite plugin)

## Story

As an OSRS player (main or ironman), I want a RuneLite side panel that reads my
account state (quests, skills, XP, bank, equipment, diaries, achievements) and
does two things:

1. **Suggest** — on open, tells me the next best thing to focus on for *this*
   account (a skill, quest, diary, slayer target, or boss) with a plain-English
   reason why.
2. **Focus** — lets me pick any goal myself (e.g. Song of the Elves) and shows
   the most effective route to it given my skills and bank: which skill to
   train next, which method, and if I'm short on materials, how and where to
   get them.

Example outputs the plugin should be able to produce:

- Suggest: "Varrock Easy diary — 11/12 tasks done. Missing: *Craft a bowl at
  Barbarian Village*. You have 8 Crafting (need 8) and 0 soft clay. Why: one
  task from a diary reward, everything else is already met."
- Suggest: "Moons of Peril — ready now. Why: Blood Moon set is your biggest
  melee upgrade over Bandos, no requirements missing, Perilous Moons complete."
- Suggest: "Slayer 87 (have 82) — why: unlocks Kraken (trident) and you already
  have 75 Magic and the Zeah diary for Konar."
- Focus: "Song of the Elves — blocked on 70 Herblore (have 61) and Mourning's
  End Part II (not started). Train Herblore first: 340 prayer potions → 66
  using 412 ranarr in bank, but you have 0 snape grass. Get snape grass: Waterbirth
  Island spawns (iron) or GE (main). Then 210 Saradomin brews → 70; you need
  210 toadflax (have 0) — grow from seeds (have 14) or Farming contracts."

## Decisions (made now, override if wrong)

1. **No AI in v1.** The valuable part is the gap analysis ("what am I missing for
   X"), and that must be *correct*. LLMs hallucinate requirements. v1 is a
   deterministic engine over a static knowledge base. AI is a phase-2 feature
   flag that only ever *writes prose over facts the engine already computed*; it
   never decides requirements.
2. **Knowledge base is bundled, not fetched live.** A build-time script pulls
   quest reqs, diary tasks, unlock recommendations from the OSRS wiki API and
   ships them as JSON in the plugin jar. Runtime never scrapes the wiki (rate
   limits, HTML drift, game-thread stalls). Refresh = rebuild.
3. **Bank data is cached from the last bank visit.** RuneLite only exposes the
   bank container while the bank interface is open. We persist a snapshot per
   account on every bank open and mark recommendations as "bank as of <time>".
4. **Ironman awareness is a filter, not a separate engine.** Every "acquire X"
   step carries a `source` (GE, drop, shop, craft). Iron accounts hide GE
   sources and show the next-best.
5. **Personal/sideloaded plugin, not Plugin Hub.** Plugin Hub review would
   block outbound Bedrock calls anyway. Revisit if we want to publish.

## In scope

- Account snapshot: account type, all quest states, all skill levels + XP,
  bank contents (cached), equipment, achievement diary task/tier completion,
  combat achievement tiers.
- Knowledge base (KB): quests (skill + quest + item reqs), diary tasks (reqs,
  items), a curated "milestones" list (armour/weapon upgrades, key unlocks like
  fairy rings, Piety, Ancient magicks, slayer level targets, bosses) with reqs
  and iron/main sources, training methods per skill (XP/action, materials,
  level req), and a materials table (where to get each material, per account
  type).
- Gap engine: for each incomplete goal, compute missing requirements down to
  concrete items/levels, and estimate effort (XP needed, items short).
- Suggest mode: ranked goals, "almost done" first, weighted by a curated
  priority score from the KB (metas). Every suggestion has a "why". Player can
  pin/ignore goals.
- Focus mode: pick any goal; get the ordered plan (next skill, method,
  quantities, materials, where to source what's missing).
- Side panel UI with both modes.

## Out of scope (v1)

- AI/Bedrock (phase 2, see AC group H).
- Live wiki fetches, GE price lookups, GP-cost optimisation ("cheapest per XP").
- Clue scrolls, collection log, pets, minigame points, boss KC tracking.
- Full training planner (time estimates, multi-skill scheduling). Routes are
  "highest XP/action you can afford from bank, then next", not a solver.
- Gathering-skill routes (Mining, Fishing, Woodcutting, Hunter, Farming runs,
  Agility, Thieving, Runecraft, Slayer XP): these get "train N more levels" +
  a wiki link to the wiki's training guide, no material planning.
- Combat-skill routes (Attack/Str/Def/Ranged/Magic/HP): "train N more levels"
  + wiki link. Slayer *level* is a goal target, not a planned route.
- Group ironman shared storage.

## Acceptance criteria

### A. Account snapshot

- **A1** Given I am logged in, when the panel is opened, then it shows my
  account type as one of: Main, Ironman, Ultimate, Hardcore, Group, Hardcore
  Group, Unranked Group (from varbit `ACCOUNT_TYPE`/1777).
- **A2** Given I am logged in, then every `Quest` in the RuneLite enum is
  classified as Finished / In progress / Not started, and the panel shows the
  count (e.g. "Quests: 141/163 complete").
- **A3** Given I am logged in, then all 23 skills report real level and XP
  matching the in-game skills tab exactly.
- **A4** Given I open my bank and close it, then the plugin persists the full
  bank contents (item id + quantity) to disk keyed by account hash within one
  game tick of the bank closing, and the panel refreshes within the F5 budget.
- **A5** Given I have never opened the bank on this account since installing,
  then the panel shows "Bank: unknown — open your bank once" and item-based
  recommendations are suppressed (not shown as "missing everything").
- **A6** Given I am logged in, then diary completion is reported per task (from
  the per-diary varplayer bitfields, or varbits for Karamja) and per tier, and
  matches the in-game diary tab.
- **A7** Given I hop worlds or relog, then the snapshot is rebuilt without
  restarting the client.
- **A8** Snapshot collection never blocks the game thread for more than one
  tick; heavier work (gap analysis) runs off-thread.

### B. Knowledge base

- **B1** A repeatable script (`kb-build/`) fetches from the OSRS wiki API and
  emits `plugin/src/main/resources/kb/{quests,diaries,methods,materials}.json`
  (milestones.json is hand-curated, see B4). Running it twice with no wiki
  changes produces byte-identical output.
- **B2** Every quest in the RuneLite `Quest` enum has a KB entry with: skill
  reqs (level, boostable flag), quest prereqs, required items, and quest points.
  Missing entries fail the build.
- **B3** Every diary task has: tier, description, skill reqs, quest reqs,
  required items, and the varplayer+bit (or varbit) that marks it complete.
- **B4** Milestones are a hand-curated JSON (not scraped) with: name, category
  (melee/range/mage armour, weapon, unlock, prayer, spellbook), reqs, and a
  priority score 1–10. Each item req carries `sources: [GE|drop|shop|craft|
  quest]`. Initial list covers the standard mid/late-game meta progression
  (e.g. Barrows gloves, Fire cape, Piety, fairy rings, Ancients/Lunars, Moons
  of Peril armour, Fighter torso, Dragon defender, Ava's, Void, Slayer helm,
  Toxic blowpipe, Bowfa, Torva/Masori/Ancestral).
- **B5** Milestones include two further categories:
  - `slayer_target`: a Slayer level with what it unlocks (e.g. 87 → Kraken /
    trident, 91 → Cerberus, 93 → Thermy, 95 → Hydra) plus the gear/quest reqs
    to actually do that content.
  - `boss`: a boss with entry reqs (quests, diaries, levels, recommended gear
    tier as a list of acceptable item ids) and what it drops that matters for
    progression (drives priority). Initial list: Barrows, Moons of Peril,
    Vorkath, Zulrah, GWD bosses, DKs, Kraken, Cerberus, Gauntlet/CG, Muspah,
    Duke/Whisperer/Leviathan/Vardorvis, CoX, ToB, ToA.
- **B6** Training methods: for each planned skill (Herblore, Prayer, Crafting,
  Smithing, Cooking, Fletching, Construction, Magic-via-alch/enchant,
  Firemaking) a table of `(method, levelReq, xpPerAction, materials[],
  outputs[])`, scraped from wiki calculators where available and hand-checked.
- **B7** Materials table: for every material referenced in B6 and every quest/
  diary item, `sources[]` with `{type: GE|shop|drop|spawn|gather|craft|quest,
  where: "Waterbirth Island", detail, accountTypes: [main|iron|all]}`. `craft`
  sources point back at a B6 method so the engine can chain (toadflax potion
  (unf) ← toadflax + vial of water).
- **B8** KB has a `version`/`generatedAt` field shown in the panel footer.

### C. Gap engine

- **C1** For each incomplete quest, diary task, and milestone, the engine
  produces a `Gap` = list of unmet requirements, each typed as one of:
  `SkillLevel(skill, have, need, xpDelta, boostable)`,
  `QuestPrereq(quest, state)`, `Item(id, have, need, sources)`,
  `DiaryTier`, `CombatAchievementTier`.
- **C2** Quest prereqs are resolved transitively: if SotE needs Mourning's End
  II which needs Mourning's End I, all three appear in the chain with the
  *deepest incomplete* one flagged as "start here".
- **C3** Item `have` counts include bank + inventory + equipment. A missing bank
  snapshot (A5) sets `have = unknown`, not 0.
- **C4** Given an ironman account, `Item.sources` excludes `GE`; if no other
  source exists the item is flagged "iron: must obtain — see wiki" with a link.
- **C5** Boostable skill reqs within the max boost range for that skill are
  shown as "boostable from N" rather than as a hard gap.
- **C6** For a skill gap in a planned skill (B6), the engine proposes a route
  from bank contents: walk methods from highest XP/action I meet the level for
  downward, take as many actions as my materials allow, advance the simulated
  level (unlocking higher methods), repeat until the XP delta is covered or
  materials run out (e.g. "prayer pots ×340 → 66, brews ×210 → 70"). Output is
  an ordered list of `(method, count, fromLevel, toLevel, materialsUsed)`.
- **C7** When the route cannot cover the XP delta, the engine reports the
  uncovered XP and, for the *best* method available at the current simulated
  level, the material shortfall as `(item, have, need)` plus that item's
  sources from B7 filtered by account type (C4). Craft-type sources recurse
  one level (e.g. "need 210 toadflax potion (unf): make from 210 toadflax
  (have 0) + 210 vial of water (have 1200); toadflax: grow from toadflax seed
  (have 14) / Tithe farm / Sorceress's garden").
- **C8** Boss goals with a gear req list are satisfied if *any* acceptable item
  id is in bank/equipment; otherwise the gap shows the cheapest-tier
  acceptable item and its sources.
- **C9** Engine is a pure function `(Snapshot, KB) -> List<Goal>` with unit
  tests covering: transitive quest chains, iron source filtering, unknown
  bank, boostable reqs, the herblore route above, the toadflax shortfall
  recursion, and a boss gear "any of" match.

### D. Suggest mode (ranking + why)

- **D1** Goals are ranked by `priority × closeness`, where closeness is
  derived from number of unmet reqs and total XP delta. Ties broken by
  priority. Exact formula lives in one function with tests.
- **D2** Fully-met goals (all reqs satisfied but not done) are always ranked
  above goals with gaps and labelled "Ready now".
- **D3** Every suggestion carries a deterministic "why" built from templates
  over the ranking inputs, e.g. "1 requirement away", "unlocks X, Y", "biggest
  {melee|ranged|magic} upgrade over what you own", "you already have the
  materials in bank". No free text outside the KB's curated `unlocks` /
  `reason` fields.
- **D4** The panel leads with exactly three suggestions ("Pick one"), spanning
  at least two categories (e.g. not three diaries) unless fewer categories
  have any incomplete goals.
- **D5** Given I click "Do this" on one of the three, it becomes my active
  focus and the panel switches to Focus mode (E) for it.
- **D6** Given I click "Not now" on one of the three, it is snoozed and the
  next-ranked goal takes its slot immediately. Snoozed goals stay hidden for a
  configurable period (default 7 days) *or* until their gap changes (a req is
  met or a new one appears), whichever comes first. Snoozes persist across
  sessions. A "Snoozed" section lists them with "bring back".
- **D7** Given I click "Ignore" on a goal, it is hidden permanently until I
  restore it from an "Ignored" section. Ignore is distinct from snooze.
- **D8** Given I click "pin", the goal stays at the top regardless of score.
- **D9** Below the three, the panel shows the next 10 by default with "show
  more"; each row also has "Do this" / "Not now" / "Ignore".

### E. Focus mode (pick a goal, get the route)

- **E1** A searchable picker over every KB goal (quests, diary tiers/tasks,
  milestones incl. slayer targets and bosses). Selecting one sets it as the
  active focus, persisted per account.
- **E2** The focus view shows the full requirement tree (C1/C2) with met items
  greyed out and unmet items expanded.
- **E3** "Do this next" picks one unmet requirement using this order: (a) any
  quest prereq whose *own* requirements are fully met, else (b) the skill gap
  with the smallest XP delta that has a bank-covered route (C6), else (c) the
  smallest XP delta overall, else (d) the first missing item. The rule lives
  in one function with tests.
- **E4** For the chosen skill, the view shows the C6 route as an ordered
  checklist with quantities, and the C7 shortfall + sources when the route
  falls short.
- **E5** For a chosen missing item, the view shows its B7 sources filtered
  for my account type, with the wiki link.
- **E6** When the focus goal's requirements change (level up, quest done, bank
  close), the view recomputes without me re-selecting.
- **E7** A "Clear focus" action returns to Suggest mode.

### F. Panel UI

- **F1** A RuneLite side panel with: account header (type, quest count, total
  level, bank-as-of time), a mode switch (Suggest / Focus), and the active
  mode's content.
- **F2** In Suggest mode: the "Pick one" three (D4) with why and the
  Do this / Not now / Ignore actions, then the ranked list. Each goal expands
  to show its gap grouped as Skills / Quests / Items, with have/need columns.
- **F3** Every goal, method and material has a wiki link that opens in the
  system browser.
- **F4** A "Refresh" button re-runs the engine; auto-refresh runs on login,
  bank close, quest completion, and level up (via RuneLite events).
- **F5** Panel renders in under 200 ms after the engine returns; engine runs
  under 1 s for a maxed-quest account on a typical laptop.

### G. Persistence & privacy

- **G1** Bank snapshot, snoozes, ignores, pins and active focus are stored under
  RuneLite's per-profile config or a file in
  `~/.runelite/next-target/<accountHash>.json`.
- **G2** No network calls are made in v1. Nothing about the account leaves the
  machine.

### H. Phase 2 — AI narrative (feature-flagged, off by default)

- **H1** A config toggle "Use AI summary (AWS Bedrock)" with fields: region,
  model id (default: latest Claude Sonnet on Bedrock), and a note that AWS
  credentials come from the standard credential chain (env / `~/.aws`).
- **H2** When enabled, the plugin sends *only* the engine's structured output
  (top N goals + gaps, account type, skill levels) — never raw bank dumps —
  and receives a ≤150-word "what to focus on this week and why" paragraph
  shown above the list.
- **H3** The AI text cannot introduce goals or requirements not present in the
  engine output; the prompt instructs this and the response is shown with a
  "generated" label.
- **H4** Bedrock failure (no creds, throttled, timeout > 10 s) degrades to v1
  behaviour with a one-line warning; never blocks the panel.
- **H5** AI calls happen only on explicit "Refresh" click, not on every event,
  and are debounced to at most one per 5 minutes.

## Open questions

1. Is this ever going to the Plugin Hub? If yes, decision 5 flips and AI has
   to go (or move behind a user-run proxy).
2. Milestone list (B4): do you want me to draft the initial 30–40 entries
   from the wiki's "optimal quest guide" + "armour progression" pages, or will
   you curate it? Draft is faster; your curation is more "your meta".
3. Bedrock: fine with the AWS SDK v2 `bedrockruntime` dependency (~10 MB of
   jars) inside the plugin, or would you rather a tiny local HTTP shim so the
   plugin stays lean?
4. Planned skills (B6): Herblore, Prayer, Crafting, Smithing, Cooking,
   Fletching, Construction, Magic, Firemaking. Confirm that list for v1;
   everything else gets "train N more levels" + wiki link.
5. Boss list (B5): happy with the initial list, or do you want it trimmed to
   what's relevant to your current account so the KB stays small?
6. The materials table (B7) is the biggest curation job. Proposal: only
   populate sources for materials that a B6 method or a quest/diary item
   actually references (~150 items), and only for the top method per skill
   tier. Anything else gets a bare wiki link. OK?

## Tech notes (verified against runelite master)

- Account type: `client.getVarbitValue(Varbits.ACCOUNT_TYPE)` (1777; gameval
  name `VarbitID.IRONMAN`). 0 normal, 1 iron, 2 ultimate, 3 hardcore, 4 group,
  5 hardcore group, 6 unranked group. `Client.getAccountType()` exists but
  prefer the varbit.
- Quests: `Quest` enum, `quest.getState(client)` → `QuestState`
  {NOT_STARTED, IN_PROGRESS, FINISHED}.
- Skills: `client.getRealSkillLevel(Skill)`, `client.getSkillExperience(Skill)`
  (also array forms `getRealSkillLevels()` / `getSkillExperiences()`).
- Containers: `client.getItemContainer(InventoryID.BANK|INVENTORY|EQUIPMENT)`;
  subscribe to `ItemContainerChanged` and snapshot when id == BANK.
- Diaries: tier varbits `Varbits.DIARY_<AREA>_<TIER>`. Per-task completion
  is a bit in one of two `VarPlayer` ints per diary (e.g.
  `VarPlayerID.VARROCK_ACHIEVEMENT_DIARY` 1176 / `_DIARY2` 1177); Karamja
  alone uses per-task varbits (`VarbitID.ATJUN_*`). Bit mapping transcribed
  from Quest Helper (BSD-2). Combat achievement tiers:
  `Varbits.COMBAT_ACHIEVEMENT_TIER_*`.
- Events to hook: `GameStateChanged` (LOGGED_IN), `ItemContainerChanged`,
  `StatChanged`, `VarbitChanged` (quest/diary), `ChatMessage` for "Congratulations,
  quest complete" is unnecessary — varbits cover it.
- Wiki: `https://oldschool.runescape.wiki/api.php` (MediaWiki) for build
  script; set a descriptive `User-Agent` per wiki policy.

## Phasing

1. A + B1–B3 + C1–C5 + F1–F3 + E1–E2 — "what am I missing for X" (Focus
   mode with the requirement tree, no routes yet).
2. B4–B5 + D — Suggest mode with why (quests, diaries, milestones, slayer,
   bosses).
3. B6–B7 + C6–C8 + E3–E7 — routes and material sourcing in Focus mode.
4. H — AI narrative.
