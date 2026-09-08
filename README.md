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

- **Three picks** spanning categories, ranked by priority and how close you are. Pin one, snooze
  one ("Not now") or ignore it, and the next best moves up.
- **Why?** under each card: "Stats met: Farming 49, Herblore 57 | Missing: 70 Herblore" style
  lines, with the gear you own counted against a boss's recommended set.
- **Skill targets** such as "70 Herblore, needed for Song of the Elves", offered only when your
  bank can actually get you there.
- **Detail view**: click any skill gap to see the route (which potions, how many, xp and levels
  per step, item icons that open the wiki). If the bank falls short it names the fastest method
  to finish, what you still need, and step-by-step gathering loops (Taverley blue dragon scales,
  herb farm runs, snape grass at Waterbirth...), filtered to steps your levels and quests allow.
  When the fastest method needs something you can't obtain yet, a fully gatherable alternative is
  offered too.
- **Search** the current suggestions (any ranked or "later" quest, diary, boss, milestone or
  skill target) to focus one directly.
- **"Own it" / "Done it"**: mark a milestone as already owned, or a boss as done, so it drops
  out of the suggestions.

## How it decides

Score = priority × closeness. Priority is per goal (curated for quests, diaries and milestones);
closeness falls with unmet requirements and xp to go. Goals are tiered: pinned, ready now, the
rest, then "later" for anything more than one stage above your account. Your stage (1-4) is
estimated from gear, combat, total level and quest count, and a boss is "Ready now" only when
both its entry requirements and its recommended gear/stat profile are met.

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
profiles, priorities and gathering plans are hand-curated in the same directory. Rebuild with:

```
cd kb-build && npm ci && npm run build-kb -- quests|diaries|methods|materials|expand-milestones|gathering
```

## Building

```
./gradlew test        # engine, knowledge-base and Swing render tests
./gradlew run         # RuneLite with the plugin loaded (developer mode)
./gradlew shadowJar   # standalone jar under build/libs/ for sideloading in developer mode
```

Java 11 source level; the build resolves the latest RuneLite release, as the Plugin Hub requires.

## Licence

Code: BSD 2-Clause (see `LICENSE`). Bundled wiki-derived data: CC BY-NC-SA 3.0, attribution to
the Old School RuneScape Wiki and its contributors.
