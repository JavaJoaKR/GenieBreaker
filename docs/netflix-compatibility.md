# Netflix TV clone startup profile

The app-management clone popup exposes an experimental Netflix option only for
`com.netflix.ninja`. It defaults on for Netflix TV regardless of version. These are research reference versions, not an allowlist:

| versionName | versionCode | Status |
| --- | --- | --- |
| 13.0.1 build 25028 | 25028 | Preferred upstream target; MA4000 unverified |
| 13.2.1 build 27207 | 27207 | Experimental; MA4000 unverified |
| 13.2.2 build 27250 | 27250 | Experimental; MA4000 unverified |

With the option enabled, the original resource package and task affinity are
preserved and native-library extraction is enabled. The clone declares package
visibility for the installed, enabled original app, which must remain installed.
A DEX scan requires exactly one public, non-static, no-argument Runnable.run():void
method containing the exact startup certificate-failure marker. Only that method
body is replaced by a void return. Absent/ambiguous markers,
unexpected method shapes, or oversized DEX files stop generation with an error.
The resulting DEX is re-read before ARSCLib writes the aligned APK and apksig signs
and verifies it. Output metadata records effective options and the changed method;
APK generation never means playback was verified.

Reference research: https://github.com/ajstrick81/morphe-androidtv-patches
(Netflix CloneAppPatch and startup certificate-check fingerprint). This is an
independent implementation using Apache-licensed dexlib2; no upstream GPL source
or native hook loader is bundled. No ad, household, DRM, subscription, or device
identity patches are included. Ordinary Netflix account and device support still
apply. Changing an APK cannot grant Android system privileges.

Validation uses synthetic DEX fixtures, profile/UI tests and the existing generic
clone install/launch emulator regression. No Netflix APK or MA4000 device is
available in CI. To test on MA4000: update GenieBreaker, keep the original Netflix
installed and enabled, choose the original app in App management, recreate the
clone with the Netflix option, install, and launch. An old generic clone is not
retroactively repaired. Exported diagnostics distinguish generation/install errors
from playback failures.
