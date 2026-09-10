# Plugin Hub submission — Signpost

## Submission requirements

- `runelite-plugin.properties` at repo root: displayName, author, description, tags, plugins, build=standard
- `icon.png` at repo root, 48×48 (limit 48×72)
- `LICENSE` BSD 2-Clause
- `README.md`
- `build.gradle` `runeLiteVersion = 'latest.release'`, Java 11 release, only Lombok + RuneLite client deps
- No network calls (NoNetworkTest), no reflection, tabs for indentation
- A public Signpost repository with the release commit pushed to `main`
- A fork of https://github.com/runelite/plugin-hub containing `plugins/signpost`

Generate the submission manifest from the checkout:

```sh
printf 'repository=%s\ncommit=%s\n' "$(git remote get-url origin)" "$(git rev-parse HEAD)"
```

Submit the manifest in a Plugin Hub pull request. The Signpost submission is
https://github.com/runelite/plugin-hub/pull/16246.

For an update, push to `main`, then open a Hub pull request that bumps `commit=`.

The plugin uses `com.signpost` and credits `Signpost contributors`. Keep personal names,
email addresses and personal-account URLs out of release metadata. The optional `support`
field is omitted until a project-owned support destination is available.
