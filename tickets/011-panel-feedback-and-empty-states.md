# RL-011 — Suggest panel feedback, empty states and progress bars

## Story
As a player, I want the panel to tell me what just happened and why it's empty, and to show at a
glance how far along a skill target is.

## Evidence
Compass: post-click status line ("Later: hidden for 1 hour", OsrsStrategistPanel.java:842-856);
"Level 40 → 50" progress bar on the card; FallbackRecommendationFactory.java:15-45 explains an
empty state ("open your bank" / "log in"); consistent "unobserved is unknown, not empty" notes;
milestone completion feedback (overlay only, sidebar banner is dead code). Signpost: no status
line, no empty-state text, silent focus clear on completion, single header bank hint.

## Acceptance criteria
1. Skill-target cards show a thin progress bar from current xp to target xp with "61/70" text
   (same ProgressBar component as the detail view).
2. After Not now / Ignore / Pin / Own it, a one-line grey status appears under the affected card's
   former position for one render cycle ("Not now: hidden for 7 days or until something changes").
3. When pick-3 is empty the Suggest view says why, choosing the first that applies: "Bank not seen
   yet: open your bank once" / "Everything left is a stage above yours; the closest is X (Later)" /
   "Nothing to suggest: all known goals done or hidden", with a link to the Later/Snoozed section.
4. A goal whose readiness depends on bank contents while the bank is unknown gets a Why note
   "Bank not seen yet, materials assumed missing".
5. When a focused goal or skill target completes, a green strip "Done: 70 Herblore" shows at the
   top of the Suggest view until the next user action; completed goals in the current session are
   listed under a collapsed "Achieved this session (N)".
6. Re-snapshot when the diary journal widget loads (`InterfaceID.JOURNALSCROLL`) so per-task bits
   refresh after the player checks a diary.
7. Render smoke tests for each state; no engine changes beyond a `completedSinceLast` list in
   Advice computed from the previous snapshot's goals.

## Size
S-M. No dependency.
