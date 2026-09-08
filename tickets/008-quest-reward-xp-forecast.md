# RL-008 — Quest reward XP in the knowledge base; skill targets account for it

## Story
As an ironman, when Signpost suggests "70 Herblore", I want it to know that quests I can already
do will hand me a chunk of that XP, so it tells me to do those quests first instead of grinding,
and the Why says how much they cover.

## Evidence
Compass sums reward XP of not-yet-done quests whose requirements are met and discounts manual
training by coverage (StrategyServices.java:1464-1489, 190-261; quest-knowledge.json has rewardXp
for 124 quests, e.g. Waterfall Attack/Strength 13,750, Desert Treasure Magic 20,000). Signpost's
quests.json has requirements only; SkillTargetSynthesiser uses raw xpDelta.

## Scope
kb-build: fetch each quest's reward XP (fixed skill XP and choice lamps as `lampXp` with allowed
skills) from the wiki quest page rewards and the quest bucket, emit `rewards: {xp: {SKILL: n},
lamps: [{xp, skills[]}]}` in quests.json. Engine: for a skill target, compute "available quest XP"
= sum of rewards in that skill from quests that are not complete and whose own requirements are
met now; subtract from xpDelta for closeness; WhyBuilder line "Quests you can do now give 27,500
Attack xp: Waterfall Quest, Tree Gnome Village". Route: a route may begin with "Do quest X (+13,750
xp)" steps before methods.

## Acceptance criteria
1. quests.json carries rewards for every quest with XP rewards on the wiki (target ≥ 120 quests);
   a kb-build test parses a fixture page with fixed XP and a lamp; generation is deterministic.
2. A skill target whose remaining XP is fully covered by ready quests is not synthesised as a
   grind target; instead the covering quests rank up (test: fixture account 1 xp short of 40
   Attack with Waterfall ready).
3. Partial coverage reduces xpDelta and adds the Why line naming the quests, in reward-size order,
   capped at three names plus "+N more".
4. Lamps count only when the target skill is an allowed choice, and never double-count a lamp
   across two skill targets in the same advice (first-come by rank).
5. RoutePlanner emits quest steps before method steps with the XP credited to the simulation;
   detail view renders them with the quest icon and wiki link.
6. Diagnostics line per skill target shows `questXp=` so live behaviour can be checked.

## Size
M. No dependency (kb-build needs network to regenerate).
