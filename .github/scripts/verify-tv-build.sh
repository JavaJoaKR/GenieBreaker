#!/usr/bin/env bash
set -euo pipefail

mkdir -p tv-results
adb shell wm size 1920x1080
adb shell wm density 320
bash .github/scripts/verify-tv-release-remote.sh tv-apks/release.apk tv-results

adb install -t tv-apks/debug.apk
adb install -t tv-apks/instrumentation.apk
runner=kr.javajoa.geniebreaker.test/androidx.test.runner.AndroidJUnitRunner
if ! adb shell am instrument -w -r "$runner" | tee tv-results/instrumentation.txt ||
   ! grep -Eq '^OK \([1-9][0-9]* tests?\)' tv-results/instrumentation.txt; then
  adb pull /sdcard/Download/genie-remote-failure.png tv-results/remote-failure.png || true
  exit 1
fi

for screen in home-ui tv-ui app-ui flutter-menu files-ui file-path-ui storage-ui mapping-ui \
  settings-ui shortcuts-ui popup-depth clone-options per-app-options; do
  adb pull "/sdcard/Download/genie-$screen.png" "tv-results/$screen.png"
done
bash .github/scripts/verify-tv-startup.sh --preinstalled
