# WidgetLoft — standalone app

WidgetLoft is an independent Android application for building a personal board of app and widget
cards. It has no Lawnchair module dependency and is not part of the Lawnchair Gradle settings.

## What works

- **Board of cards** with 1–2 column spans and drag-resizable height (vertical drag) and width
  (horizontal drag on the same handle).
- **Real app picker** built from `PackageManager.queryIntentActivities` — add and remove apps per
  card; long-press an app to remove it.
- **Cards, groups and nesting** — Stack and Combine two selected cards, Group two or more into a
  named container, and Split a container back into its cards.
- **Drag to reorder** — long-press a card heading and drop it at the position under the finger.
- **Persistence** — the whole board is saved on every change and restored on launch and after
  reboot.
- **Material You theming** — surfaces and accent come from the framework `system_*` palette on
  Android 12+, with a Day/Night fallback.
- **Real Android widgets** — a card can host a system widget through `AppWidgetHost`, using the
  system picker and the `ACTION_APPWIDGET_BIND` grant.

## Architecture

- `WidgetLoftModel.kt` — `WidgetLoftCard`/`AppEntry` data and `WidgetLoftLayoutEngine`, the pure
  board operations (resize, merge, group, split, move, add/remove apps).
- `WidgetLoftCodec.kt` — dependency-free text (de)serialization for the board tree.
- `WidgetLoftStore.kt` — `SharedPreferences` persistence.
- `WidgetLoftTheme.kt` — Material You / Day-Night colour resolution.
- `WidgetLoftAppPicker.kt` — installed-app list and the multi-select picker dialog.
- `WidgetLoftAppWidgets.kt` — `AppWidgetHost` lifecycle, pick and bind flow.
- `WidgetLoftBoardView.kt` — the board UI and all card interaction.
- `MainActivity.kt` — the activity that owns the store, theme and widget host.

## Build and test

From the repository root:

```bash
./gradlew -p widgetloft-standalone testDebugUnitTest assembleDebug
```

The APK is written to `widgetloft-standalone/app/build/outputs/apk/debug/app-debug.apk`.

## Boundaries

WidgetLoft is standalone by design. Do not integrate it into Lawnchair, and do not modify Caddy
Home, the Dock, Search or Suggestions from this project.
