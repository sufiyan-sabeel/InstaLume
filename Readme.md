<div align="center">
  <img src="assets/social-preview.png" alt="InstaLume logo — original frosted-glass mark" width="720" height="">
  <h1>InstaLume</h1>
  <p><strong>Social, reimagined.</strong></p>
  <p>
    InstaLume is an independent Android customization project inspired by modern social-media design,
    with an iOS-inspired Liquid Glass interface, powerful customization, enhanced media controls,
    privacy controls, and a modular feature architecture.
  </p>
  <p>Creator: <strong>Umaiz Sufiyan</strong> · Version: <strong>V1.0.0</strong></p>
  <p><img src="assets/social-preview.png" alt="InstaLume preview" width="720"></p>
</div>

<p align="center">
  <a href="https://github.com/sufiyan-sabeel/InstaLume/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/sufiyan-sabeel/InstaLume?style=flat-square&label=release&color=10a37f"></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/github/license/sufiyan-sabeel/InstaLume?style=flat-square&color=6b7280"></a>
</p>

<p align="center">
  <a href="https://github.com/sufiyan-sabeel/InstaLume/releases/latest"><strong>Download APK</strong></a>
  ·
  <a href="docs/INSTALL.md">Install</a>
  ·
  <a href="docs/BUILD_FROM_SOURCE.md">Build from source</a>
  ·
  <a href="docs/FAQ.md">FAQ</a>
  ·
  <a href="docs/PRIVACY.md">Privacy</a>
  ·
  <a href="https://github.com/sufiyan-sabeel/InstaLume/issues">Issues</a>
</p>

> Independent open-source project. Not affiliated with Meta or Instagram.
> Uses the FeurStagram codebase as its open-source foundation (GPLv3).
> See [NOTICE](NOTICE), [docs/OPEN_SOURCE.md](docs/OPEN_SOURCE.md), and [docs/MIGRATION.md](docs/MIGRATION.md).

## What it keeps and blocks

| Keep using Instagram for | Remove or block |
|--------------------------|-----------------|
| Direct Messages | Endless Home feed |
| Stories | Reels surfaces |
| Search and profiles | Explore and suggested-account recommendations |
| Notifications | Ads, shopping preloads, and telemetry |

## Settings

Long-press the Home tab to open **INSTA LUME SETTINGS**:

- **Support** — Support InstaLume, Project Information, Documentation, Report Issue
- **Mods & customization** — Ghost Mode (version-dependent), Extra Features, Themes & UI, Download Settings (version-dependent), Saved Copies (version-dependent)
- **App customization** — Special Features (landing page), Miscellaneous, Distraction Free, Backup & Restore (JSON)
- **Developer** — Developer Options, Debug Information, Version Compatibility, Patch Diagnostics
- **About** — InstaLume V1.0.0, Created by Umaiz Sufiyan, Licenses, Privacy, Contact

No fake toggles: every control is Implemented, Version dependent, or Unavailable with a reason.

## Architecture

```
Instagram APK (user-supplied)
        ↓
build.sh → :patches:build (.mpp) + extension (.mpe)
        ↓
Morphe CLI patch → build/patch-report.json
        ↓
apksigner (INSTALUME keystore) → instalume.apk
```

- `patches/` — Kotlin Morphe patches, fingerprint-based, in `com.instalume.patches`
- `extensions/extension/` — Java runtime merged into APK, `com.instalume.extension`
- `Config` — single `SharedPreferences` (`instalume_prefs`, migrates from `feurstagram_prefs`)
- `VersionCompatibility` / `PatchRegistry` / `FeatureRegistry` — explicit IG 446 profile, fail-closed
- `Glass` — programmatic Liquid Glass (API 31+ blur, fallback otherwise)
- `BackupManager` — JSON schema v1, validate + merge modes, credential denylist
- See `docs/INSTALUME_ARCHITECTURE.md`, `docs/VERSION_COMPATIBILITY.md`, `docs/FEATURES.md`, `docs/DESIGN.md`.

## Build from source

Requirements: JDK 21, Android SDK (`ANDROID_HOME` + build-tools), GitHub token `read:packages`:

```properties
gpr.user=<your-github-username>
gpr.key=<token-with-read:packages>
```

```bash
./build.sh instagram.apk
./build.sh instagram.apk --clone --install
```

Output: `instalume.apk`. Signing: `INSTALUME_KEYSTORE_PASS` (+ optional `INSTALUME_KEY_PASS`) with `instalume.keystore`; legacy `FEURSTAGRAM_*` vars accepted as fallback. See `docs/BUILD.md`.

## Supported versions

- Android: 8.0+ installable; 12L/13+ for full Liquid Glass blur; graceful fallback below.
- Instagram base tested: `446.0.0.49.77`. Other versions are Unsupported until fingerprinted — see Developer → Version Compatibility.
- Clone package default: `com.instagram.android.instalume`, label `InstaLume`.

## Limitations

- Ghost read/seen/typing controls: Unavailable / version-dependent in V1.0.0 (no safe fingerprint).
- Media quality/autoplay/viewer/DM theming: version-dependent; only validated hooks ship.
- True universal backdrop blur inside every IG window cannot be guaranteed — translucent fallback is used.
- Nav reorder ships only if a reliable pager mapping validates; otherwise disabled.
- Signature-trust bypass from the foundation conflicts with the no-bypass policy — see `docs/SECURITY.md` and `docs/VERSION_COMPATIBILITY.md`; deep links may fall back to home on re-signed builds.

## Security model

- No passwords, session tokens, cookies, or credentials collected, stored, logged, or transmitted.
- Auth stays in Instagram's own flow. No traffic proxy. No analytics.
- Update check is OFF by default (explicit consent). Debug bridge is dev-only (`--debug`), never release.
- See `docs/SECURITY.md` and `docs/PRIVACY.md`.

## Branding

Original InstaLume logo: `assets/instalume-logo.svg`, `docs/app_icon.png`, `docs/app_icon_not_rounded.png`, `docs/assets/img/icon-256.png`. No Instagram logo or proprietary assets. Tagline: “Social, reimagined.”

## Contributing

- Report IG version, Android version, device, classic/clone, repro steps.
- Keep changes focused; update docs; no unrelated reformatting.
- See `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`.

## License

GPLv3 — see [LICENSE](LICENSE). Foundation: FeurStagram by jean-voila + contributors. Built with Morphe (Section 7c name restriction) and Piko techniques. See [NOTICE](NOTICE).
