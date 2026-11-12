package dev.widgetloft

data class AppEntry(
    val packageName: String,
    val label: String,
)

/**
 * A card on the WidgetLoft board.
 *
 * A card either shows its own [apps] or, when [stackedCards] is non-empty, acts as a container
 * for nested cards (a stack or a named group). [appWidgetIds] holds the ids of real Android
 * widgets hosted inside the card through [android.appwidget.AppWidgetHost].
 */
data class WidgetLoftCard(
    val id: String,
    val title: String,
    val apps: List<AppEntry> = emptyList(),
    val stackedCards: List<WidgetLoftCard> = emptyList(),
    val columnSpan: Int = 2,
    val heightDp: Int = 208,
    val group: String = "",
    val appWidgetIds: List<Int> = emptyList(),
) {
    val isContainer: Boolean get() = stackedCards.isNotEmpty()
    val appCount: Int get() = apps.size

    companion object {
        const val MIN_SPAN = 1
        const val MAX_SPAN = 2
        const val MIN_HEIGHT_DP = 144
        const val MAX_HEIGHT_DP = 360
    }
}

enum class MergeMode {
    STACK,
    COMBINE,
}

/**
 * Pure board operations. This module has no Lawnchair or launcher dependency, so every function
 * here is a plain value transform and is covered by JVM unit tests.
 */
object WidgetLoftLayoutEngine {

    fun resize(card: WidgetLoftCard, columnSpan: Int, heightDp: Int): WidgetLoftCard =
        card.copy(
            columnSpan = columnSpan.coerceIn(WidgetLoftCard.MIN_SPAN, WidgetLoftCard.MAX_SPAN),
            heightDp = heightDp.coerceIn(WidgetLoftCard.MIN_HEIGHT_DP, WidgetLoftCard.MAX_HEIGHT_DP),
        )

    /**
     * Merges exactly two selected cards into one. STACK keeps the originals as nested cards so the
     * stack can be split again; COMBINE flattens every app into a single card. Returns the input
     * unchanged when the selection is not exactly two cards, so the caller can no-op safely.
     */
    fun merge(
        cards: List<WidgetLoftCard>,
        selectedIds: Set<String>,
        mode: MergeMode,
        newId: String,
    ): List<WidgetLoftCard> {
        val selected = cards.filter { it.id in selectedIds }
        if (selected.size != 2) return cards

        val merged = when (mode) {
            MergeMode.STACK -> WidgetLoftCard(
                id = newId,
                title = "Stack",
                stackedCards = selected,
                group = newId,
            )
            MergeMode.COMBINE -> WidgetLoftCard(
                id = newId,
                title = selected.joinToString(" + ") { it.title },
                apps = selected.flatMap { it.apps }.distinctBy { it.packageName },
            )
        }

        val firstIndex = cards.indexOfFirst { it.id in selectedIds }
        val remaining = cards.filterNot { it.id in selectedIds }.toMutableList()
        remaining.add(firstIndex.coerceIn(0, remaining.size), merged)
        return remaining
    }

    /**
     * Nests the selected cards into a named group card. Unlike [merge], any number of cards (two or
     * more) can be grouped, and the group keeps the given [title].
     */
    fun group(
        cards: List<WidgetLoftCard>,
        selectedIds: Set<String>,
        newId: String,
        title: String,
    ): List<WidgetLoftCard> {
        val selected = cards.filter { it.id in selectedIds }
        if (selected.size < 2) return cards
        val grouped = WidgetLoftCard(
            id = newId,
            title = title.ifBlank { "Group" },
            stackedCards = selected,
            group = newId,
        )
        val firstIndex = cards.indexOfFirst { it.id in selectedIds }
        val remaining = cards.filterNot { it.id in selectedIds }.toMutableList()
        remaining.add(firstIndex.coerceIn(0, remaining.size), grouped)
        return remaining
    }

    /** Splits a container card back into its nested cards; a plain card splits into itself. */
    fun split(card: WidgetLoftCard): List<WidgetLoftCard> =
        if (card.stackedCards.isEmpty()) listOf(card) else card.stackedCards

    /**
     * Moves the card [id] to [targetIndex] (clamped into range), which is how a drop position is
     * applied. Returns the list unchanged when the card is missing.
     */
    fun moveTo(cards: List<WidgetLoftCard>, id: String, targetIndex: Int): List<WidgetLoftCard> {
        val from = cards.indexOfFirst { it.id == id }
        if (from < 0) return cards
        val mutable = cards.toMutableList()
        val moved = mutable.removeAt(from)
        val to = targetIndex.coerceIn(0, mutable.size)
        mutable.add(to, moved)
        return mutable
    }

    /** Adds [apps] to the card, skipping ones it already shows. Returns the card unchanged if none. */
    fun addApps(card: WidgetLoftCard, apps: List<AppEntry>): WidgetLoftCard {
        val existing = card.apps.mapTo(mutableSetOf()) { it.packageName }
        val added = apps.filter { it.packageName !in existing }
        if (added.isEmpty()) return card
        return card.copy(apps = card.apps + added)
    }

    /** Removes the app with [packageName] from the card. */
    fun removeApp(card: WidgetLoftCard, packageName: String): WidgetLoftCard =
        card.copy(apps = card.apps.filterNot { it.packageName == packageName })

    /** Attaches a hosted Android widget id to the card, ignoring duplicates. */
    fun addAppWidget(card: WidgetLoftCard, appWidgetId: Int): WidgetLoftCard =
        if (appWidgetId in card.appWidgetIds) card
        else card.copy(appWidgetIds = card.appWidgetIds + appWidgetId)

    /** Replaces one card in place by id. */
    fun replace(cards: List<WidgetLoftCard>, updated: WidgetLoftCard): List<WidgetLoftCard> {
        val index = cards.indexOfFirst { it.id == updated.id }
        if (index < 0) return cards
        val mutable = cards.toMutableList()
        mutable[index] = updated
        return mutable
    }
}
