#!/usr/bin/env bash
set -euo pipefail

pkg=kr.javajoa.geniebreaker
[[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" == 29 ]]
adb install dist/GenieBreaker-Android10.apk
adb shell wm size 1920x1080
adb shell wm density 320
adb shell wm dismiss-keyguard
adb shell am start -W -n "$pkg/.MainActivity"
sleep 4
adb shell pidof "$pkg"

# Enter Shortcuts, move to its sixth row, and open the system settings.
for key in 20 20 22 20 20 20 20 20 23; do
  adb shell input dpad keyevent "$key"
  sleep 0.25
done
for attempt in {1..12}; do
  if adb shell dumpsys activity activities | grep -E '(topResumedActivity|mResumedActivity|ResumedActivity).*com\.android\.settings/' >/dev/null; then
    echo 'Android 10 compatibility APK launched and opened TV update settings or device info.'
    exit 0
  fi
  sleep 1
done
adb shell dumpsys window windows | grep -E '(mCurrentFocus|mFocusedApp)' || true
adb logcat -d -s flutter AndroidRuntime ActivityTaskManager | tail -100
exit 1
