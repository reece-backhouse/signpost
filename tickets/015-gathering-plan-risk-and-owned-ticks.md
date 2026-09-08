# RL-015 — Gathering plan risk flags, wilderness opt-in, owned-item ticks

## Story
As a hardcore or risk-averse ironman, I want wilderness gathering loops hidden unless I opt in,
every plan to say its risk, and the items a plan asks me to bring ticked off when I already have
them.

## Evidence
Compass resource-sources.json carries per-route `wilderness`/risk flags and the config has a
wilderness opt-in (OsrsStrategistConfig.java); variable-method-guidance.json lists "observed"
items that the engine substitutes with what the bank holds. Signpost's gathering.json has neither
a risk field nor item ids for `requires.items`.

## Acceptance criteria
1. gathering.json plans and alternatives gain `risk: none|wilderness|hardcore-unsafe` (default
   none) and `requires.items` entries may be `{name, id}`; kb-build resolves names to ids from
   materials.json and fails on unknown names.
2. Config "Include wilderness plans" default off; wilderness plans are excluded from offers when
   off, and hardcore accounts (HCIM/HCGIM) exclude `hardcore-unsafe` plans always; a note in the
   shortfall says "1 wilderness plan hidden (enable in settings)".
3. The plan's items line renders each item with an icon and a green tick when owned (bank,
   inventory, equipment, RL-003 group storage), grey otherwise.
4. Every existing plan is reviewed and flagged where it enters the wilderness (at least Wine of
   Zamorak and any Chaos altar step); tests cover the exclusion and the tick rendering.

## Size
S. Better after RL-003.
