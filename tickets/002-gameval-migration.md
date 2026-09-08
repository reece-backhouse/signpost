# RL-002 — Migrate account reads to gameval ids; fix combat-achievement tier semantics

## Story
As the maintainer, I want every varbit, varp and container read to use RuneLite's `gameval`
constants (`VarbitID`, `VarPlayerID`, `InventoryID` ints) so the plugin compiles without
deprecation warnings, survives the removal of the old enums, and reads combat-achievement tiers
correctly.

## Evidence
Compass finished this migration (no `Varbits.`/`VarPlayer.` references remain in its source) and
reads CA tiers as `VarbitID.CA_TIER_STATUS_* >= 2` (LiveReaders.java:342-372). Signpost still uses
`Varbits.ACCOUNT_TYPE`, `Varbits.DIARY_*`, `Varbits.COMBAT_ACHIEVEMENT_TIER_*`,
`VarPlayer.QUEST_POINTS`, `InventoryID.<enum>.getId()` (SnapshotCollector.java:26-33, 62-63, 102,
116; DiaryTier.java; NextTargetPlugin.java:327, 340) and treats CA tier `!= 0` as complete, which
may count "claimable but unclaimed" as done.

## Scope
Replace every deprecated read with its gameval equivalent: `VarbitID.IRONMAN`,
`VarbitID.<REGION>_DIARY_<TIER>_COMPLETE` (Karamja: `ATJUN_EASY_DONE`, `ATJUN_MED_DONE`,
`ATJUN_HARD_DONE`), `VarbitID.CA_TIER_STATUS_*`, `VarPlayerID.QP`, `InventoryID.INV`, `WORN`,
`BANK`. Keep the client-thread guard in SnapshotCollector.

## Acceptance criteria
1. `./gradlew compileJava` emits no deprecation warnings from `dev.reece.nta`.
2. No reference to `net.runelite.api.Varbits`, `net.runelite.api.VarPlayer` or the
   `InventoryID` enum `.getId()` remains under `src/main`.
3. CA tier N is "complete" only when its status varbit value means claimed/complete; the exact
   value semantics are verified in the dev client against an account with at least one tier
   claimed and one unclaimed, and recorded in a code comment with the observed values.
4. Diary tier completion for every region and tier, account type, quest points and the three
   containers produce the same snapshot as before on the same account (dev-client diary self-check
   still reports 48 tiers, mismatches unchanged or fewer).
5. Existing snapshot/diary tests pass unchanged; a test pins the Karamja special-case names.

## Size
S. No dependencies. Prerequisite for RL-003, RL-004, RL-005.
