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

# Shared build-only identity is inherited by Chromium and all gclient sub-repositories.
git config --global user.name "KING-BROWSER Build"
git config --global user.email "build@localhost"

sudo apt-get update
sudo apt-get install -y lsb-release file git curl python3 python3-pillow imagemagick librsvg2-bin

git clone --depth 1 https://chromium.googlesource.com/chromium/tools/depot_tools.git
export PATH="$PWD/depot_tools:$PATH"

mkdir -p chromium/src/out/Default
cd chromium/src
git init
# Local build-only identity for applying upstream patches. This is not a login,
# does not use the user's email, and is not sent as an authentication credential.
git config --local user.name "KING-BROWSER Build"
git config --local user.email "build@localhost"
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

# Harden Vanadium's subproject patch helper: never index empty git-am output,
# and print stderr so a real patch failure is diagnosable.
python3 - "$SCRIPT_DIR/vanadium/tools/common/apply_subprojects_patches.py" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
source = path.read_text()
old = """            git_am_stdout_lines = git_am_stdout_str.splitlines()
            git_am_patch_msg = git_am_stdout_lines[0].replace('Applying: ', '')
            if git_am_completed_proc.returncode != 0:
"""
new = """            git_am_stdout_lines = git_am_stdout_str.splitlines()
            if git_am_stdout_lines:
                git_am_patch_msg = git_am_stdout_lines[0].replace('Applying: ', '')
            else:
                git_am_patch_msg = f'<no git-am stdout; patch: {patches_path_abs}>'
            if git_am_completed_proc.returncode != 0:
                if not git_am_stdout_lines:
                    git_am_stderr_str = git_am_completed_proc.stderr.decode('utf-8', errors='replace')
                    print(f'git am failed without stdout for {patches_path_abs}', file=sys.stderr)
                    if git_am_stderr_str:
                        print(git_am_stderr_str, file=sys.stderr)
"""
if old not in source:
    raise SystemExit("Expected upstream helper code was not found; refusing to patch an unexpected version.")
path.write_text(source.replace(old, new))
PY

gclient runhooks
./build/install-build-deps.sh --no-prompt

source "$SCRIPT_DIR/patch.sh"

# Chromium 157 references base::android::android_info in supported_types.cc;
# include its declaration explicitly instead of relying on transitive headers.
if ! grep -q '#include "base/android/android_info.h"' media/base/supported_types.cc; then
  sed -i '/#include "media\/base\/android\/media_codec_util.h"/i #include "base/android/android_info.h"' media/base/supported_types.cc
fi

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
