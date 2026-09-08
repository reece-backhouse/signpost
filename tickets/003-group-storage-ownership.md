# RL-003 — Group ironman shared storage counts as owned

## Story
As a group ironman, I want gear and materials in the group's shared storage to count toward
"Ready now", skill-target coverage and shortfalls, so I stop being told to grind something a
teammate already banked for the group.

## Evidence
Compass subscribes `ItemContainerChanged` for `InventoryID.INV_GROUP_TEMP`, caches it with a
5-minute freshness window and sums it into ownership when the user opts in
(OsrsStrategistPlugin.java:315-335, LiveReaders.java:757-769, ItemsState.java:16, 73-79,
ItemIndex.java:85-90). It never persists it. Signpost has no group storage read; the only
workaround is the manual "Own it" per goal.

## Scope
Cache the shared-storage container the same way as the bank (latest copy on change, persisted on
interface close with an `asOf` timestamp), fold it into `GapEngine.isOwned` and the simulated bank
used by RoutePlanner/ShortfallResolver, and label the source in the detail view. Add a `version`
field to `AccountData` at the same time.

## Acceptance criteria
1. Opening and closing group storage on a GIM account stores its contents under the account's
   JSON with `groupStorageAsOf`; a non-GIM account never writes the field.
2. A gear item present only in group storage satisfies `gearOwnedAny`/`gearOwnedMin` for a
   milestone; a test proves "Ready now" flips from false to true when the item moves from nowhere
   to group storage.
3. Materials in group storage are added to the simulated bank for routes and shortfalls; a route
   step or shortfall line whose quantity comes partly from group storage says so ("in group
   storage: 200").
4. The header bank line shows "Group storage: as of HH:MM" beside the bank line, and "Group
   storage: not seen" when unknown; unknown storage is treated as empty for readiness (same rule as
   the bank) and the WhyBuilder note says it hasn't been seen.
5. A config toggle "Count group storage" (default on) disables the read and the ownership
   contribution without deleting the cached data.
6. `AccountData` gains `version = 2`; loading a version-1 file upgrades it with empty group storage
   and a test covers the upgrade.
7. Engine stays pure; all container reads stay on the client thread; existing bank tests pass.

## Size
M. Depends on RL-002.
