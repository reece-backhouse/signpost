# RL-005 — Rune pouch contents and prayer/spellbook unlocks in the snapshot

## Story
As a player, I want runes in my rune pouch to count as owned, and Rigour, Augury, Preserve and my
current spellbook to be read from the game, so shortfalls don't tell me to gather runes I carry
and boss readiness doesn't guess at prayers.

## Evidence
Compass reads `VarbitID.RUNE_POUCH_TYPE_1..4` + `RUNE_POUCH_QUANTITY_1..4` and maps type to item id
via `client.getEnum(EnumID.RUNEPOUCH_RUNE)` (LiveReaders.java:1056-1068, 1118-1139), plus
`VarbitID.SPELLBOOK`, `PRAYER_RIGOUR_UNLOCKED`, `PRAYER_AUGURY_UNLOCKED`, `PRAYER_PRESERVE_UNLOCKED`
(LiveReaders.java:392-396). Signpost has neither; our rigour/augury milestones are inferred from
items only.

## Acceptance criteria
1. When a rune pouch (any variant id) is in inventory, its four slots are added to the ownership
   counts and to the simulated bank by item id; a test covers pouch present/absent and empty slots.
2. Snapshot carries `rigour`, `augury`, `preserve` booleans and the current spellbook; the existing
   prayer milestones are Met when the varbit says unlocked, regardless of scroll in bank.
3. Boss recommended profiles may list a prayer requirement (`prayers: ["Rigour"]` in
   milestones.json); a missing one renders as a gap "Rigour: not unlocked" with the milestone
   link; KB validation rejects unknown prayer names.
4. Lunar spellbook access (from RL-007's milestone) is Met when the spellbook varbit reads Lunar or
   the quest is finished.
5. Tests for the enum-based rune id mapping using a stubbed `Client`; engine untouched except the
   new Met/gap.

## Size
S. Depends on RL-002.
