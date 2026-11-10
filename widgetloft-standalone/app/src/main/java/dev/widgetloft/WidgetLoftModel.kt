package dev.widgetloft

data class AppEntry(
    val packageName: String,
    val label: String,
)

data class WidgetLoftCard(
    val id: String,
    val title: String,
    val apps: List<AppEntry>,
    val stackedCards: List<WidgetLoftCard> = emptyList(),
    val columnSpan: Int = 2,
    val heightDp: Int = 208,
)

enum class MergeMode {
    STACK,
    COMBINE,
}

/** Pure board operations; this module has no Lawnchair or launcher dependencies. */
object WidgetLoftLayoutEngine {
    fun resize(card: WidgetLoftCard, columnSpan: Int, heightDp: Int): WidgetLoftCard =
        card.copy(
            columnSpan = columnSpan.coerceIn(1, 2),
            heightDp = heightDp.coerceIn(144, 360),
        )

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
                apps = emptyList(),
                stackedCards = selected,
            )
            MergeMode.COMBINE -> WidgetLoftCard(
                id = newId,
                title = selected.joinToString(" + ") { it.title },
                apps = selected.flatMap { it.apps },
            )
        }

        val firstIndex = cards.indexOfFirst { it.id in selectedIds }
        val remaining = cards.filterNot { it.id in selectedIds }.toMutableList()
        remaining.add(firstIndex.coerceIn(0, remaining.size), merged)
        return remaining
    }

    fun split(card: WidgetLoftCard): List<WidgetLoftCard> =
        if (card.stackedCards.isEmpty()) listOf(card) else card.stackedCards
}
