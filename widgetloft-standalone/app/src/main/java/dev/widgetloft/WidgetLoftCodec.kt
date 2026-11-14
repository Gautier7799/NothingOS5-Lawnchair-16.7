package dev.widgetloft

/**
 * Serializes the board to and from a compact text format.
 *
 * The format is hand-written rather than a JSON library so the module stays dependency-free and the
 * (de)serialization can be unit tested on the JVM without Android. One line per card, values are
 * escaped so titles and labels may contain any character; the tree is stored pre-order with a
 * depth column so nested stacks and groups round-trip exactly.
 */
object WidgetLoftCodec {

    private const val HEADER = "WL1"
    private const val SEP = '\u0001'

    private class Record(val depth: Int, val card: WidgetLoftCard)

    fun encode(cards: List<WidgetLoftCard>): String = buildString {
        append(HEADER).append('\n')
        cards.forEach { appendCard(it, 0) }
    }

    private fun StringBuilder.appendCard(card: WidgetLoftCard, depth: Int) {
        append("C").append(SEP)
        append(depth).append(SEP)
        append(escape(card.id)).append(SEP)
        append(escape(card.title)).append(SEP)
        append(card.columnSpan).append(SEP)
        append(card.heightDp).append(SEP)
        append(escape(card.group)).append(SEP)
        append(card.apps.joinToString(",") { escape(it.packageName) }).append(SEP)
        append(card.apps.joinToString(",") { escape(it.label) }).append(SEP)
        append(card.appWidgetIds.joinToString(",")).append('\n')
        card.stackedCards.forEach { appendCard(it, depth + 1) }
    }

    fun decode(text: String?): List<WidgetLoftCard> {
        if (text.isNullOrBlank()) return emptyList()
        val lines = text.split('\n').filter { it.isNotBlank() }
        if (lines.isEmpty() || lines.first().trim() != HEADER) return emptyList()
        val records = lines.drop(1).mapNotNull(::parseRecord)
        if (records.isEmpty()) return emptyList()
        return buildForest(records)
    }

    private fun parseRecord(line: String): Record? {
        if (!line.startsWith("C")) return null
        val parts = line.split(SEP)
        if (parts.size < 10) return null
        val depth = parts[1].toIntOrNull() ?: return null
        val card = WidgetLoftCard(
            id = unescape(parts[2]),
            title = unescape(parts[3]),
            columnSpan = parts[4].toIntOrNull() ?: 2,
            heightDp = parts[5].toIntOrNull() ?: 208,
            group = unescape(parts[6]),
            apps = zipApps(parts[7], parts[8]),
            appWidgetIds = parts[9].split(',').mapNotNull { it.toIntOrNull() },
        )
        return Record(depth, card)
    }

    /** Rebuilds the nested card tree from the pre-order, depth-tagged record list. */
    private fun buildForest(records: List<Record>): List<WidgetLoftCard> {
        var index = 0

        fun parseLevel(depth: Int): MutableList<WidgetLoftCard> {
            val level = mutableListOf<WidgetLoftCard>()
            while (index < records.size && records[index].depth == depth) {
                val card = records[index].card
                index++
                val children =
                    if (index < records.size && records[index].depth == depth + 1) {
                        parseLevel(depth + 1)
                    } else {
                        emptyList()
                    }
                level.add(card.copy(stackedCards = children))
            }
            return level
        }

        return parseLevel(0)
    }

    private fun zipApps(packages: String, labels: String): List<AppEntry> {
        if (packages.isEmpty()) return emptyList()
        val pkgs = packages.split(',').map { unescape(it) }
        val lbls = labels.split(',').map { unescape(it) }
        return pkgs.mapIndexed { i, pkg -> AppEntry(pkg, lbls.getOrElse(i) { pkg }) }
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(",", "\\c")
        .replace(SEP.toString(), "\\s")
        .replace("\n", "\\n")

    private fun unescape(value: String): String = buildString {
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    '\\' -> append('\\')
                    'c' -> append(',')
                    's' -> append(SEP)
                    'n' -> append('\n')
                    else -> append(value[i + 1])
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}
