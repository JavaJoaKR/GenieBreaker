# GenieBreaker TV design

A compact, quiet Android TV utility operated entirely by a directional remote.
Keep the existing navy/blue identity and blue/navy settings logo. Visual restraint
3/10, motion 1/10, density 6/10. Brand/version remain top-left. No marketing hero,
glass layers, decorative animation, or oversized controls.

## Functional contract
Within three seconds identify Bluetooth, apps, files or settings and the current
focused action. Pair devices, create/install/manage clones, browse files, clear
update downloads and read local error codes without losing a focus target.

## System
- Canvas #0D1420, surfaces #172233/#1B293B, secondary #AAB7C9, text #F3F6FC.
- Accent #9DC5FF for actions; selected is a subdued fill, focused adds a white 2dp
  outline. Selection and keyboard focus remain visually distinct.
- 4dp component / 8dp section spacing, 10–18dp radii. Main type 14–18sp,
  headings 24–30sp. Rows 56–70dp; app icons 40dp. Korean system fonts avoid
  network font downloads and unnecessary APK assets.
- Linear reference: three luminance layers, quiet borders and single accent.
  Apple reference: one interaction color, short labels and role-based type.
  These reference decisions were read through qiaomu-design; brand visuals are
  not copied. Existing product direction and the user's direct rewrite request
  determine this implementation rather than a new style-selection workflow.
- Native icons are Lucide ISC SVG assets converted to Android VectorDrawables;
  source/license retained in docs/assets/lucide. Flutter uses its bundled official
  Material Icons. No custom Canvas symbols or Unicode navigation chevrons.

## Navigation contract
- Sidebar Up/Down stays in the sidebar; Right opens that tab's remembered target.
- Lists move vertically; grids move by row/column with clamped edges. No wrapping.
- Bluetooth columns move horizontally to the corresponding row. Leftmost exits
  to the sidebar. Back from native content returns to the selected tab.
- File Left returns from storage actions to folders, from a nested folder to its
  parent, and from the root folders to the sidebar. Back follows the same hierarchy.
- Popups have bounded surfaces, a focus scope, aligned directional traversal,
  stable item keys and remembered parent focus. Back returns one level. Close
  returns to the native opener. No animated scrolling during key repeats.
- Refreshes preserve package/path identity. Removed items choose a visible fallback.
- Destructive confirmations default to Cancel. Progress prevents duplicate work.

## Storage and logs
Update downloads have a separate size/action; orphaned download files and already
installed update APKs are pruned automatically. File locks and pending installer
staging protect active work. Release includes ARM32, emulator builds retain x86_64.
Logs remain local. Stable 10-character ASCII error codes identify categories and
exception types without random suffixes. No raw messages, device metadata, IP,
location or full paths are recorded. No log upload, sharing endpoint or QR feature
is included. GitHub automatic update requests remain enabled as requested.


## Acceptance
Run widget tests for direction clamping, parent-page focus, file hierarchy, empty
states, update cleanup and short local error-code display. Run Android 14 remote/clone/install
regressions and inspect actual Bluetooth/apps/files/popup screenshots. MA4000
hardware pairing and Netflix playback require device validation.
