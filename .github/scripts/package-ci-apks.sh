#!/usr/bin/env bash
set -euo pipefail

mkdir -p dist tv-apks
standard="dist/GenieBreaker-${GITHUB_RUN_NUMBER}-${GITHUB_SHA:0:7}.apk"
release=app/build/outputs/apk/release
cp "$release/app-armeabi-v7a-release.apk" "$standard"
cp "$release/app-x86_64-release.apk" tv-apks/release.apk
cp app/build/outputs/apk/debug/app-debug.apk tv-apks/debug.apk
cp app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk tv-apks/instrumentation.apk
if [[ "$ANDROID10_COMPAT" == true ]]; then
  cp "$release/app-universal-release.apk" dist/GenieBreaker-Android10.apk
fi

python3 - "$standard" "$ANDROID10_COMPAT" <<'PY'
from sys import argv
from zipfile import ZipFile

apks = [
    (argv[1], {"armeabi-v7a"}),
    ("tv-apks/release.apk", {"x86_64"}),
]
if argv[2] == "true":
    apks.append(("dist/GenieBreaker-Android10.apk", {"armeabi-v7a", "arm64-v8a", "x86_64"}))

for apk, expected in apks:
    with ZipFile(apk) as archive:
        libraries = set(archive.namelist())
    actual = {name.split("/")[1] for name in libraries if name.startswith("lib/") and name.endswith(".so")}
    if actual != expected:
        raise SystemExit(f"{apk}: expected {sorted(expected)}, found {sorted(actual)}")
    for abi in expected:
        for library in ("libapp.so", "libflutter.so"):
            if f"lib/{abi}/{library}" not in libraries:
                raise SystemExit(f"{apk}: missing {abi}/{library}")
PY

tools="$ANDROID_HOME/build-tools/35.0.0"
for apk in dist/*.apk tv-apks/release.apk; do
  "$tools/apksigner" verify --min-sdk-version 26 "$apk"
  if ! "$tools/aapt" dump badging "$apk" | grep -F "versionCode='$BUILD_NUMBER'" >/dev/null; then
    echo "$apk does not have CI version code $BUILD_NUMBER" >&2
    exit 1
  fi
done

(
  cd dist
  sha256sum *.apk > SHA256SUMS.txt
  stat -c '%n %s bytes' *.apk | tee APK_SIZE.txt
  for apk in *.apk; do
    echo "$apk"
    unzip -l "$apk" | awk '/lib\/.*\.so$/ { print $4, $1 }'
  done | tee NATIVE_LIBS.txt
)
echo "android10=$ANDROID10_COMPAT" >> "$GITHUB_OUTPUT"
