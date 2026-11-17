#!/usr/bin/env bash
set -euo pipefail
pkg=kr.javajoa.geniebreaker
target=com.android.settings
home_component() {
  adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -1
}
wait_for_settings() {
  for attempt in {1..80}; do
    if adb shell dumpsys activity activities | grep -E '(topResumedActivity|mResumedActivity|ResumedActivity).*com\.android\.settings/' >/dev/null; then return 0; fi
    sleep 2
  done
  echo "Selected app did not become the resumed activity after $1" >&2
  # Only the disposable test AVD and its synthetic test app are inspected here.
  adb shell dumpsys package "$pkg" | grep -E 'User 0:|StartupBootReceiver|RECEIVE_BOOT_COMPLETED' | head -12 || true
  adb shell dumpsys activity broadcasts | grep -E 'StartupBootReceiver|act=android.intent.action.BOOT_COMPLETED' | tail -16 || true
  adb logcat -b all -d -s GenieStartupTest | tail -10
  adb shell cmd appops get "$pkg" SYSTEM_ALERT_WINDOW
  adb shell settings get global boot_count
  adb shell run-as "$pkg" cat shared_prefs/tv_controls.xml || true
  adb shell dumpsys activity activities | grep -E '(topResumedActivity|mResumedActivity|ResumedActivity)' | head -4 || true
  adb logcat -d -s ActivityTaskManager AndroidRuntime WindowManager ActivityManager | grep -E '(geniebreaker|Background activity|com.android.settings/.Settings|FATAL EXCEPTION)' | tail -45 || true
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
adb shell cmd appops write-settings
# A normal power-menu reboot lets PackageManager persist the newly installed app's
# first-launch state before BOOT_COMPLETED is dispatched on the next boot.
sleep 12
adb shell sync
adb shell svc power reboot || true
adb wait-for-disconnect
adb wait-for-device
for attempt in {1..60}; do
  [[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == 1 ]] && break
  sleep 2
done
adb shell wm dismiss-keyguard
wait_for_settings reboot
[[ "$(home_component)" == "$original_home" ]]
adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell input keyevent KEYCODE_SLEEP
sleep 2
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
wait_for_settings standby
[[ "$(home_component)" == "$original_home" ]]
echo 'Selected app opened after reboot and standby. Original HOME component verified.'

# This AVD is disposable. Enable the tested service only here to exercise an actual input event.
service="$pkg/.RemoteButtonService"
previous=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
if [[ -z "$previous" || "$previous" == null ]]; then services="$service"; else services="$previous:$service"; fi
adb shell settings put secure enabled_accessibility_services "$services"
adb shell settings put secure accessibility_enabled 1
adb shell input keyevent KEYCODE_HOME
sleep 3
adb shell input keyevent KEYCODE_PROG_RED
wait_for_settings remote
[[ "$(home_component)" == "$original_home" ]]
echo 'Mapped red key opened the selected app from the original launcher.'
