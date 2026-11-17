#!/usr/bin/env bash
set -euo pipefail
pkg=kr.javajoa.geniebreaker
target=com.android.settings
home_component() {
  adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -1
}
wait_for_settings() {
  for attempt in {1..40}; do
    if adb shell dumpsys activity activities | grep -E '(topResumedActivity|mResumedActivity|ResumedActivity).*com\.android\.settings/' >/dev/null; then return 0; fi
    sleep 2
  done
  echo 'Selected app did not become the resumed activity' >&2
  return 1
}
gradle --no-daemon :app:installDebug :app:installDebugAndroidTest
original_home=$(home_component)
[[ "$original_home" == */* && "$original_home" != "$pkg/"* ]]
adb shell appops set "$pkg" SYSTEM_ALERT_WINDOW allow
seed=$(adb shell am instrument -w -e class "$pkg.TvControlsTest#configureEmulatorBoot" -e startupFixture "$target" "$pkg.test/androidx.test.runner.AndroidJUnitRunner")
if [[ "$seed" != *'OK (1 test)'* ]]; then echo "$seed"; exit 1; fi
adb shell am start -W -n "$pkg/.MainActivity" --ez ui_preview true
adb shell input keyevent KEYCODE_HOME
adb reboot
adb wait-for-device
for attempt in {1..60}; do
  [[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == 1 ]] && break
  sleep 2
done
adb shell wm dismiss-keyguard
wait_for_settings
[[ "$(home_component)" == "$original_home" ]]
adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell input keyevent KEYCODE_SLEEP
sleep 2
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
wait_for_settings
[[ "$(home_component)" == "$original_home" ]]
echo 'Selected app opened after reboot and standby. Original HOME component verified.'

