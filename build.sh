#!/bin/bash
set -Eeuo pipefail

source common.sh
set_keys

VERSION="$(grep -m1 -o '[0-9]\+\(\.[0-9]\+\)\{3\}' vanadium/args.gn)"
if [[ -z "$VERSION" ]]; then
  echo "Could not determine Chromium version from vanadium/args.gn" >&2
  exit 1
fi

export CHROMIUM_SOURCE="https://chromium.googlesource.com/chromium/src.git"
export DEBIAN_FRONTEND=noninteractive

sudo apt-get update
sudo apt-get install -y lsb-release file git curl python3 python3-pillow imagemagick librsvg2-bin

git clone --depth 1 https://chromium.googlesource.com/chromium/tools/depot_tools.git
export PATH="$PWD/depot_tools:$PATH"

mkdir -p chromium/src/out/Default
cd chromium/src
git init
git remote add origin "$CHROMIUM_SOURCE"
git fetch --depth 1 "$CHROMIUM_SOURCE" "+refs/tags/$VERSION:chromium_$VERSION"
git checkout "$VERSION"
cp "$SCRIPT_DIR/.gclient" ../.gclient

# Remove optional upstream patches not used by this Android browser build.
rm -f "$SCRIPT_DIR"/vanadium/patches/*trichrome-{apk-build-targets,browser-apk-targets}.patch
rm -f "$SCRIPT_DIR"/vanadium/patches/*{detailed,supported}-language*.patch
rm -f "$SCRIPT_DIR"/vanadium/patches/*javascript-optimizer-{site-setting,settings-UI}.patch
rm -f "$SCRIPT_DIR"/vanadium/patches/*component-updates.patch
rm -f "$SCRIPT_DIR"/vanadium/patches/*{pdf,PDF,for-content-public,toolbar-button,configs-from-config-app,config-app-parsing,new-tab-card,predictive-back}*.patch

replace "$SCRIPT_DIR/vanadium/patches" "VANADIUM" "TITANIUM"
replace "$SCRIPT_DIR/vanadium/patches" "Vanadium" "Titanium"
replace "$SCRIPT_DIR/vanadium/patches" "vanadium" "titanium"
git am --whitespace=nowarn --keep-non-patch "$SCRIPT_DIR"/vanadium/patches/*.patch

gclient sync -D --no-history --nohooks
gclient runhooks
./build/install-build-deps.sh --no-prompt

source "$SCRIPT_DIR/patch.sh"
cp "$SCRIPT_DIR/args.gn" out/Default/args.gn
gn gen out/Default
mkdir -p out/release

# Build one ABI only: ARM64 APK. Skip the ARM32 rebuild and Android App Bundle.
autoninja -C out/Default chrome_public_apk

APK_PATH="$(find out/Default/apks -type f -name 'Chrome*.apk' -print -quit)"
if [[ -z "$APK_PATH" ]]; then
  echo "Build finished but no Chrome APK was found." >&2
  exit 1
fi

export PATH="$PWD/third_party/jdk/current/bin:$PATH"
export ANDROID_HOME="$PWD/third_party/android_sdk/public"
sign_apk "$APK_PATH" "out/release/$VERSION-arm64-v8a.apk"

test -s "out/release/$VERSION-arm64-v8a.apk"
rm -rf "$SCRIPT_DIR/keys"
echo "Built signed ARM64 APK: out/release/$VERSION-arm64-v8a.apk"
