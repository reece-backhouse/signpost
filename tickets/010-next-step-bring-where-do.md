# RL-010 — "Bring / Where / Do" for the next step, with an optional in-game overlay

## Story
As a player about to do the next step, I want a one-glance block of what to carry, where to go and
what to do, in the detail view and optionally on the game canvas, because I'm looking at the game
while doing it, not the sidebar.

## Evidence
Compass renders METHOD / BRING / WHERE / DO under its single card (Presentation.java:23-70) and an
optional movable MethodGuidanceOverlay with the same block (Overlays.java:11-84, default off).
Signpost's detail view has routes and gathering steps but no summary of the immediate action.
Gathering plans already carry `requires.items` and ordered steps; training methods carry
materials but no location.

## Scope
KB: optional `location` (short text) and `bring` (item names or ids) per method in methods.json,
curated for the ~60 methods that appear in routes for Herblore, Prayer, Crafting, Fletching,
Smithing, Cooking, Construction, Farming (a `method-guidance.json` overlay keyed by method name
rather than editing the generated file). Engine: `NextStep` gains `bring`, `where`, `do` strings
derived from the plan/method (pure). UI: a bordered block at the top of the detail view; overlay
panel `SignpostOverlay` off by default, movable, same three lines plus the goal name.

## Acceptance criteria
1. For a gathering-plan next step, BRING = the plan's items with owned ones ticked (bank/inventory),
   WHERE = the plan's first location sentence, DO = the first unmet step; for a method step, BRING =
   materials × count for the step, WHERE = curated location or "any bank", DO = "Make N <output>".
2. The block renders inside the panel width with icons for BRING items (existing Icons helper) and
   ASCII-only text.
3. Overlay: config toggle "Show next step on screen" default off; when on, an OverlayPanel shows the
   goal name and the three lines, is movable, respects RuneLite's overlay font, and re-renders only
   when Advice changes (no per-tick work).
4. Curated `method-guidance.json` validates at load: every key names an existing method; missing
   guidance falls back to "any bank" rather than failing.
5. Tests: NextStep derivation for both step kinds; render smoke test of the block; overlay renders
   with a stub Graphics without touching Client.

## Size
M. No hard dependency; better after RL-003 (owned ticks include group storage).
