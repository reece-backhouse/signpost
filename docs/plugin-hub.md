# Plugin Hub submission — Signpost

Checklist (all done in-repo unless marked TODO):

- [x] `runelite-plugin.properties` at repo root: displayName, author, support, description, tags, plugins, build=standard
- [x] `icon.png` at repo root, 48×48 (limit 48×72)
- [x] `LICENSE` BSD 2-Clause
- [x] `README.md`
- [x] `build.gradle` `runeLiteVersion = 'latest.release'`, Java 11 release, only Lombok + RuneLite client deps
- [x] No network calls (NoNetworkTest), no reflection, tabs for indentation
- [x] Public repo https://github.com/reece-backhouse/signpost, `main` pushed (`origin`)
- [x] Fork https://github.com/runelite/plugin-hub, branch `signpost`, `plugins/signpost`
      (PR https://github.com/runelite/plugin-hub/pull/16246):

```
repository=https://github.com/reece-backhouse/signpost.git
commit=<full 40-character hash of the pushed main commit>
```

- [x] PR "Add Signpost" opened; hub build passed. "Requires maintainer review" is the hub app's normal
      state for a new plugin until a maintainer reviews it.

Releasing an update later: push to `main`, then a hub PR that bumps `commit=`.
