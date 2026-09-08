# RL-013 — Knowledge-base maintenance: revision pinning, kb-diff, content lint

## Story
As the maintainer, I want to know exactly which wiki revisions the KB was built from, see what
changed before I commit a regeneration, and catch weasel content in curated files automatically.

## Evidence
Compass pins MediaWiki revision ids per source page and ships a `--check-live` script that
reports which pages changed (scripts/review-strategy-sources.py, strategy-source-snapshot.json)
plus a change detector that classifies NEW/REMOVED/RENAMED/CHANGED (detect-content-changes.py). Its
policy-lists.json bans generic phrasing ("choose whichever", "your best usable"). Signpost has
`generatedAt` per file only, and the reviewer found 31 en dashes in materials.json source names.

## Acceptance criteria
1. kb-build records, per generated file, the wiki revision id and timestamp of every page and
   module fetched (`sources: [{title, revid, timestamp}]`), deterministically ordered.
2. `npm run kb-check` queries the MediaWiki API for the current revision of each recorded page and
   prints the pages that moved, grouped by generated file, without modifying anything.
3. `npm run kb-diff` compares the working-tree KB against `git HEAD` by entry id and prints
   added/removed entries and changed requirement lines for quests, diaries, milestones and
   gathering plans.
4. kb-build normalises Unicode dashes and quotes in emitted strings to ASCII; a test covers an en
   dash in a source name; the plugin-side hyphen mapping can then be removed.
5. A plugin test scans curated text (milestones reasons and notes, gathering steps and notes,
   priorities reasons) for a banned-phrase list (at least: "choose whichever", "your best",
   "if you have", "as needed", "etc.") and fails naming the entry.
6. README's Data section documents the three commands.

## Size
M. No dependency.
