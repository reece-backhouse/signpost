# RL-007 — POH infrastructure and ability unlocks as goals

## Story
As a mid-game ironman, I want the player-owned-house unlocks that change how I play (restoration
pool, portal nexus, jewellery box, spirit tree, fairy ring, costume room) and the ability unlocks
(Chivalry, Lunar spellbook, Iban Blast, Arceuus book) offered as goals with exact materials, so a
"75 Construction" suggestion has a reason and a shopping list.

## Evidence
Compass infrastructure-milestones.json (12 entries) carries level, prerequisite milestone and
exact materials, e.g. restoration pool: Construction 65, needs superior garden, "5 limestone
bricks, 5 buckets of water, 1,000 soul runes, 1,000 body runes"; jewellery box 81; portal nexus 72;
POH spirit tree 75 Construction + 83 Farming; POH fairy ring 85. Its ability-unlocks.json has
Chivalry (King's Ransom, Prayer 60, Defence 65, Knight Waves). All spot-checked correct by the
review. Signpost has fairy-rings and piety only.

## Scope
milestones.json entries with `requirements.skills`, `requirements.quests`, `requirements.items`
(exact materials with quantities and ids, so ShortfallResolver prices them from the bank),
`prerequisite` milestone ids (new optional field), `reason`, and stage. No POH scanning: the
player marks a built room with "Own it" (already exists); a `note` says so.

## Acceptance criteria
1. Entries: POH access, costume room (42), oak armour case (46), portal chamber (50 + Magic 25 and
   Varrock portal runes), superior garden (65) + restoration pool (65), portal nexus (72),
   jewellery box (81 basic / 91 ornate as two goals), POH spirit tree (75 + Farming 83), POH fairy
   ring (85 + Fairytale II), Chivalry, Lunar spellbook (Lunar Diplomacy), Iban Blast (Underground
   Pass), Arceuus spellbook (Kourend favour note). Each material list matches the wiki and is
   reviewed line by line.
2. `prerequisite` chains resolve: a goal whose prerequisite is not owned shows the prerequisite as
   a gap and the prerequisite goal is what gets suggested first (test: nexus not ready while portal
   chamber unowned).
3. The detail view lists materials as item gaps with icons and shortfalls (existing path), so
   "1,000 soul runes: have 0" appears with sources.
4. Skill targets synthesised from these goals carry the parent ("75 Construction, needed for POH
   spirit tree") like any other.
5. "Own it" removes the goal and its dependants treat it as met; KB validation rejects a
   `prerequisite` id that does not exist.

## Size
M (curation) + S (prerequisite field in engine). No dependency.
