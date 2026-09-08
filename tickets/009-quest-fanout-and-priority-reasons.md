# RL-009 — Computed quest fan-out bonus and priority reasons in Why?

## Story
As a player, I want quests that unblock several other things I want to rank a little higher, with
the Why line saying what they unblock, and I want curated priorities to explain themselves.

## Evidence
Compass adds +24 to a quest that is an unmet prerequisite of other incomplete quests and a
shared-dependency value of `min(1, dependents × 0.12)` (CandidateProviders.java:1818-1822, 1885-
1898; StrategyServices.java:422-449). Its quest-priorities.json pairs each bonus with a reason
("King's Ransom: Unlocks the route to Chivalry and Piety"). Signpost hand-curates hub quests in
priorities.json with a bare number and no explanation.

## Acceptance criteria
1. Ranker computes `dependents` for a quest = number of distinct non-hidden goals (quests,
   diaries, milestones, skill targets' parents) whose gap tree contains that quest; score uses
   `priority + min(2, dependents × 0.5)`; a test shows a 5-dependent quest outranking an otherwise
   equal one and the cap at +2.
2. Why? adds "Unblocks: Desert Treasure I, Lunar Diplomacy, Fremennik Hard" (top three by their own
   rank, "+N more").
3. priorities.json entries may be `{"score": 9, "reason": "..."}` or a bare number; the loader
   accepts both; WhyBuilder prints the reason as its own line when present.
4. Reasons are added for the top 40 curated quests and all 9 diaries, each one factual and checked
   (e.g. no "unlocks Cam Torum" for Perilous Moons).
5. Diagnostics line prints `dependents=` per quest pick.

## Size
S-M. No dependency.
