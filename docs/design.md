# TV Bluetooth design

Design guidance: [frontend-design on skills.sh](https://skills.sh/anthropics/skills/frontend-design), [upstream instructions](https://github.com/anthropics/skills/blob/main/skills/frontend-design/SKILL.md). Consulted for visual hierarchy, deliberate spacing, restrained motion and actionable empty states. Applied to native Android views; no web runtime or installed personal skill is required.

- TV viewing: 32sp title, 15sp device name, 78dp focusable rows, 32dp safe margins.
- Palette: graphite #14171D, slate #1D2129, primary #F0F3F9, secondary #A6AFBE, cool blue #97C0FF. Blue identifies action and remote focus, never connection status.
- Separate registered and nearby devices. Registered means bonded, not currently connected. No misleading connection indicator.
- Inline row updates preserve D-pad focus and scroll position when a device name arrives.
- Discovery/name-change broadcasts use address identity and an independent name cache; blank names and stale device caches do not erase resolved names. Names received before discovery are retained but do not create unsolicited rows.
- Debug-only fixtures and a landscape 960x540dp Android emulator screenshot test verify late-name focus retention. Production builds never show fixture devices.

Hardware validation on MA4000 is still required for Bluetooth radio, pairing dialogs, remote behavior and overscan.
