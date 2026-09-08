# Tickets

| Id | Title | Size | Depends on |
|----|-------|------|-----------|
| RL-001 | Next Target Advisor (delivered as Signpost 1.0) | — | — |
| RL-002 | Gameval migration; CA tier semantics | S | — |
| RL-003 | Group storage counts as owned | M | RL-002 |
| RL-004 | Slayer state and reward unlocks as goals | M | RL-002 |
| RL-005 | Rune pouch and prayer/spellbook unlocks | S | RL-002 |
| RL-006 | Skilling untradeables as milestones | M | — |
| RL-007 | POH infrastructure and ability unlocks | M | — |
| RL-008 | Quest reward XP forecast for skill targets | M | — |
| RL-009 | Quest fan-out bonus and priority reasons | S-M | — |
| RL-010 | Bring / Where / Do next step, optional overlay | M | (RL-003) |
| RL-011 | Panel feedback, empty states, progress bars | S-M | — |
| RL-012 | ETA from observed XP rate | M | — |
| RL-013 | KB maintenance: revision pinning, kb-diff, lint | M | — |
| RL-014 | Gear ladders and next-upgrade goals (needs spec) | L | RL-006, RL-007 |
| RL-015 | Gathering plan risk flags and owned ticks | S | (RL-003) |

Suggested order for a mid-game group ironman: RL-002 → RL-003 → RL-006 → RL-007 → RL-011 →
RL-009 → RL-008 → RL-004 → RL-005 → RL-010 → RL-015 → RL-013 → RL-012 → RL-014.

## Source
RL-002 to RL-015 come from a code review of Gielinor Compass (GiggyCash/osrs-strategist at
58c10e2, 2026-09-08). Full reports with file:line evidence are in the private ledger
(`.superpowers/sdd/LOCAL-next-target-advisor/compass-review-{planning,content,ui,readers}.md`).

## Reviewed and deliberately not taken
- Goal / strategy / session mode enums and a recommendation stabiliser: our pin, focus, stage and
  synthesised skill targets cover "path to a goal" without a knob for every mood.
- Ten-layer additive scoring with hand-tuned constants and a −10000 sentinel: our two-constant
  formula is inspectable and tested.
- Keyword-sniffed requirement actionability and substring item-style matching: our typed gaps and
  item ids already give the invariant "unknown never leads".
- Live-inventory PvM readiness (food and prayer potions carried): planners should not say "check
  needed" because you are at the bank; our recommended profiles are bank-verifiable.
- Per-method curated efficient/balanced/relaxed scores: our routes use measured xp per action on a
  simulated bank.
- The 861-line slayer task skip/block state machine and Mortimer offers (RL-004 takes only state
  reading and unlock goals).
- Training fatigue and post-completion variety rotation, provider deduplication.
- A progress tab with session XP charts (duplicates the XP Tracker); RL-012 keeps only the ETA.
- A details overlay that duplicates the sidebar; feedback with no per-item undo; a restricted-build
  auto-detector that silently hides skills.
- Farming patch state and timers (Time Tracking plugin does it), transport catalog (ours is
  data-driven already), clue step decoding via core plugin internals, `@PluginDependency` on core
  plugins, numeric string-table player text, RS-profile config persistence with silent drops.
