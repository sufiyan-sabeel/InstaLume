# InstaLume — Local Build Guide

> No code changes needed. You supply two local files; everything else is validated by `./build.sh`.
> Nothing below is committed to git: APKs, `tools/`, keystores, and credentials are all gitignored.

## 1. Instagram base APK — where to place it

- Required: **Instagram 446.0.0.49.77, arm64-v8a** (the only base this V1.0.0 build supports; anything else is rejected before patching).
- Obtain it yourself from a trusted source (e.g. APKMirror: `instagram-instagram`, arm64-v8a, nodpi). It is never downloaded or bundled automatically.
- Place it anywhere and pass the path to the build. Recommended — repo root:
  ```bash
  cp ~/Download/instagram.apk ./instagram.apk
  ```
  Split bundles (`.apkm` / `.xapk` / `.apks`) work too; they are merged first (needs APKEditor, see §6).

## 2. Morphe CLI jar — where to place it

- Required: **Morphe CLI ≥ 1.11** (`morphe-desktop-*.jar`, or the older `morphe-cli-*.jar`).
- Obtain it from the official Morphe releases: https://github.com/MorpheApp/morphe-desktop/releases
- Place the jar in `tools/` (create the folder; it stays local, never committed):
  ```bash
  mkdir -p tools
  cp ~/Download/morphe-desktop-*.jar tools/
  ls tools/   # must show morphe-desktop-*.jar (or morphe-cli-*.jar)
  ```

## 3. JDK 21 / Android SDK

- Install **JDK 21** and the **Android SDK** (`build-tools` included), then export:
  ```bash
  export JAVA_HOME=/path/to/jdk-21
  export ANDROID_HOME=$HOME/Android/Sdk   # must contain build-tools/ and platform-tools/
  java -version        # must report 21
  ls "$ANDROID_HOME/build-tools"
  ```
- `python3` is also required (APK validation step).

## 4. GitHub Packages credentials

The Morphe Gradle plugins resolve from GitHub Packages — add to `~/.gradle/gradle.properties` (never in the repo):

```properties
gpr.user=<your-github-username>
gpr.key=<token-with-read:packages>
```

(`GITHUB_TOKEN`/`GITHUB_ACTOR` from `gh auth login` are used as fallback.)

## 5. Build command

From the repo root, with `instagram.apk` beside it:

```bash
./build.sh instagram.apk --clone
```

- `--clone` builds side-by-side as `com.instagram.android.instalume` (label `InstaLume`); omit it to replace stock Instagram.
- Add `--install` to deploy to a connected device (`adb install -r`).
- `--debug` is development-only: never ship it (exposes settings over ADB broadcasts).
- The script validates the base APK first and stops with a clear error on: missing file, undersized file, invalid zip, non-Android APK, wrong package, or unsupported Instagram version.

## 6. Split bundles only

If your base is `.apkm` / `.xapk` / `.apks`, one extra local file is needed:

```bash
gh release download V1.4.9 -R REAndroid/APKEditor -p 'APKEditor-1.4.9.jar' -D tools/
```

## 7. Expected output

- APK: `./instalume.apk` (repo root)
- Patch report: `build/patch-report.json` — every applied patch is listed as `ok <name>`; the CLI aborts on the first failure, so a `FAIL` line means the output is unusable.

## 8. Verify the resulting APK

```bash
# 1. Patch report shows all expected patches applied, zero FAIL.
python3 -c "import json; r=json.load(open('build/patch-report.json')); print('applied:',len(r.get('appliedPatches',[])),'failed:',len(r.get('failedPatches',[])))"
# 2. Package + label are correct.
aapt dump badging instalume.apk 2>/dev/null | grep -E "package:|application-label:" || \
  apkanalyzer manifest print instalume.apk | grep -E "package|label"   # whichever tool you have
#    classic: package com.instagram.android · clone: com.instagram.android.instalume, label InstaLume
# 3. Signature verifies (apksigner from the SDK build-tools).
apksigner verify --print-certs instalume.apk | grep -i "SHA-256"
# 4. Install + smoke test on device.
adb install -r instalume.apk
#    Log in via Instagram's own flow, long-press Home → INSTA LUME SETTINGS opens,
#    Developer → Patch Diagnostics reports Compatible on IG 446.0.0.49.77.
```

## 9. Signing (release)

```bash
export INSTALUME_KEYSTORE_PASS='...'   # never commit; never paste into files
# optional: INSTALUME_KEYSTORE / INSTALUME_KEY_ALIAS / INSTALUME_KEY_PASS
./build.sh instagram.apk --clone
```

Without a keystore password the APK is signed with a throwaway key (testing only — updates will not carry over). Reuse the same `instalume.keystore` for every release build.

## Release status

The V1.0.0 GitHub Release currently ships the **patch bundle only** (`.mpp` + checksums). It is not complete until an actual `instalume.apk` has been generated from a validated base and verified per §8 above.
