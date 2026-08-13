# Build instructions — Sloflix Android TV

Detailed compile, install, and test notes for the Android TV app (`com.sloflix.tv`).

Stack snapshot (see `gradle/libs.versions.toml` and `app/build.gradle.kts`):

- AGP **8.7.3**, Kotlin **2.0.21**, Compose BOM **2024.10.01**
- `minSdk` **30**, `compileSdk` / `targetSdk` **35**, JVM toolchain **17**

## Prerequisites

- **JDK 17** (Android Studio’s bundled JBR is fine)
- **Android SDK** with platform tools (`adb`) and an Android TV system image or physical TV box (API 30+)
- Network access to Sloflix APIs when signing in or running live e2e tests

Install Android Studio or the command-line SDK tools, then ensure `adb` is on your `PATH` (typically `$ANDROID_HOME/platform-tools`).

### macOS: `JAVA_HOME` for Android Studio JBR

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
java -version   # should report 17.x
```

If Android Studio lives elsewhere, adjust the path to that install’s `jbr/Contents/Home`.

## Debug build

From the repository root:

```bash
./gradlew :app:assembleDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install on Android TV / Shield

1. On the TV: **Settings → Device preferences → About** → tap Build number until Developer options unlock.
2. Enable **Developer options** and **Network debugging** (or USB debugging if using a cable).
3. Note the device LAN IP (DHCP; yours will differ — e.g. `192.168.1.50` is only an example).

Connect and install:

```bash
adb connect <tv-ip>:5555
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Launch:

```bash
adb shell am start -n com.sloflix.tv/.MainActivity
```

Or open **Sloflix** from the Android TV launcher.

## Release build (unsigned)

```bash
./gradlew :app:assembleRelease
```

Output:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

There is **no** `signingConfig` in `app/build.gradle.kts`, so `assembleRelease` produces an **unsigned** APK. Many devices reject unsigned installs.

### Personal sideload (debug keystore)

For your own device only, you can align/sign with the debug keystore via `apksigner` / `zipalign` from build-tools (paths vary by SDK install):

```bash
# Example only — adjust build-tools version and SDK path
zipalign -v -p 4 \
  app/build/outputs/apk/release/app-release-unsigned.apk \
  app-release-aligned.apk

apksigner sign \
  --ks ~/.android/debug.keystore \
  --ks-key-alias androiddebugkey \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out app-release-signed.apk \
  app-release-aligned.apk
```

Proper Play Store / production signing (upload keystore, CI secrets, Play App Signing) is **future work** — do not commit keystores.

## Instrumented e2e (optional)

Requires a connected Android TV emulator or device (`adb devices` shows it) and a real Sloflix account. Credentials are read from environment variables or instrumentation runner arguments (see `E2ECredentials`).

**Never** put real passwords in the repo, docs, screenshots, or committed Gradle properties.

### Via environment variables

```bash
export SLOFLIX_USERNAME="your_username"
export SLOFLIX_PASSWORD="your_password"
./gradlew :app:connectedDebugAndroidTest
```

### Via instrumentation runner arguments

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.sloflix.username=YOUR_USER \
  -Pandroid.testInstrumentationRunnerArguments.sloflix.password=YOUR_PASS
```

Tests that need credentials call `E2ECredentials.assumePresent()` and are skipped if neither source is set.

## What not to commit

| Item | Why |
|------|-----|
| `local.properties` | Machine-specific SDK path |
| `*.apk`, `*.ap_`, `*.idsig` | Build artifacts |
| Root copies like `sloflix-tv*.apk` | Sideload leftovers |
| `*.jks` / `*.keystore` (except never commit secrets) | Signing material |
| Sloflix username/password | Account secrets |
| Local screenshots (`sloflix_home.png`, etc.) | Optional / large; gitignored |

Use `.gitignore` as the source of truth. Prefer building with Gradle rather than checking in APKs.
