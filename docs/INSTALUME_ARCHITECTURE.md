# InstaLume Architecture Audit — V1.0.0 Foundation

> Base: FeurStagram `main` @ `7d57dcc` (Instagram base 446.0.0.49.77)
> Target: InstaLume V1.0.0 by Umaiz Sufiyan
> Store: `instalume_prefs` (migrated from `feurstagram_prefs`)
> License: GPL-3.0, see LICENSE + NOTICE

## 1. Current architecture (as forked)

This is NOT a standalone Instagram client. It is a patch project:

```
Instagram APK (supplied by user, ~400MB)
        ↓
build.sh merges splits via APKEditor (if .apkm/.xapk/.apks)
        ↓
: patches:build produces .mpp bundle (Morphe 1.3.2 plugin)
        ↓
Morphe CLI applies .mpp + extensions/extension.mpe to APK
        ↓
sign with apksigner (INSTALUME keystore) or throwaway key
        ↓
instalume.apk (classic) or clone APK
```

Modules:

- `settings.gradle.kts`: root `feurstagram-patches` + `app.morphe.patches` 1.3.2
- `patches/build.gradle.kts`: group `com.feurstagram`, about block, morphe-patches-library 1.2.0
- `extensions/extension/build.gradle.kts`: `extension { name = "extensions/extension.mpe" }`, namespace `com.feurstagram.extension`
- No `src/test`, no `src/androidTest`, no root `assembleDebug` aggregate (extension is Android library, patches is JVM/Kotlin).

Code size: 11 patches (~600 LOC Kotlin) + 15 runtime classes (~4980 LOC Java) = ~5586 LOC.

## 2. Patch pipeline (11 patches)

Shared: `patches/.../shared/Constants.kt`

- `EXTENSION_PACKAGE = "Lcom/feurstagram/extension"`
- `EXTENSION = "extensions/extension.mpe"`
- `COMPATIBILITY_INSTAGRAM`: package `com.instagram.android`, `version = null` (targets latest + all future).

| Patch | Type | Default | Fingerprint anchor | Runtime hook |
|---|---|---|---|---|
| Settings entry point | bytecode | true | TabBar binder `<init>(View)` with ViewGroup+View IPUT shape, `LX/*` | `Settings.installHomeTabWatcher(ViewGroup)` |
| Network content blocking | bytecode | true | `Lcom/instagram/api/tigon/TigonServiceLayer.startRequest` (stable name) | `Block.throwIfBlocked(URI)` throws IOException |
| Feed item filtering | bytecode | true | strings `media_or_ad,clips_netego,stories_netego,ad4ad` + `parseFromJson` | `Block.replaceFeedItemType(String)` rewrites to `feurstagram_blocked` |
| Limit feed to following | bytecode | true | debug strings `Request{mReason=,mInstanceNumber=` + `pagination_source,FEED_REQUEST_SENT` | `LimitFeed.setFollowingHeader(Map)` |
| Popup hiding | bytecode | true | sole `Toast` subclass `show()V` | `Toasts.shouldSuppress()` |
| Signature check bypass | bytecode | true | string `Invalid SHA256 key hash` → key-hash type → `Z(X.3uq)` + scope check (legacy static 446- vs 446+ instance) | body replaced with `return true` |
| Force SDR display | bytecode | true | every `Window.setColorMode(I)` framework ref | `Display.setColorMode(Window,int)` pins to SDR if `force_sdr` |
| Restart relay | resource | true | AndroidManifest.xml | declares `RestartActivity` in `:feurstagram_restart`, singleInstance, noHistory |
| Install-packages permission | resource | true | AndroidManifest.xml | adds `REQUEST_INSTALL_PACKAGES` for in-app updater |
| Clone | resource+bytecode | false | manifest package/authorities + string refs | renames to `com.instagram.android.feurstagram`, label `Feurstagram` |
| Debug bridge | bytecode | false (`--debug`) | same TabBar anchor | `DebugBridge.install(ViewGroup)`, exported receiver `com.feurstagram.debug.*` |

CLI behavior: aborts on first failure. `build/patch-report.json` lists applied/failedPatches (receipt, not partial apply).

## 3. Runtime architecture (15 classes, `com.feurstagram.extension`)

Entry: `Settings.installHomeTabWatcher(tabBar)` installs:

- `HomeTabWatcher`: waits for `feed_tab` id via `getIdentifier(..., pkg || com.instagram.android)`, long-press → `Settings.show()`, then `Onboarding.maybeShow()`.
- `Hiders.installAll()`: 10× `OnGlobalLayoutListener`:
  - notes `cf_hub_recycler_view`, instants `creation_entrypoint,direct_quick_snap_consumption_preview`, notifications scoped `action_bar_buttons_container_right/notification`, nav `search_tab,clips_tab,creation_tab,direct_tab,profile_tab` (inverted `nav_show_*`), `FriendsLaneHider` (clips_viewer_action_bar pos>0), `LandingWatcher` (click search/direct/profile once), `HiddenTabSwipeSkipper.install()`.
- `UpdateChecker.check()` + `FollowPrompt.maybeShow(... → checkWhatsNew)`.

Other runtime:

- `Config`: `feurstagram_prefs`, `getBlocked/setBlocked`, hardcore lock (`block_*` + `nav_show_*`), `isLockable/hidesSurface/wasHiddenAtBaseline/captureBaseline`, landing `home/search/direct/profile`, `last_seen_version`, `follow_prompt_update_time`, `sNeedsRestart`.
- `Block`: `SUGGESTED[16]`, `ADS[14]`, `AD_FEED_UNITS[3]`, `SUGGESTED_FEED_UNITS[7]`, always-block logging/commerce/seen/fbupload/stats, `String.contains` matching.
- `Toasts`, `Display`, `LimitFeed`, `CacheCleaner` (wipe Exo/Reels/Room DBs, preserve auth markers, kill procs, relay restart), `RestartActivity` (kill pid, launch intent), `FollowPrompt` (lastUpdateTime, `feurstagram_official`), `Onboarding` (spotlight, 400ms×150 polls, hit-test), `HiddenTabSwipeSkipper` (ViewPager2 reflection `getScrollState/setCurrentItem`, re-aim swipe), `DebugBridge` (SET/GET/DUMP/NAV/TRACE/UPDATE/WHATSNEW/RESET/RESTART/SETTINGS/PING), `UpdateChecker` (api.github.com `jean-voila/Feurstagram`, `Feurstagram-UpdateCheck`, tag `v434-0-0-44-74` ↔ `434.0.0.44.74`, apk `-clone.apk` select, HttpURLConnection download → PackageInstaller, notification+progress page, `REQUEST_INSTALL_PACKAGES`).
- `Settings.java` (990 LOC): entire design system — SURFACE `#0E0E0E`, PRIMARY white, CORNER_XL 28 / L 24 / JOINT 4 / FULL 200, `newPage/cardFrame/addCard/boundToFrame/styleWindow/applyPageInsets`, `makeButton/outlined/ripple`, rows 64dp, Switch tinting, landing Radio rows, hardcore freeze `alpha 0.38`, confirm dialogs, openUrl sponsors/coffee.

No bundled `res/`: all drawables/layouts in code. No Compose/XML.

## 4. Settings architecture

- Single store `feurstagram_prefs` via `ActivityThread.currentApplication`.
- Keys: `block_feed(T),block_explore(T),block_reels(T),block_friends_lane(T),block_stories(F),block_instants(T),block_notes(T),block_suggested(T),block_ads(T),block_notifications(F),nav_show_search(T),nav_show_reels(F),nav_show_create(T),nav_show_direct(T),nav_show_profile(T),limit_following_feed(F),hide_toasts(T),force_sdr(T),landing_page(home),auto_update(T),hardcore_mode(F),onboarding_done(F),last_seen_version, follow_prompt_update_time`.
- UI sections today: Blocked surfaces, Navigation bar, Feed, Popups, Display, Landing page, Updates, Support/Donate, Permanent lock/Done.
- Change → `setNeedsRestart=true`; Done/Back → `CacheCleaner.clearAndRestart()`.
- Landing ↔ nav sync live; hardcore freezes already-hidden surfaces per-session baseline.

## 5. Build pipeline (`build.sh`)

Args: `<apk> [--clone] [--install] [--debug]`. Steps:

1. `[0/3]` merge splits with `tools/APKEditor-*.jar` → `build/merged/*.apk` (JVM 4g).
2. `[1/3]` `:patches:build` → `patches/build/libs/patches-*.mpp`.
3. `[2/3]` `java -jar tools/morphe-cli|desktop-*.jar patch -p MPP -f -r build/patch-report.json -o feurstagram.apk [-e Clone] [-e "Debug bridge"] [--unsigned] APK`.
4. Sign: if `FEURSTAGRAM_KEYSTORE_PASS` set → `apksigner v1+v2+v3` with `feurstagram.keystore(alias feurstagram)`; else CLI throwaway key.
5. Python receipt prints `ok <patch>` / `FAIL <patch>: <err>`.
6. `[3/3]` output + optional `adb install -r`.

Reqs: JDK 21 pinned, ANDROID_HOME build-tools, `~/.gradle/gradle.properties gpr.user/key` or `gh auth token` (read:packages), APKEditor jar, Morphe CLI jar — none present in this env (`no java`, `no tools/`).

## 6. Version compatibility mechanism (today)

- `AppTarget(version=null)` = apply to any Instagram version.
- Robustness via structural fingerprints (shapes, wire strings, framework refs) not obfuscated names — survives renames but NOT semantic restructuring (cf. issue #117 `in_feed_survey` removal killed filter silently; 446 scope-check reshape broke bypass until dual-form match).
- No runtime version gate, no PatchRegistry, no per-patch status UI, no `supportedVersions`, no fail-closed disable. CLI fail-fast is only gate.
- Resource-id resolution via `getIdentifier(name)` + clone fallback gives some resilience.
- `UpdateChecker` compares installed `versionName` vs GitHub tag, picks clone asset by package suffix `.feurstagram`.

## 7. Risks

1. `version=null` silently tries incompatible patches on new IG; partial semantic drift = silent misbehavior.
2. `SignatureCheckBypassPatch` forces trust → conflicts with “no security bypass” requirement; must be removed/disabled or explicitly excepted.
3. `DebugBridge` exported receiver = any app can drive settings if shipped (`default=false` mitigates, but must stay out of release).
4. `ClonePatch` string-replace across all classes risks over-rewrite; provider authorities must stay consistent.
5. `Hiders` global-layout listeners ×10 + swipe skipper + onboarding poller = jank/leak risk if not detached; `Onboarding` `postInvalidateOnAnimation` continuous while shown.
6. `CacheCleaner` heuristic wipe (`AUTH_MARKERS` vs `MEDIA_MARKERS`) + Room DB delete by substring — false positive could drop session-adjacent data; currently preserves auth by name only.
7. `UpdateChecker` HTTP without cert pinning, no SHA256 verify, auto-check ON by default, no consent screen; `REQUEST_INSTALL_PACKAGES` + PackageInstaller = high-trust surface.
8. Build secrets: keystore `*.keystore` gitignored, but `FEURSTAGRAM_*` env must never be logged; no keystore committed.
9. Legal: GPL-3.0 + Morphe 7c name restriction + Piko attribution must be preserved; no Meta/IG affiliation claims; no proprietary assets.
10. No tests: zero unit/instrumentation coverage; manual device testing only via `--debug` + logcat `Blocked by Feurstagram`.

## 8. Limitations (must be honest in UI/docs)

- Cannot guarantee Ghost Mode (read/seen/typing/online) — no fingerprint for those private behaviors in this tree; must ship as `Version dependent / Unavailable`, never fake.
- Cannot guarantee media quality/autoplay/zoom/viewer/DM theming — requires new media-viewer fingerprints not present.
- Cannot guarantee true universal backdrop blur inside IG windows — fallback to translucent material.
- Cannot reorder nav without reliable pager/tab mapping — ship disabled if unverified.
- Deep links without signature bypass will fall back to home feed on re-signed builds.
- Website `docs/*.html` is FeurStagram marketing, not part of APK; must be replaced or removed for InstaLume.

## 9. Proposed InstaLume architecture (V1.0.0)

```
BuildInfo (product, version, creator, repo, igBase)
        ↓
VersionCompatibility (profiles: ig-446-tested, others unsupported)
        ↓
PatchRegistry (11 patches: id, target, fingerprint, status, reason)
        ↓
FeatureRegistry (id, category, enabled, supportedVersions, deps, status)
        ↓
Config (typed keys, instalume_prefs + migration, redaction)
        ↓
Settings IA (Support / Mods / App / Developer / About)
        ↓
GlassSystem (programmatic M3-base components, API31+ blur, fallback)
        ↓
BackupRestore (JSON schema v1, validate, merge modes, denylist)
        ↓
Diagnostics (PatchDiagnostics, export redacted, no creds)
```

Principles:

- Single `SharedPreferences`, typed accessors, migration preserves user settings.
- Explicit `supportedVersions`; unknown IG → compatible patches only, rest disabled with reason.
- No fake toggles: `Implemented / Version dependent / Unavailable`.
- Perf: no continuous blur, detach listeners, no main-thread IO, no bitmap leaks.
- Security: no passwords/tokens/cookies logging, no traffic proxy, no auth bypass, updater consent + checksum.
- Accessibility: 48dp targets, TalkBack descriptions, contrast, reduced motion.
- GPL: keep LICENSE, expand NOTICE with InstaLume modifier + upstream FeurStagram/Morphe/Piko.

Baseline env gap: `java`, Android SDK, Morphe CLI, APKEditor, Instagram APK all absent — build/test steps documented but not yet runnable here. Must be provisioned before declaring V1.0.0 complete.
