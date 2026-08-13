# Sloflix Android TV

Independent Android TV client for [Sloflix](https://sloflix.com), built with Kotlin and Jetpack Compose for TV. Not an official Sloflix product.

## Requirements

- Android Studio (or JDK **17**)
- Android TV device or emulator (**API 30+**)

## Quick start

On macOS, point Gradle at Android Studio’s bundled JBR if `java` is missing or too old:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Build a debug APK:

```bash
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

Install (USB or network `adb`):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Full prerequisites, Shield/TV install, release notes, and optional e2e tests: **[Build instructions](docs/BUILD.md)**.

## Sign in

Use a real Sloflix account on the device. Never commit credentials, keystores, or `local.properties`.

## License / status

Personal / independently developed client. Target public repo: [gapipro/sloflix-android-tv-app](https://github.com/gapipro/sloflix-android-tv-app).
