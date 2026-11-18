#!/usr/bin/env bash
set -euo pipefail
trap 'echo "Startup verification failed at line $LINENO" >&2' ERR
pkg=kr.javajoa.geniebreaker
target=com.android.settings
home_component() {
  adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -1
}
wait_for_settings() {
  local limit=80
  if [[ "$1" != reboot ]]; then limit=15; fi
  for ((attempt=0; attempt<limit; attempt++)); do
    if adb shell dumpsys activity activities | grep -E '(topResumedActivity|mResumedActivity|ResumedActivity).*com\.android\.settings/' >/dev/null; then return 0; fi
    sleep 2
  done
  echo "Selected app did not become the resumed activity after $1" >&2
  # Only the disposable test AVD and its synthetic test app are inspected here.
  adb shell dumpsys package "$pkg" | grep -E 'User 0:|StartupBootReceiver|RECEIVE_BOOT_COMPLETED' | head -12 || true
  adb shell dumpsys activity broadcasts | grep -E 'StartupBootReceiver|act=android.intent.action.BOOT_COMPLETED' | tail -16 || true
  adb logcat -b all -d -s GenieStartupTest GenieRemoteTest | tail -14
  adb shell dumpsys accessibility | grep -E "Bound services|RemoteButtonService|filterKeyEvents" | head -12 || true
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
timeout 15s adb shell sync
previous_boot=$(timeout 10s adb shell settings get global boot_count | tr -d '\r')
[[ "$previous_boot" =~ ^[0-9]+$ ]]
echo "Requesting emulator reboot from boot $previous_boot"
timeout 15s adb shell svc power reboot || true
# ADB may reconnect before wait-for-disconnect starts. Observe a new boot
# instead of waiting forever for a disconnect edge that has already passed.
reboot_deadline=$((SECONDS + 180))
reboot_ready=false
while (( SECONDS < reboot_deadline )); do
  current_boot=$(timeout 5s adb shell settings get global boot_count 2>/dev/null | tr -d '\r' || true)
  completed=$(timeout 5s adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
  if [[ "$current_boot" =~ ^[0-9]+$ && "$current_boot" -gt "$previous_boot" && "$completed" == 1 ]]; then
    reboot_ready=true
    break
  fi
  sleep 2
done
if [[ "$reboot_ready" != true ]]; then
  echo 'Emulator did not complete a new boot within 180 seconds' >&2
  exit 1
fi
echo "Emulator completed boot $current_boot"
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
service="$pkg/$pkg.RemoteButtonService"
previous=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
if [[ -z "$previous" || "$previous" == null ]]; then services="$service"; else services="$previous:$service"; fi
adb shell settings put secure enabled_accessibility_services "$services"
adb shell settings put secure accessibility_enabled 1
adb shell input keyevent KEYCODE_HOME
for attempt in {1..20}; do
  if adb logcat -d -s GenieRemoteTest | grep -F 'Remapping service connected' >/dev/null; then break; fi
  sleep 1
done
adb logcat -d -s GenieRemoteTest | grep -F 'Remapping service connected'
# The shell input command dispatches at the framework level. A virtual USB remote
# enters through InputReader and exercises the same accessibility filter as hardware.
adb shell uinput - <<'REMOTE'
{"id":1,"command":"register","name":"Genie QA Remote","vid":2567,"pid":7043,"bus":"usb","configuration":[{"type":100,"data":[1]},{"type":101,"data":[103,108,105,106,28,398]}]}
{"id":1,"command":"delay","duration":1500}
{"id":1,"command":"inject","events":[1,398,1,0,0,0]}
{"id":1,"command":"delay","duration":120}
{"id":1,"command":"inject","events":[1,398,0,0,0,0]}
{"id":1,"command":"delay","duration":300}
REMOTE
wait_for_settings remote
[[ "$(home_component)" == "$original_home" ]]
echo 'Mapped red key opened the selected app from the original launcher.'
