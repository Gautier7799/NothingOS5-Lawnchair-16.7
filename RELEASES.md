# Releases

Downloadable debug builds for this fork. Each entry links to the GitHub release,
names the commit it was built from, and records the checksum of the published
APK so a download can be verified.

Only builds that are known good are listed. Superseded builds are removed, so
the newest entry is always the one to install.

---

## caddy-panel-fixed-860b055

Caddy category panel: apps can be dragged out onto the workspace, and the app
grid is laid out correctly.

- **Commit:** `860b055fae060d6e0bd766f733e18f426cb794d7`
- **Built:** 2026-11-20
- **Size:** 50,816,673 bytes
- **SHA-256:** `24186ea5ba51a18be28ab98fd0b1d25e7c9dae9f0e4d3f213493a6608b8f9488`
- **Download:** https://github.com/Gautier7799/NothingOS5-Lawnchair-16.7/releases/download/caddy-panel-fixed-860b055/Lawnchair.16.Dev.860b055.github.debug.apk

```bash
adb install -r -d Lawnchair.16.Dev.860b055.github.debug.apk
```

### What it fixes

Two separate defects in the panel that opens when a Caddy category card is
tapped.

**Dragging an app out did nothing.** The panel was a `Dialog`, so it owned a
separate window. A drag preview cannot be mapped from a view that is not in the
launcher `DragLayer`, and move/up events went to the dialog window instead of
the drag driver. The panel is now an `AbstractFloatingView` in the `DragLayer`,
so the panel, the drag preview and the workspace share one coordinate space and
one touch stream.

**The app icons were stacked on top of each other.** The panel was added with
`WRAP_CONTENT` and given its real width later, in a layout callback. The grid
was therefore measured while its width was still unresolved, and its cells were
zero-width columns with `weight = 1f`. A weight can only divide space the parent
actually has, so every column measured to zero and all four cells landed at
`x = 0`. The panel width is now resolved before the grid is built and each cell
receives that width in pixels, so no layout pass can collapse the columns.

### Verified

- 226 unit tests, 0 failures
- `spotlessCheck` clean
- APK contains the new `cellWidthPx` and `SpacerView` symbols, checked inside the dex
- Installed and confirmed working by the reporter

There is no emulator in the build environment, so the layout was verified by
arithmetic and unit tests rather than by screenshot.

---

## Earlier builds

Builds from before this fork's release tracking began were published as
individual GitHub releases and are not repeated here. Superseded Caddy panel
builds (`caddy-panel-drag-64ccfb9`, `caddy-panel-parity-0fbebae`,
`caddy-panel-fixed-56126fd`) have been deleted: they were all replaced by
`860b055`, and two of them contained the icon overlap described above.
