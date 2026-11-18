# GenieBreaker TV design

A compact Android TV utility operated by a directional remote. A dark grayscale
palette uses luminance to distinguish the active tab, focus, and content.
Design guidance: qiaomu-design (existing-direction craft and functional UI path).
Visual restraint 3/10, motion 1/10, density 8/10.

## Functional contract

Within three seconds identify the current tab, focused action and device/task
state. Complete Bluetooth pairing, app cloning/installation, app settings access,
file management, app-owned storage cleanup and local error-code reading using
only the remote. All application screens and popups share one Flutter engine.
Android permission, pairing, picker and installer screens remain system-owned.

## Design system

- Canvas #121212, navigation #1B1B1B, panel #222222, and raised #2A2A2A.
- Primary text #F5F5F5, secondary #BDBDBD, and action accent #DDDDDD.
- The launcher icon, TV banner, and notification share an angular G mark.
- The sidebar uses a whole-surface #393939 fill for the active tab and a brighter
  #5C5C5C fill for focus. Content actions keep the 2dp focus outline.
- Linear donor: three luminance layers, one accent, quiet 1px structural borders.
  Apple donor: 2px focus outline, 8px spacing base, 400/500/600 type roles,
  shadows reserved for modal depth. Translate to the grayscale palette; copy no
  proprietary typography or brand marks.
- Korean system fonts avoid remote font requests and additional APK font assets.
  Headings 24–27; rows 17; metadata 12. Halve prior explicit radii:
  tiles and action buttons 5, highlights inset 6, panels 7, rail 8, modals 9.
- Whole application rendered at 0.90 of its previous logical size, with matching
  viewport and hit testing. This never alters Android's global density setting.
- Five deliberate details: full focus outline, distinct selected fill, persistent
  package/path/device identities, official Material icons plus actual app icons,
  bounded modal surface with pinned actions and prominent local error codes.
- No decorative media. Bundled Material icons are tree-shaken. Installed app icons
  are read locally, resized to 64px, and displayed in grayscale.

## Layout and interaction

- A 146–174dp navigation rail and 18–24dp horizontal, 14–18dp vertical outer margins reserve most of the
  viewport for content. Its footer shows the installed version. Content headers
  can show a live item count or scan state.
- Eight tabs: Bluetooth, apps, shortcuts, files, storage, mapping, startup and
  settings. Keep navigation and controls visible while each result list scrolls.
- Up/Down in the sidebar opens the focused tab while keeping focus in the rail.
  Right restores that tab's last attached content focus. All edges clamp.
- Bluetooth lists maintain their own columns even when lengths differ. Pinned
  counts and compact rows give devices the remaining height. Async names update
  keyed rows without replacing the focused identity.
- Toolbars share a row with status at TV widths. The app grid sizes columns from
  the available content width, with equal cards, fixed icon slots, and one-line
  labels and status. Package keys keep focus stable.
- Explorer provides connected-storage roots, folder navigation, sort, refresh,
  mkdir, copy, cut/paste, rename, delete, open/install and local details. Each row
  has a separately focusable file-actions button. Down from any toolbar action
  enters the first file row. Up from that row returns to the initiating action.
  The breadcrumb uses display names and keeps the current folder visible. From
  two or more levels deep, the storage list is one action away even if the
  parent folder is unavailable. Only permitted roots are shown.
- Left at content's first column returns to sidebar. Back inside a nested folder
  returns to its parent and originating row; at top-level returns to sidebar.
- Storage shows free and total capacity together above the usage bar. File information has a single Close action and only shows a known modification date.
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
