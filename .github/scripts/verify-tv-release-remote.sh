#!/usr/bin/env bash
set -euo pipefail
pkg=kr.javajoa.geniebreaker
base64 --decode development-keystore.base64 > release.jks
gradle --no-daemon :app:assembleRelease -PtvReleaseSmoke=true

# Replace the debug fixture on this disposable emulator. Exercise AOT Flutter
# and R8 against the regular launcher entry, with no preview intent.
adb uninstall "$pkg"
adb install app/build/outputs/apk/release/app-release.apk
adb shell am force-stop com.android.settings
adb shell am start -W -n "$pkg/.MainActivity"
sleep 3
for key in 20 20 22 23; do
  adb shell input dpad keyevent "$key"
  sleep 0.35
done
for ((attempt=0; attempt<15; attempt++)); do
  if adb shell dumpsys activity activities | grep -E '(topResumedActivity|mResumedActivity|ResumedActivity).*com\.android\.settings/' >/dev/null; then
    echo 'Release APK: remote moved from Bluetooth to Shortcuts and opened Android app settings.'
    exit 0
  fi
  sleep 1
done
echo 'Release APK: remote input did not open Android app settings.' >&2
exit 1
