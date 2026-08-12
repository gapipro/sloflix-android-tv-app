# Sloflix Android TV

Sloflix is an Android TV application built with Kotlin and Compose for TV.

## Build

Use Android Studio's bundled JDK 17 or another installed JDK 17:

```bash
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Install on Nvidia Shield

Enable Developer options and Network debugging on the Shield, then run:

```bash
adb connect <shield-ip>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Sign in

Open Sloflix from the TV launcher and sign in with your Sloflix account. Do not
store account credentials in this repository.

## Shield smoke checklist

- [ ] App appears in the Android TV launcher.
- [ ] Login success and failure messages are clear.
- [ ] Killing and relaunching the app keeps the user signed in.
- [ ] Categories scroll correctly with the D-pad.
- [ ] Filters change the displayed results.
- [ ] Opening details and selecting Play starts playback.
- [ ] When progress is available, Resume and Continue watching appear.
