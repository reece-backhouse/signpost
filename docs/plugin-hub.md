# Plugin Hub submission — Signpost

Checklist (all done in-repo unless marked TODO):

- [x] `runelite-plugin.properties` at repo root: displayName, author, support, description, tags, plugins, build=standard
- [x] `icon.png` at repo root, 48×48 (limit 48×72)
- [x] `LICENSE` BSD 2-Clause
- [x] `README.md`
- [x] `build.gradle` `runeLiteVersion = 'latest.release'`, Java 11 release, only Lombok + RuneLite client deps
- [x] No network calls (NoNetworkTest), no reflection, tabs for indentation
- [ ] TODO Create the public GitHub repo `reece-backhouse/signpost` and push `main`
      (`git remote add origin https://github.com/reece-backhouse/signpost.git && git push -u origin main`).
      If the repo name differs, update `support=` in `runelite-plugin.properties` first.
- [ ] TODO Fork https://github.com/runelite/plugin-hub, branch `signpost`, add `plugins/signpost`:

```
repository=https://github.com/reece-backhouse/signpost.git
commit=<full 40-character hash of the pushed main commit>
```

- [ ] TODO Open the PR titled "Add Signpost" with a two-line description (what it does, no network,
      data from the OSRS Wiki under CC BY-NC-SA). Fix any CI failure, wait for review.

Releasing an update later: push to `main`, then a hub PR that bumps `commit=`.
