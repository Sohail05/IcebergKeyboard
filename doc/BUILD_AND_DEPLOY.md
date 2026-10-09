# Build, Test and Deploy

All commands run from the repo root. `make help` lists the targets.

## 1. One-time setup (macOS, Apple Silicon or Intel)

```bash
make setup
```

`scripts/setup-mac.sh` installs, if missing:

| Tool | Version | Where |
|---|---|---|
| JDK | 17 (Homebrew `openjdk@17`) | `/opt/homebrew/opt/openjdk@17` |
| Android cmdline-tools | latest | `~/Library/Android/sdk/cmdline-tools/latest` |
| Platform / build tools | `platforms;android-35`, `build-tools;35.0.0`, `platform-tools` (adb) | `~/Library/Android/sdk` |
| Emulator (optional, `WITH_EMULATOR=1`) | `system-images;android-29;default;arm64-v8a` (Android 10, same as the S9+) | AVD `iceberg_s9`, 1440×2960 |

It also writes `local.properties` with `sdk.dir`. Gradle itself comes from the wrapper (`./gradlew`, Gradle 8.11.1), so nothing else needs installing.

Toolchain: AGP 8.7.3 · Kotlin 2.0.21 · compileSdk 35 · targetSdk 34 · minSdk 26.

## 2. Build and test

| Command | What it does | Output |
|---|---|---|
| `make test` | JVM unit tests (`core/`) | `app/build/reports/tests/testDebugUnitTest/index.html` |
| `make lint` | Android lint (fails on errors) | `app/build/reports/lint-results-debug.html` |
| `make debug` | Debug APK (`com.iceberg.keyboard.debug`, can be installed alongside the release build) | `app/build/outputs/apk/debug/app-debug.apk` |
| `make release` | Minified, signed release APK, copied to `dist/` | `dist/IcebergKeyboard-<version>.apk` |
| `make check` | test + lint + debug build (what CI runs) | |

## 3. Install on the Galaxy S9+

### Option A: USB with adb (recommended while developing)

1. On the phone: **Settings → About phone → Software information → tap "Build number" 7 times** to unlock Developer options.
2. **Settings → Developer options → USB debugging: On.**
3. Connect by USB and accept the "Allow USB debugging?" prompt.
4. Run:
   ```bash
   make install     # builds + installs the debug APK, then enables and selects the keyboard
   ```
   `make install` re-selects Iceberg as the keyboard after each install, because Android switches back to the default keyboard when an IME's APK is updated.

### Option B: Sideload the APK file (no cable, no developer options)

1. `make release` and copy `dist/IcebergKeyboard-<version>.apk` to the phone (USB file transfer, Google Drive, email, Quick Share…).
2. Open it in **My Files**. When prompted, allow **Install unknown apps** for My Files (Settings → Apps → ⋮ → Special access → Install unknown apps).
3. Tap **Install**. Play Protect may warn about an unknown developer: choose **Install anyway**.

### Turn the keyboard on

Open **Iceberg Keyboard** from the app drawer and follow the two setup steps. Or do it manually:
**Settings → General management → Language and input → On-screen keyboard → Manage keyboards → Iceberg Keyboard: On**, then pick it under **Default keyboard**.

Android shows a standard warning that any keyboard "may be able to collect all the text you type". Iceberg has no internet permission, so it cannot send anything off the device.

## 4. Release signing

Release builds are signed with a key you own. Create it once:

```bash
make keystore    # prompts for passwords; writes keystore/release.jks + keystore.properties
```

Both files are **git-ignored. Back them up.** Android only accepts an update signed with the same key, so losing it means uninstalling (and losing learned words and shortcuts, unless exported) to install a new build.

Without `keystore.properties`, `make release` falls back to the debug key and prints a warning. That works for testing but isn't meant for a phone you keep updating.

### Versioning

Bump both values in `app/build.gradle.kts` for every APK you hand out:

```kotlin
versionCode = 2        // integer, must increase or Android refuses the update
versionName = "0.2.0"
```

## 5. Emulator (optional)

```bash
WITH_EMULATOR=1 make setup   # once
make emulator                # boots the iceberg_s9 AVD in the background
make install                 # installs onto the running emulator
```

## 6. CI and GitHub releases

`.github/workflows/android.yml` runs `make check` on every push and pull request, and uploads the debug APK as a build artifact.

To publish an APK on GitHub (no store needed):

```bash
make release
gh release create v0.1.0 dist/IcebergKeyboard-0.1.0.apk --title "v0.1.0" --notes "First sideload build"
```

The phone can then download the APK straight from the Releases page.

## 7. Troubleshooting

| Symptom | Fix |
|---|---|
| `Unable to locate a Java Runtime` | `make` sets `JAVA_HOME` itself. When calling `./gradlew` directly, `export JAVA_HOME=$(brew --prefix openjdk@17)` |
| `SDK location not found` | Run `make setup`, or create `local.properties` with `sdk.dir=/Users/<you>/Library/Android/sdk` |
| `adb: no devices` | Check the cable (data cable, not charge-only), re-accept the RSA prompt, then `adb kill-server && adb devices` |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Installed APK was signed with a different key. Export shortcuts, uninstall, reinstall |
| Keyboard vanished after update | Android re-selects the default keyboard on update. `make install` handles this; otherwise use the in-app "Switch keyboard" button |
