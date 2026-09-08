# RL-014 — Per-slot gear ladders and "next upgrade" goals (needs spec)

## Story
As a player, I want Signpost to say "your next melee upgrade is a Dragon defender (Warriors'
Guild), then a Fighter torso" per slot and style, from what I actually own, so gear progression is
a goal like any other.

## Evidence
Compass gear-progression.json (17 ladders: style × F2P/BUDGET/MIDGAME/HIGH_END/BIS) lists slots as
prose ("Neitiznot helm, Fighter torso, Barrows legs, Dragon defender, Barrows gloves, Dragon
boots, Fury, Fire cape"); the BIS rows are hedged filler. Signpost has per-milestone `gearTier` and
`gearOwnedAny` but no per-slot ladder, so it cannot say what the next upgrade is.

## Scope (to be grilled before implementation)
A curated `gear-ladders.json`: for melee, ranged, magic × budget and midgame tiers, an ordered
list per slot of item ids (with variants) and the goal that provides each (milestone id, quest id,
method, or shop). Engine: a `GEAR_UPGRADE` goal per (style, slot) whose target is the first ladder
item not owned above the best owned one; ranks as a milestone; Why says "replaces Rune platebody".

## Acceptance criteria
1. Ladders for six (style, tier) combinations, every entry an item id with variants and a provider
   reference that exists in the KB; validated at load.
2. Owning any item at or above a rung marks lower rungs satisfied (test: Bandos chestplate owned →
   no Fighter torso upgrade suggested).
3. At most one GEAR_UPGRADE goal per style is suggested at a time (the cheapest missing rung);
   pick-3 treats it as the milestone category.
4. The detail view of a gear upgrade shows the provider goal's gaps inline (e.g. Warriors' Guild
   tokens for a defender) using the existing focus path.
5. Ironman rule: rungs whose only source is the GE are skipped for iron accounts.

## Size
L. Spec and grill first. Depends on RL-006/RL-007 conventions for `ownedIf`.
