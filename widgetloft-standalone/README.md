# WidgetLoft — standalone prototype

WidgetLoft is being developed as an independent Android application. It has no Lawnchair module dependency and is not included in the Lawnchair Gradle settings.

## Current prototype

- Independent Android application shell (minSdk 26, compile/target SDK 37).
- Card board with 1–2 column spans and adjustable card height.
- Drag-to-reorder interaction.
- Select two cards and create either a stack or a combined card.
- Split a stack back into its original cards.
- Uses Android system palette resources where available, with a Day/Night theme.
- Core layout operations have JVM unit tests.

## Deliberate boundaries

This is an early interaction/model prototype, not a finished launcher widget host. It does not yet publish Android home-screen AppWidgets, persist board state, provide a full app picker, or implement production-grade resize handles. Those are later acceptance items; do not integrate it into Lawnchair until those are implemented and tested independently.

## Build

From the repository root:

```bash
./gradlew -p widgetloft-standalone testDebugUnitTest assembleDebug
```
