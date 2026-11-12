# WidgetLoft — standalone Android app

WidgetLoft is an independent Android application. It has no Lawnchair module dependency and is not included in Lawnchair's Gradle settings.

## Implemented functional milestone

- Independent Android application (minSdk 26, compile/target SDK 37).
- App picker lists launchable applications installed on the device.
- Create a collection from selected apps, or add selected apps to the selected collection.
- Tap an app icon to launch it; long-press to remove it from a collection.
- Persist collections, app membership, order, stack contents, and dimensions in private app storage.
- Drag cards to reorder them; adjust card height and switch between one- and two-column spans.
- Select two cards to stack or combine them; split a stack back into its original cards.
- Delete selected collections and follow system Day/Night resources.

## Remaining acceptance work

This milestone does not yet host Android home-screen AppWidgets. The resize affordance remains a prototype control rather than a polished edge/corner handle system. Live-wallpaper-backed glass effects and a Compose UI migration remain separate design/performance decisions; do not add them until the functional board is stable and tested.

## Build

From the repository root:

```bash
./gradlew -p widgetloft-standalone testDebugUnitTest assembleDebug
```
