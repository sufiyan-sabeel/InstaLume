# InstaLume Migration — FeurStagram → V1.0.0

> Creator: Umaiz Sufiyan · Version 1.0.0 · GPLv3, see LICENSE + NOTICE

## Why packages moved (safe)

`com.feurstagram.*` → `com.instalume.*` was done atomically:

- `patches/src/main/kotlin/com/feurstagram/` → `com/instalume/`
- `extensions/.../com/feurstagram/extension/` → `com/instalume/extension/`
- Every `package`, `import`, and bytecode descriptor (`Lcom/...`) updated together.
- `Constants.EXTENSION_PACKAGE` now `Lcom/instalume/extension`.
- `RestartActivity` manifest entry now `com.instalume.extension.RestartActivity`, process `:instalume_restart`.
- Debug actions now `com.instalume.debug.*`, tags `InstaLumeDebug/InstaLumeNet/InstaLumeFeed`.
- Clone default now `com.instagram.android.instalume`, label `InstaLume`.

Instagram's own `com.instagram.android` package is never renamed (classic builds replace it; clone builds use the new suffix).

## Key renames

| Old | New | Notes |
|---|---|---|
| `feurstagram-patches` | `instalume-patches` | `settings.gradle.kts` |
| `com.feurstagram` group | `com.instalume` | `patches/build.gradle.kts` + namespace |
| `feurstagram.apk` / `feurstagram.keystore` | `instalume.apk` / `instalume.keystore` | `build.sh` |
| `FEURSTAGRAM_*` env | `INSTALUME_*` (legacy accepted as fallback) | signing |
| `feurstagram_prefs` | `instalume_prefs` (one-time copy migration) | `Config` + `DebugBridge` |
| `feurstagram_blocked` / `Blocked by Feurstagram` | `instalume_blocked` / `Blocked by InstaLume` | `Block` |
| `:feurstagram_restart` | `:instalume_restart` | `RestartRelayPatch` |
| `jean-voila/Feurstagram` updater | `sufiyan-sabeel/InstaLume` | `UpdateChecker`, `site.js` |
| `feurstagram-update.apk`, channel, threads | `instalume-*` | updater/notifications |
| `Follow @feurstagram_official` | Welcome → GitHub project | `FollowPrompt` (no IG handle claimed) |
| Sponsors/coffee links | Repo/issues/docs links | `Settings` SUPPORT |
| `docs/app_icon*.png`, `icon-256.png`, `social-preview.png` | Regenerated original glass-orb mark | `assets/instalume-logo.svg` is source |
| `docs/index.html` | Rewritten InstaLume landing | download → `releases/latest` + API-resolved APK |
| `docs/robots.txt` sitemap | `sufiyan-sabeel.github.io/InstaLume` | Pages URL |

## What stayed FeurStagram (intentionally)

- `NOTICE`, `LICENSE`, `CHANGELOG` history, `docs/INSTALUME_ARCHITECTURE.md`, and `BuildInfo.FOUNDATION` keep FeurStagram/Morphe/Piko attribution per GPLv3 + Morphe Section 7c.
- Legacy website subpages under `docs/*/`, `docs/fr/*` remain as historical foundation files, unlinked from the new landing. They are not current InstaLume docs.
- `build.sh` still accepts legacy `FEURSTAGRAM_*` env as fallback (migration aid, not branding).

## Settings store migration

First `Config.prefs()` call copies all non-empty legacy keys into `instalume_prefs` once. Legacy store is left intact. Sensitive keys are never copied into backups.
