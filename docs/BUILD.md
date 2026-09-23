# InstaLume Build

Creator: Umaiz Sufiyan · V1.0.0 · Tested IG base 446.0.0.49.77

## Requirements

- JDK 21, Android SDK (`ANDROID_HOME` + build-tools)
- `~/.gradle/gradle.properties`: `gpr.user`, `gpr.key` (`read:packages`)
- User-supplied Instagram APK (arm64-v8a, e.g. APKMirror). Never committed.
- Morphe CLI jar under `tools/` (gitignored) + APKEditor jar for `.apkm/.xapk/.apks`.

## Commands

```bash
./gradlew :patches:build
./build.sh instagram.apk
./build.sh instagram.apk --clone --install
./build.sh instagram.apk --debug   # dev only, never release
```

Output: `instalume.apk`, report `build/patch-report.json`.

## Signing

`INSTALUME_KEYSTORE_PASS` (+ optional `INSTALUME_KEY_PASS`) with `instalume.keystore` (PKCS12 via apksigner v1+v2+v3). Legacy `FEURSTAGRAM_*` accepted as fallback. Without a password the CLI uses a throwaway key (testing only). Never commit `*.keystore`, `*.jks`, `*.apk`, or secrets. CI uses `INSTALUME_KEYSTORE_B64` / `*_PASS` / `*_ALIAS` secrets.

## CI

`.github/workflows/release.yml`: on tag `v*` + `workflow_dispatch`. Always builds + releases the `.mpp` bundle. Full `instalume.apk` only when a base APK URL is supplied (`INSTAGRAM_APK_URL`) and Morphe CLI is available; signing keys come from secrets only.
