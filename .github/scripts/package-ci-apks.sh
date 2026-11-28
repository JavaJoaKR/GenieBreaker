#!/usr/bin/env bash
set -euo pipefail

mkdir -p dist
standard="dist/GenieBreaker-${GITHUB_RUN_NUMBER}-${GITHUB_SHA:0:7}.apk"
release=app/build/outputs/apk/release
signed_apks=("$standard")
verify_tv="${VERIFY_TV:-true}"
if [[ "$verify_tv" == true ]]; then
  mkdir -p tv-apks
  cp "$release/app-armeabi-v7a-release.apk" "$standard"
  cp "$release/app-x86_64-release.apk" tv-apks/release.apk
  cp app/build/outputs/apk/debug/app-debug.apk tv-apks/debug.apk
  cp app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk tv-apks/instrumentation.apk
  signed_apks+=(tv-apks/release.apk)
else
  cp "$release/app-release.apk" "$standard"
fi
python3 - "$standard" "$verify_tv" <<'PY'
from pathlib import Path
from sys import argv
from zipfile import ZipFile

apks = [(argv[1], {"armeabi-v7a"}, ("libapp.so", "libflutter.so"))]
if argv[2] == "true":
    apks += [
        ("tv-apks/release.apk", {"x86_64"}, ("libapp.so", "libflutter.so")),
        ("tv-apks/debug.apk", {"x86_64"}, ("libflutter.so",)),
    ]
for apk, expected, required in apks:
    with ZipFile(apk) as archive:
        libraries = set(archive.namelist())
    actual = {name.split("/")[1] for name in libraries if name.startswith("lib/") and name.endswith(".so")}
    if actual != expected:
        raise SystemExit(f"{apk}: expected {sorted(expected)}, found {sorted(actual)}")
    for abi in expected:
        for library in required:
            if f"lib/{abi}/{library}" not in libraries:
                raise SystemExit(f"{apk}: missing {abi}/{library}")

if argv[2] == "true":
    # The clone and APK editor instrumented tests use this APK as input.
    debug_size = Path("tv-apks/debug.apk").stat().st_size
    if debug_size > 200 * 1024 * 1024:
        raise SystemExit(f"tv-apks/debug.apk exceeds the APK workshop size limit: {debug_size} bytes")
    print(f"x64 debug test APK: {debug_size} bytes")
PY

tools="$ANDROID_HOME/build-tools/35.0.0"
for apk in "${signed_apks[@]}"; do
  "$tools/apksigner" verify --min-sdk-version 26 "$apk"
  if ! "$tools/aapt" dump badging "$apk" | grep -F "versionCode='$BUILD_NUMBER'" >/dev/null; then
    echo "$apk does not have CI version code $BUILD_NUMBER" >&2
    exit 1
  fi
done

(
  cd dist
  sha256sum *.apk > SHA256SUMS.txt
  python3 - *.apk <<'PYREPORT' | tee APK_SIZE.txt
from collections import Counter
from pathlib import Path
from sys import argv
from zipfile import ZipFile

def section(name):
    if name.startswith("lib/"):
        return "Native libraries"
    if name.startswith("frameworks/"):
        return "Bundled framework tables"
    if name.startswith("assets/flutter_assets/"):
        return "Flutter assets"
    if name.startswith("classes") and name.endswith(".dex"):
        return "DEX"
    return "Other files"

for path in map(Path, argv[1:]):
    size = path.stat().st_size
    with ZipFile(path) as archive:
        sizes = Counter()
        for entry in archive.infolist():
            sizes[section(entry.filename)] += entry.compress_size
    sizes["APK headers and signatures"] = size - sum(sizes.values())
    print(f"{path.name} {size} bytes")
    for name, packaged_size in sizes.most_common():
        print(f"  {name}: {packaged_size} bytes")
PYREPORT
  for apk in *.apk; do
    echo "$apk"
    unzip -l "$apk" | awk '/lib\/.*\.so$/ { print $4, $1 }'
  done | tee NATIVE_LIBS.txt
)
