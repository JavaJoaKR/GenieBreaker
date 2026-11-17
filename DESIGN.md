# GenieBreaker TV design

A compact Android TV utility operated by a directional remote. Maintain the
existing navy/blue identity. This is a migration and polish of the existing
approved product direction, not a new visual-direction selection exercise.
Design guidance: qiaomu-design (existing-direction craft and functional UI path).
Visual restraint 3/10, motion 1/10, density 6/10.

## Functional contract

Within three seconds identify the current tab, focused action and device/task
state. Complete Bluetooth pairing, app cloning/installation, app settings access,
file management, app-owned storage cleanup and local error-code reading using
only the remote. All application screens and popups share one Flutter engine.
Android permission, pairing, picker and installer screens remain system-owned.

## Design system

- Canvas #0D1420; panel #172233; raised #1B293B; focus fill #2D4665.
- Primary text #F3F6FC; secondary #AAB7C9; action accent #9DC5FF.
- Selected: subdued whole-surface #193554 and selection icon. Focused: 2 logical
  pixel white full outline. No decorative left bars, glow or gradients.
- Linear donor: three luminance layers, one accent, quiet 1px structural borders.
  Apple donor: 2px focus outline, 8px spacing base, 400/500/600 type roles,
  shadows reserved for modal depth. Translate to existing navy tokens; copy no
  proprietary typography or brand marks.
- Korean system fonts avoid remote font requests and additional APK font assets.
  Headings 24–30; rows 17; metadata 12; 4px component / 8px section rhythm.
- Whole application rendered at 0.90 of its previous logical size, with matching
  viewport and hit testing. This never alters Android's global density setting.
- Five deliberate details: full focus outline, distinct selected fill, persistent
  package/path/device identities, official Material icons plus actual app icons,
  bounded modal surface with pinned actions and prominent local error codes.
- No decorative media. Bundled Material icons are tree-shaken; installed app icons
  are read locally and resized to 64px. No app/device information is uploaded.

## Layout and interaction

- Full-height fixed sidebar; independently scrollable content. Six tabs:
  Bluetooth, apps, shortcuts, files, storage, settings.
- Up/Down in sidebar stays there. Right opens the chosen tab and restores its last
  attached content focus. All edges clamp; no wrapping or diagonal list jumps.
- Bluetooth lists maintain their own columns even when lengths differ. Async
  names update keyed rows without replacing the focused identity.
- App grid uses stable package keys, adaptive columns and explicit directions.
- Explorer provides connected-storage roots, folder navigation, sort, refresh,
  mkdir, copy, cut/paste, rename, delete, open/install and local details. Each row
  has a separately focusable file-actions button. Only permitted roots are shown.
- Left at content's first column returns to sidebar. Back inside a nested folder
  returns to its parent and originating row; at top-level returns to sidebar.
- Storage is a separate tab. Cleanup only affects app-owned APKs, updates and
  caches. Destructive actions default to Cancel; progress rejects duplicate work.
- Menus stay within the same Flutter surface and exclude background focus.
  Back pops one menu level; Close restores the original content focus.
- No permanent direction/confirm/back tutorial text. Labels describe actions.
- Key-repeat feedback and scrolling are immediate. System reduced-motion settings
  are respected; no decorative animation is added.

## States and recovery

- Loading preserves existing content, empty states explain the missing content,
  permission state offers permission access, errors show a short offline code and
  refresh/retry remains reachable. Long names wrap at most twice then ellipsize.
- Repeated operations are serialized. Copy never overwrites existing files; move
  deletes the source only after copy completes. Incomplete copies are removed or
  explicitly reported. Root deletion and copies into descendants are rejected.
- Read-only roots remain browsable, but mutations are offered only with write
  permission. Internal storage and USB use user-granted document-tree access.
- File API and installer locks prevent cleanup racing active file work.

## Acceptance

Widget tests own directional/focus regression coverage; Android tests cover the
single Flutter host, menu callback stack, provider containment, actual file
operations and existing installer/updater regressions. Review real rendered TV
screens at 960×540 and a smaller landscape viewport. Hardware Bluetooth/profile
behavior and vendor settings screens still require MA4000 validation.

Release builds enable R8/resource shrinking, retain ARM32 only and preserve
reflective APK editing/signing APIs. Automatic GitHub updates remain enabled.
Logs stay local and store category/exception codes without device metadata,
IP, location or full paths. No log upload or analytics endpoint is included.
