# RL-004 — Read slayer state; slayer reward unlocks as goals

## Story
As a mid-game ironman, I want Signpost to know my current slayer task, master, streak and points,
so it can suggest slayer reward unlocks (slayer helmet, Bigger and Badder, boss tasks) as concrete
goals with a points gap, and show my current task in the header.

## Evidence
Compass reads task and count from `VarPlayerID.SLAYER_TARGET` / `SLAYER_COUNT`, points from
`VarbitID.SLAYER_POINTS`, master from `VarbitID.SLAYER_MASTER`, streak from
`VarbitID.SLAYER_TASKS_COMPLETED` (`SLAYER_WILDERNESS_TASKS_COMPLETED` for Krystilia), resolves the
task name through `client.getDBRowsByValue(DBTableID.SlayerTask...)` so no name table is
maintained, and reads 18 reward-unlock varbits (`SLAYER_HELM_UNLOCKED`, `SLAYER_RING_UNLOCKED`,
`SLAYER_UNLOCK_BOSSES`, `SLAYER_UNLOCK_SUPERIORMOBS`, `SLAYER_LONGER_*` ...) (LiveReaders.java:
1292-1344, 1407-1410, 1458-1482; DomainTypes.java:1992-2035). Signpost reads nothing about slayer.
Compass's 861-line task/skip/block state machine is out of scope.

## Scope
Snapshot fields: current task name, remaining count, master, streak, points, set of unlocked
rewards. New gap type `SlayerPointsGap(have, need)` and `SlayerUnlockGap`. Milestone entries in
milestones.json for: slayer helmet (Malevolent masquerade 400), Bigger and Badder 150, Like a boss
200, Slayer ring 300, superior monsters unlocked (Bigger and Badder is the id), reward point
shop items are NOT goals. Header line "Slayer: 87 Aberrant spectres left (Duradel), 1,240 pts".

## Acceptance criteria
1. On login, the snapshot contains task name, count, master, streak, points and unlocks; the task
   name comes from the client's DB table lookup, not a bundled list; a fixture test covers a boss
   task (`SLAYER_TARGET_BOSSID`).
2. A slayer-unlock milestone is "Ready now" when points ≥ cost and the required Slayer level (if
   any) is met; its gap shows "Slayer points 640/400" style have/need; once the unlock varbit is set
   the goal drops out like a finished quest.
3. Why? lines name the unlock's effect from the milestone `reason` ("Slayer helmet: +16.67% damage
   on task, needs Malevolent masquerade 400 pts and 55 Crafting").
4. The header shows the current task line when a task is active and nothing when none.
5. Points and task changes trigger a re-snapshot only on the relevant varbits (no per-kill re-rank).
6. Engine purity, client-thread reads, and NoNetworkTest unchanged; five new milestone entries
   validate at KB load.

## Size
M. Depends on RL-002. Explicitly excludes task skip/block advice and Mortimer offers.
