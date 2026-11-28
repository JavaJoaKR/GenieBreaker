#!/usr/bin/env bash
set -euo pipefail
pkg=kr.javajoa.geniebreaker
apk=${1:-}
screenshots=${2:-.}
if [[ -z "$apk" ]]; then
  base64 --decode development-keystore.base64 > release.jks
  gradle --no-daemon :app:assembleRelease -PtvReleaseSmoke=true
  apk=app/build/outputs/apk/release/app-release.apk
fi

# Replace the debug fixture on this disposable emulator. Exercise AOT Flutter
# and R8 against the regular launcher entry, with no preview intent.
adb uninstall "$pkg" || true
adb install "$apk"
adb shell am force-stop com.android.settings
adb logcat -c
adb shell am start -W -n "$pkg/.MainActivity"
sleep 3
adb exec-out screencap -p > "$screenshots/release-remote-before.png"
for key in 20 20 20 22 23; do
  adb shell input dpad keyevent "$key"
  sleep 0.35
done
for ((attempt=0; attempt<15; attempt++)); do
  if adb shell dumpsys activity activities | grep -E '(topResumedActivity|mResumedActivity|ResumedActivity).*com\.android\.settings/' >/dev/null; then
    echo 'Release APK: remote moved from Home to Shortcuts and opened Android app settings.'
    adb uninstall "$pkg"
    exit 0
  fi
  sleep 1
done
echo 'Release APK: remote input did not open Android app settings.' >&2
adb exec-out screencap -p > "$screenshots/release-remote-after.png" || true
adb shell dumpsys window windows | grep -E '(mCurrentFocus|mFocusedApp)' || true
adb logcat -d -s flutter AndroidRuntime ActivityTaskManager | tail -140
exit 1
