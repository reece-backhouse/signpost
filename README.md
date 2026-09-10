# Signpost

**Points your account at its next best goal.**

Signpost reads your account (quests, skills, bank and equipment, achievement diaries) and
suggests the three things most worth doing next: a quest, a diary tier, a boss or milestone, or
a plain skill target like "70 Herblore". Every suggestion comes with a **Why?** that names what
you meet and what you're missing, and a detail view that turns a skill gap into a step-by-step
route from the materials already in your bank, with gathering plans for whatever is short.

It is built for accounts that don't want to be told to do the Theatre of Blood at 1,400 total:
suggestions are ranked for your account's stage, bosses count as ready only when your gear and
stats meet a curated profile, and ironman rules (no Grand Exchange) apply automatically.

## What you get

- **Three picks** ranked by progression value and how close you are, with category variety as a
  tie-break. Pin one, snooze one ("Not now") or ignore it, and the next best moves up.
- **Why?** under each card: "Stats met: Farming 49, Herblore 57 | Missing: 70 Herblore" style
  lines, with owned equipment satisfying each required combat role.
- **A concrete reason to revisit a boss**: remaining rewards exclude owned items, tracked
  upgrades and consumed prayer-scroll unlocks, including enabled group storage. A green-logged
  boss returns only for a known unfinished combat achievement: its name, tier, points and full
  task instructions lead the explanation. Unknown achievement data is not treated as unfinished.
  Green-logged bosses with no known remaining task—and their completed drop grinds—drop out.
  Collection completion is not required for replacement coverage: Bandos chestplate and tassets
  exclude their Blood Moon counterparts even when uncollected. The same curated reward relationships
  filter standalone drop milestones and gear-ladder suggestions, counting partial sets piece by piece.
  This does not claim ownership of the replaced item or satisfy crafting ingredients.
  A shared equipment comparison also powers boss readiness: every required role needs a valid
  item or tracked replacement, not an arbitrary count of unrelated items. Combat styles remain
  distinct; melee gloves do not replace ranged or magic gloves.
  Reward explanations name the gain and existing equipment it improves. Situational alternatives
  stay separate from primary upgrades: Masori does not universally replace crystal with Bowfa,
  and a Blade of Saeldor is a marginal alternative alongside an existing Noxious halberd.
  Alternate forms of one armour piece cannot complete multiple slots of a set.
- **Skill targets** such as "70 Herblore, needed for Song of the Elves", offered only when your
  bank can actually get you there.
  Ready quest rewards reduce the remaining grind, and observed XP rates provide an ETA once
  enough training samples exist.
- **Detail view**: click any skill gap to see the route (which potions, how many, xp and levels
  per step, item icons that open the wiki). If the bank falls short it names the fastest method
  to finish, what you still need, and step-by-step gathering loops (Taverley blue dragon scales,
  herb farm runs, snape grass at Waterbirth...), filtered to steps your levels and quests allow.
  When the fastest method needs something you can't obtain yet, a fully gatherable alternative is
  offered too.
- **Bring / Where / Do**: the next affordable training batch or actionable gathering loop,
  with item quantities and owned-item ticks. The movable on-screen overlay is optional and off
  by default.
- **Next gear upgrades** across melee, ranged and magic budget/midgame ladders. Owned charged
  and cosmetic variants count; a better tracked item satisfies lower rungs. One upgrade per
  style is picked using readiness/effort ranking, with the provider's requirements in its detail view.
- **Risk-aware gathering**: Wilderness plans require opt-in; hardcore-unsafe plans are never
  offered to hardcore accounts. Owned-item ticks include bank, inventory, equipment and enabled
  group storage.
- **Live unlocks and supplies**: Slayer tasks, points and rewards, prayer/spellbook unlocks, rune
  pouch contents and group storage are read from the account rather than inferred from item names.
- **Search** the current suggestions (any ranked or "later" quest, diary, boss, milestone or
  skill target) to focus one directly.
- **"Own it" / "Done it"**: mark a milestone as already owned, or a boss as done, so it drops
  out of the suggestions.

## How it decides

Score = priority × closeness × remaining progression value. Priority is curated per goal;
closeness falls with unmet requirements and xp to go. Progression weighting favours major gains,
then useful upgrades, then situational alternatives; collection-only rewards are not objectives.
Pins come first, then ready primary progression, the rest, and "later" goals more than one stage
above the account. Stage (1–4) is estimated from gear, combat, total level and quest count.
Boss entry readiness requires both entry requirements and the recommended combat-role/stat profile.

Within the ready/rest tiers, accessible-stage goals come before higher-stage ones, then remaining
gain determines value—not the age of the boss supplying it. Mere readiness does not promote a
situational grind into the ready-primary tier. Useful unlocks, bank-covered training and explicit
combat-achievement tasks retain their value; category diversity only breaks equal-score ties.

Collection-log flags and individual combat-achievement completion are read live on the client
thread. If task metadata or account bits are unavailable, Signpost says so rather than inventing
a task. Missing bank data also produces an ownership check, not a claim that you lack a reward.
Partial/offline bank snapshots preserve confirmed absences separately from unseen items. Unknown
ownership cannot create an automatic grind recommendation, but the goal remains inspectable.

## Privacy and safety

Signpost never makes a network call. It reads game state through the RuneLite API only, and
stores its per-account notes (bank snapshot, snoozes, ignores, pins, focused goal, goals marked
owned) as JSON under
`~/.runelite/next-target/`. Wiki links open in your browser.

## Data

The knowledge base bundled with the plugin (quest and diary requirements, training methods,
material sources, item ids) is generated from the [Old School RuneScape Wiki](https://oldschool.runescape.wiki)
by the scripts in `kb-build/`, and is used under the wiki's
[CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/) licence. Milestone
profiles, priorities and gathering plans are hand-curated. With Node 22 installed, run
`npm ci` once from `kb-build/`, then use these three commands from that directory:

- `npm run build-kb -- quests` regenerates one dataset. Replace `quests` with `diaries`,
  `methods`, `materials`, `expand-milestones`, or `gathering`; for a full refresh, run them
  in that order. Generation writes the bundled JSON and normalizes typographic dashes and
  quotes to ASCII. `expand-milestones` and `gathering` can also add missing materials.
- `npm run kb-check` makes read-only MediaWiki revision queries and groups moved or
  missing source pages by generated file. Files without a manifest are reported as
  `UNPINNED`, not up to date.
- `npm run kb-diff` compares the working-tree KB with `git HEAD`, reporting added and
  removed quests, diary tiers, milestones and gathering plans, plus changed requirement
  lines. It ignores generation metadata and prose-only edits, and does not write files
  or contact the wiki.

Generated files record sorted `sources: [{title, revid, timestamp}]` manifests for the
actual wiki pages and modules fetched, carrying those revisions into dependent datasets.
Bucket queries and the prices API do not expose revision ids, so their results are not
revision-pinned; an empty manifest is not a claim that those sources are unchanged.
Curated files without fetched page sources may be unpinned. The plugin's curated-content
test rejects vague phrases in milestone reasons and notes, gathering steps and notes,
and priority reasons.

## Building

```
./gradlew test        # engine, knowledge-base and Swing render tests
./gradlew run         # RuneLite with the plugin loaded (developer mode)
./gradlew shadowJar   # standalone jar under build/libs/ for sideloading in developer mode
```

After changing package layouts, stop any running dev client before `./gradlew clean run`.
This removes stale compiled classes and resources left by an older build.

Java 11 source level; the build resolves the latest RuneLite release, as the Plugin Hub requires.

## Licence

Code: BSD 2-Clause (see `LICENSE`). Bundled wiki-derived data: CC BY-NC-SA 3.0, attribution to
the Old School RuneScape Wiki and its contributors.
