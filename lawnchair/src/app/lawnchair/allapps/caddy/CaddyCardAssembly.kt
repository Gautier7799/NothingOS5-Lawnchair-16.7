package app.lawnchair.allapps.caddy

/**
 * A user-built Caddy card: a title plus the component keys of the apps the user assembled.
 *
 * Kept free of Android types and JSON so the assembly rules can be unit tested on the JVM.
 */
data class CaddyCardSpec(
    val title: String,
    val appKeys: List<String>,
)

/**
 * Rules for building a Caddy card "from scratch" by assembling apps.
 *
 * A card is created from an arbitrary set of apps the user picked (one or more). The engine
 * normalizes that input so every stored card is consistent regardless of where it was built
 * (the drawer editor or a future workspace assembly gesture):
 *
 *  - the title is trimmed and must not be blank;
 *  - at least [MIN_APPS] app must be selected;
 *  - duplicate keys are removed, preserving the user's selection order;
 *  - an existing card with the same title is replaced instead of duplicated.
 */
object CaddyCardAssembly {

    /** A user-built card must contain at least this many apps. */
    const val MIN_APPS = 1

    /**
     * Validates and normalizes a single assembly. Returns null when the input cannot form a card
     * (blank title, or fewer than [MIN_APPS] distinct apps).
     */
    fun assemble(title: String?, appKeys: List<String>?): CaddyCardSpec? {
        val cleanTitle = title?.trim().orEmpty()
        if (cleanTitle.isEmpty()) return null
        val keys = normalizeKeys(appKeys)
        if (keys.size < MIN_APPS) return null
        return CaddyCardSpec(cleanTitle, keys)
    }

    /**
     * Inserts [spec] into [existing], replacing a card with the same title so a rebuilt card
     * never duplicates. The returned list keeps the original order with the rebuilt card in place.
     */
    fun mergeInto(existing: List<CaddyCardSpec>, spec: CaddyCardSpec): List<CaddyCardSpec> {
        val result = ArrayList<CaddyCardSpec>(existing.size + 1)
        var replaced = false
        for (card in existing) {
            if (card.title == spec.title) {
                if (!replaced) {
                    result.add(spec)
                    replaced = true
                }
            } else {
                result.add(card)
            }
        }
        if (!replaced) result.add(spec)
        return result
    }

    /**
     * Convenience for the create flow: assembles [title]/[appKeys] and merges the result into
     * [existing]. Returns null when the input is not a valid card, leaving [existing] untouched.
     */
    fun assembleAndMerge(
        existing: List<CaddyCardSpec>,
        title: String?,
        appKeys: List<String>?,
    ): List<CaddyCardSpec>? {
        val spec = assemble(title, appKeys) ?: return null
        return mergeInto(existing, spec)
    }

    /** Removes blank keys and duplicates, preserving first-seen order. */
    fun normalizeKeys(appKeys: List<String>?): List<String> {
        if (appKeys.isNullOrEmpty()) return emptyList()
        val seen = LinkedHashSet<String>(appKeys.size)
        for (key in appKeys) {
            val trimmed = key.trim()
            if (trimmed.isNotEmpty()) seen.add(trimmed)
        }
        return seen.toList()
    }
}
