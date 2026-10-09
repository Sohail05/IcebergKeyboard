#!/usr/bin/env bash
# One-time toolchain setup for building Iceberg Keyboard on macOS.
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

command -v brew >/dev/null || { echo "Homebrew is required: https://brew.sh"; exit 1; }

if ! brew list openjdk@17 >/dev/null 2>&1; then
  echo "==> Installing JDK 17"
  brew install openjdk@17
fi
export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "==> Installing Android command-line tools into $SDK"
  tmp="$(mktemp -d)"
  curl -sSfL -o "$tmp/clt.zip" https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip
  mkdir -p "$SDK/cmdline-tools"
  unzip -q -o "$tmp/clt.zip" -d "$tmp"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi

SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
echo "==> Accepting SDK licenses"
yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses >/dev/null || true
echo "==> Installing platform, build tools, adb"
"$SDKMANAGER" --sdk_root="$SDK" "platform-tools" "platforms;android-35" "build-tools;35.0.0" >/dev/null

if [ "${WITH_EMULATOR:-0}" = "1" ]; then
  ABI=$([ "$(uname -m)" = "arm64" ] && echo arm64-v8a || echo x86_64)
  IMG="system-images;android-29;default;$ABI"
  echo "==> Installing emulator + $IMG (Android 10, like the Galaxy S9+)"
  "$SDKMANAGER" --sdk_root="$SDK" "emulator" "$IMG" >/dev/null
  echo no | "$SDK/cmdline-tools/latest/bin/avdmanager" create avd -n iceberg_s9 -k "$IMG" -d pixel_3 --force >/dev/null
  CFG="$HOME/.android/avd/iceberg_s9.avd/config.ini"
  sed -i '' -e 's/^hw.lcd.width=.*/hw.lcd.width=1440/' -e 's/^hw.lcd.height=.*/hw.lcd.height=2960/' \
            -e 's/^hw.lcd.density=.*/hw.lcd.density=560/' -e 's/^hw.keyboard=.*/hw.keyboard=no/' "$CFG"
fi

echo "sdk.dir=$SDK" > "$ROOT/local.properties"
echo "==> Done. Try: make check"
