# RL-012 — ETA to a skill target from the observed XP rate

## Story
As a player training toward a target, I want the skill row to say "about 45 min at your current
rate" so I know whether to keep going this session.

## Evidence
Compass's ProgressViewPanel shows "12,340 XP remaining • 25 min" once a rate is established from
5-minute XP buckets (ProgressHistory.java, ProgressViewPanel.java). The rest of its progress tab
duplicates RuneLite's XP Tracker and is not wanted.

## Acceptance criteria
1. The plugin keeps an in-memory ring of (timestamp, xp) samples per skill from `StatChanged`,
   bounded to the last 30 minutes; nothing is persisted.
2. A rate is "ready" after ≥ 5 minutes and ≥ 2 samples with XP gained; otherwise no ETA is shown.
3. Advice carries `Map<Skill, Long> xpPerHour` (computed in the plugin, passed as data; engine
   stays pure); the skill row in the detail view and the skill-target card append "≈ 45 min at
   38k/h".
4. ETA uses the remaining XP after the route's bank steps, not the raw gap, when a route exists.
5. Tests: rate maths with fixed timestamps; the row text with and without a rate.

## Size
M. No dependency.
