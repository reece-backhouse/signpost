# RL-006 — Skilling untradeables and outfits as milestones

## Story
As an ironman, I want the skilling untradeables that define the mid game (coal bag, herb sack,
seed box, rune pouch, bonecrusher, plank sack...) to appear as goals with a stage and a "how", so
the plugin tells me to go get a coal bag before it tells me to mine 10k coal.

## Evidence
Compass's progression-objectives.json (43 entries) ties an untradeable or outfit to the method it
speeds up (`{"id":"objective:coal-bag","methodId":"mining_mlm","type":"UNTRADEABLE"}`). Signpost's
milestones.json has graceful, fighter torso, void and slayer helm only. Every item here is
bank-detectable via `ownedIf` ids and acquired from one activity.

## Scope
Add to milestones.json (stage 1-2, category milestone, `ownedIf` with all variant ids,
`obtainedFrom`, `reason`, `recommended` skills where the activity needs them): coal bag, gem bag,
herb sack, seed box, rune pouch, bonecrusher, ash sanctifier, plank sack, fish barrel, tackle box,
prospector outfit, pyromancer outfit, angler outfit, farmer's outfit, Raiments of the Eye, lumberjack
outfit, rogue equipment, and the Kandarin/Ardougne cloak-type diary rewards that are already
diary goals are NOT duplicated.

## Acceptance criteria
1. Each entry has: at least one item id, every in-game variant (e.g. open/closed coal bag, all
   outfit pieces for sets with `gearOwnedMin` = full set), an `obtainedFrom` activity, a one-line
   `reason` naming the method it accelerates, and correct skill requirements (spot-checked against
   the wiki; wrong data fails review).
2. Owning the item (bank, equipment, inventory, RL-003 group storage) removes the goal; owning some
   outfit pieces shows "2/4 pieces" in the gap line.
3. Ranking: these goals score as milestones with priority 6-7 so a coal bag outranks a random
   diary task at the same closeness; the pick-3 category rule still applies.
4. Why? for a method-linked milestone can say "speeds up Mining (your next Mining target)" when a
   skill target in the same skill exists.
5. KB load validation passes; the milestones test asserts the new entries' ids resolve in
   materials.json and no two entries share an item id.

## Size
M (curation + one test). No code dependency.
