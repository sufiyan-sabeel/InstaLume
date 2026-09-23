#!/usr/bin/env bash
#
# InstaLume build pipeline — V1.0.0 by Umaiz Sufiyan.
# Based on FeurStagram (GPLv3), see NOTICE.
#
#   ./build.sh <instagram.apk|.apkm|.xapk|.apks> [--clone] [--install] [--debug]
#
# Builds the patch bundle (.mpp) from the Gradle project, then applies it to the
# given Instagram APK with the local Morphe CLI, producing ./instalume.apk.
#   --clone     install side-by-side as a separate package (com.instagram.android.instalume)
#   --install   install the result on the connected ADB device
#   --debug     enable the Debug bridge patch: the settings become drivable over
#               ADB broadcasts (see extensions/.../DebugBridge.java). Never ship it.
#
# Split bundles: an APKMirror .apkm (or .xapk/.apks) is a zip of base.apk plus
# split APKs. The Morphe CLI patches one APK, so those are merged into a single
# universal APK with APKEditor (tools/APKEditor-*.jar) before patching. The merge
# is cached under build/merged/ and reused while it is newer than the bundle.
#
# Signing: set INSTALUME_KEYSTORE_PASS (and optionally INSTALUME_KEY_PASS)
# to sign with instalume.keystore. That keystore is PKCS12, which the Morphe
# CLI cannot read (it expects BKS), so the APK is built unsigned and signed with
# the Android SDK's apksigner. Override the keystore/alias
# with INSTALUME_KEYSTORE / INSTALUME_KEY_ALIAS. Legacy FEURSTAGRAM_* vars are
# accepted as fallback for migration. Without a keystore password
# the CLI signs with a throwaway key (fine for testing, not for release).
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI="$(ls -t "$DIR"/tools/morphe-cli-*.jar "$DIR"/tools/morphe-desktop-*.jar 2>/dev/null | head -1 || true)"
OUT="$DIR/instalume.apk"

if [ -d "/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home" ]; then
    export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"
fi

if [ -z "${GITHUB_TOKEN:-}" ] && command -v gh >/dev/null 2>&1; then
    export GITHUB_TOKEN="$(gh auth token 2>/dev/null || true)"
    export GITHUB_ACTOR="${GITHUB_ACTOR:-$(gh api user --jq .login 2>/dev/null || true)}"
fi

if [ -z "${ANDROID_HOME:-}" ]; then
    for sdk in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" \
        "/opt/homebrew/share/android-commandlinetools" "/usr/local/share/android-commandlinetools" \
        "${ANDROID_SDK_ROOT:-}"; do
        if [ -n "$sdk" ] && { [ -d "$sdk/platform-tools" ] || [ -d "$sdk/build-tools" ]; }; then
            export ANDROID_HOME="$sdk"
            break
        fi
    done
fi

JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
JAVA_BIN="${JAVA_BIN:-java}"

KEYSTORE="${INSTALUME_KEYSTORE:-${FEURSTAGRAM_KEYSTORE:-$DIR/instalume.keystore}}"
KEY_ALIAS="${INSTALUME_KEY_ALIAS:-${FEURSTAGRAM_KEY_ALIAS:-instalume}}"
KEYSTORE_PASS="${INSTALUME_KEYSTORE_PASS:-${FEURSTAGRAM_KEYSTORE_PASS:-}}"
KEY_PASS="${INSTALUME_KEY_PASS:-${FEURSTAGRAM_KEY_PASS:-$KEYSTORE_PASS}}"
APKSIGNER=""
if [ -n "${ANDROID_HOME:-}" ]; then
    APKSIGNER="$(ls -t "$ANDROID_HOME"/build-tools/*/apksigner 2>/dev/null | head -1 || true)"
fi

APK=""
CLONE=0
INSTALL=0
DEBUG=0
for arg in "$@"; do
    case "$arg" in
        --clone) CLONE=1 ;;
        --install) INSTALL=1 ;;
        --debug) DEBUG=1 ;;
        *) APK="$arg" ;;
    esac
done

if [ -z "$APK" ] || [ ! -f "$APK" ]; then
    echo "usage: ./build.sh <instagram.apk|.apkm|.xapk|.apks> [--clone] [--install] [--debug]" >&2
    exit 1
fi
if [ -z "$CLI" ]; then
    echo "Error: Morphe CLI not found under tools/ (morphe-cli-*.jar / morphe-desktop-*.jar)." >&2
    exit 1
fi

case "$APK" in
    *.apkm | *.xapk | *.apks)
        EDITOR="$(ls -t "$DIR"/tools/APKEditor-*.jar 2>/dev/null | head -1 || true)"
        if [ -z "$EDITOR" ]; then
            echo "Error: $(basename "$APK") is a split bundle and needs APKEditor to merge." >&2
            echo "       Fetch it into tools/, then re-run:" >&2
            echo "       gh release download V1.4.9 -R REAndroid/APKEditor -p 'APKEditor-1.4.9.jar' -D tools/" >&2
            exit 1
        fi
        MERGED="$DIR/build/merged/$(basename "${APK%.*}").apk"
        if [ -f "$MERGED" ] && [ "$MERGED" -nt "$APK" ]; then
            echo "==> [0/3] Reusing merged bundle: $(basename "$MERGED")"
        else
            echo "==> [0/3] Merging splits from $(basename "$APK")"
            mkdir -p "$(dirname "$MERGED")"
            if ! "$JAVA_BIN" -Xmx4g -jar "$EDITOR" m -f -i "$APK" -o "$MERGED" > "$MERGED.log" 2>&1; then
                echo "Error: APKEditor failed to merge $(basename "$APK")." >&2
                echo "       Log: $MERGED.log" >&2
                exit 1
            fi
            echo "    merged: $MERGED"
        fi
        APK="$MERGED"
        ;;
esac

echo "==> [1/3] Building patch bundle (.mpp)"
"$DIR/gradlew" -p "$DIR" :patches:build
MPP="$(ls -t "$DIR"/patches/build/libs/patches-*[0-9].mpp 2>/dev/null | grep -v -- '-sources\|-javadoc' | head -1 || true)"
if [ -z "$MPP" ]; then
    echo "Error: no .mpp produced under patches/build/libs/" >&2
    exit 1
fi
echo "    bundle: $MPP"

echo "==> [2/3] Applying to $(basename "$APK")"
REPORT="$DIR/build/patch-report.json"
mkdir -p "$DIR/build"
ARGS=(-jar "$CLI" patch -p "$MPP" -f -r "$REPORT" -o "$OUT")
[ "$CLONE" -eq 1 ] && ARGS+=(-e "Clone")
[ "$DEBUG" -eq 1 ] && ARGS+=(-e "Debug bridge")

SIGN_WITH_APKSIGNER=0
if [ -n "$KEYSTORE_PASS" ]; then
    if [ ! -f "$KEYSTORE" ]; then
        echo "Error: keystore not found: $KEYSTORE" >&2
        exit 1
    fi
    if [ -z "$APKSIGNER" ]; then
        echo "Error: apksigner not found under \$ANDROID_HOME/build-tools." >&2
        echo "       Install the Android SDK build-tools, or unset INSTALUME_KEYSTORE_PASS" >&2
        echo "       to sign with a throwaway key (testing only)." >&2
        exit 1
    fi
    SIGN_WITH_APKSIGNER=1
    ARGS+=(--unsigned)
fi
ARGS+=("$APK")
"$JAVA_BIN" "${ARGS[@]}"

if [ "$SIGN_WITH_APKSIGNER" -eq 1 ]; then
    echo "    signing with apksigner ($(basename "$KEYSTORE"), alias $KEY_ALIAS)"
    "$APKSIGNER" sign \
        --ks "$KEYSTORE" \
        --ks-key-alias "$KEY_ALIAS" \
        --ks-pass "pass:$KEYSTORE_PASS" \
        --key-pass "pass:$KEY_PASS" \
        --v1-signing-enabled true \
        --v2-signing-enabled true \
        --v3-signing-enabled true \
        --v4-signing-enabled false \
        "$OUT"
    rm -f "$OUT.idsig"
    "$APKSIGNER" verify --print-certs "$OUT" 2>/dev/null \
        | grep -i "SHA-256" | head -1 | sed 's/^/    cert /' || true
fi

if [ -f "$REPORT" ] && command -v python3 >/dev/null 2>&1; then
    python3 - "$REPORT" <<'EOF' || true
import json, sys
report = json.load(open(sys.argv[1]))
for patch in report.get("appliedPatches", []):
    print(f"    ok   {patch.get('name')}")
for patch in report.get("failedPatches", []):
    print(f"    FAIL {patch.get('name')}: {patch.get('exception') or patch.get('error') or ''}")
EOF
fi

echo "==> [3/3] Output: $OUT"
[ "$DEBUG" -eq 1 ] && echo "    !! debug build: settings are drivable over ADB broadcasts — do not release"
if [ "$INSTALL" -eq 1 ]; then
    echo "    installing on device..."
    adb install -r "$OUT"
fi
