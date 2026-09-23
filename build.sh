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

# Only Instagram base with validated fingerprints. Anything else stops below.
SUPPORTED_IG="446.0.0.49.77"
SUPPORTED_IG_PREFIX="446.0.0.49."
SUPPORTED_PKG="com.instagram.android"
# Minimum plausible size for an Instagram base APK (a real one is ~70 MB+).
MIN_APK_BYTES=$((20 * 1024 * 1024))

usage() {
    cat >&2 <<'EOF'
usage: ./build.sh <instagram.apk|.apkm|.xapk|.apks> [--clone] [--install] [--debug]

Supply your own Instagram base APK (it is never committed to git):

  1. Get Instagram 446.0.0.49.77, arm64-v8a, from a trusted source
     (e.g. APKMirror: instagram-instagram, arm64-v8a, nodpi).
  2. Place the file anywhere, e.g. next to this script:
       cp ~/Download/instagram.apk ./instagram.apk
  3. Put the Morphe CLI jar from the official Morphe releases
     (https://github.com/MorpheApp/morphe-desktop/releases) into tools/
     as tools/morphe-desktop-*.jar (or the older morphe-cli-*.jar).
  4. Run:
       ./build.sh instagram.apk
       ./build.sh instagram.apk --clone --install   # side-by-side + deploy

Unsupported Instagram versions are rejected before patching.
EOF
}

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
    usage
    exit 1
fi
if ! command -v "$JAVA_BIN" >/dev/null 2>&1; then
    echo "Error: Java not found (need JDK 21 for the patcher and APKEditor)." >&2
    echo "       Install JDK 21 and re-run." >&2
    exit 1
fi
if [ -z "$CLI" ]; then
    echo "Error: Morphe CLI not found under tools/ (morphe-cli-*.jar / morphe-desktop-*.jar)." >&2
    echo "       Get it from the official Morphe releases" >&2
    echo "       (https://github.com/MorpheApp/morphe-desktop/releases) and place" >&2
    echo "       the jar in tools/, then re-run. It is intentionally not committed." >&2
    exit 1
fi

# Validate a user-supplied base APK before anything is patched.
# Checks: readable file, minimum size, valid zip, has AndroidManifest.xml +
# classes.dex, declares the Instagram package, and carries a supported
# Instagram versionName (read from the manifest string pool).
validate_apk() {
    local apk="$1"
    local base
    base="$(basename "$apk")"

    if [ ! -r "$apk" ]; then
        echo "Error: cannot read '$apk'." >&2
        exit 1
    fi
    local size
    size="$(wc -c < "$apk" | tr -d ' ')"
    if [ "$size" -lt "$MIN_APK_BYTES" ]; then
        echo "Error: '$base' is only $size bytes — too small to be an Instagram base APK." >&2
        echo "       Get the full arm64-v8a APK (Instagram 446.0.0.49.77) and try again." >&2
        exit 1
    fi
    if ! command -v python3 >/dev/null 2>&1; then
        echo "Error: python3 is required to validate the base APK." >&2
        exit 1
    fi
    local info
    if ! info="$(python3 - "$apk" <<'EOF'
import re, sys, zipfile
apk = sys.argv[1]
try:
    z = zipfile.ZipFile(apk)
except Exception:
    print("NOTAZIP")
    sys.exit(0)
names = set(z.namelist())
print("HAS_MANIFEST" if "AndroidManifest.xml" in names else "NO_MANIFEST")
print("HAS_DEX" if any(n == "classes.dex" or n.startswith("classes") and n.endswith(".dex") for n in names) else "NO_DEX")
try:
    blob = z.read("AndroidManifest.xml")
except KeyError:
    sys.exit(0)
text = blob.decode("utf-16-le", errors="ignore") + "\n" + blob.decode("utf-8", errors="ignore")
print("HAS_PKG" if "com.instagram.android" in text else "NO_PKG")
versions = sorted(set(re.findall(r"\d+\.\d+\.\d+\.\d+\.\d+", text)))
print("VERSIONS:" + ",".join(versions))
EOF
)"; then
        echo "Error: failed to inspect '$base'." >&2
        exit 1
    fi
    case "$info" in
        *NOTAZIP*)
            echo "Error: '$base' is not a valid APK/zip archive." >&2
            exit 1
            ;;
    esac
    case "$info" in
        *NO_MANIFEST*|*NO_DEX*)
            echo "Error: '$base' is not an Android app APK (missing AndroidManifest.xml or classes.dex)." >&2
            exit 1
            ;;
    esac
    case "$info" in
        *NO_PKG*)
            echo "Error: '$base' does not declare package com.instagram.android." >&2
            echo "       Supply the official Instagram base APK, not another app." >&2
            exit 1
            ;;
    esac
    local versions
    versions="$(printf '%s\n' "$info" | sed -n 's/^VERSIONS://p')"
    if [ -z "$versions" ]; then
        echo "Error: could not read a versionName from '$base' — refusing to patch an unidentified base." >&2
        exit 1
    fi
    local v ok=0
    for v in $(printf '%s' "$versions" | tr ',' ' '); do
        if [ "$v" = "$SUPPORTED_IG" ] || case "$v" in "$SUPPORTED_IG_PREFIX"*) true;; *) false;; esac; then
            ok=1
            break
        fi
    done
    if [ "$ok" -ne 1 ]; then
        echo "Error: unsupported Instagram base version(s) in '$base': $versions." >&2
        echo "       This InstaLume build supports Instagram $SUPPORTED_IG only." >&2
        echo "       Get the supported base and re-run ./build.sh <apk>." >&2
        exit 1
    fi
    echo "    validated: $base (package $SUPPORTED_PKG, version $v)"
    case "$base" in
        *arm64*|*arm64-v8a*) ;;
        *)
            echo "    warning: '$base' does not look like an arm64-v8a build; prefer arm64-v8a." >&2
            ;;
    esac
}

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

echo "==> Validating base APK"
validate_apk "$APK"

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
