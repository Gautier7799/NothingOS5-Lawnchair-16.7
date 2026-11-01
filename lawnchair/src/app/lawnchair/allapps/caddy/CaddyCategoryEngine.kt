package app.lawnchair.allapps.caddy

/**
 * Immutable presentation model for a Caddy category card.
 *
 * This model deliberately contains no FolderInfo, FolderIcon, or Folder lifecycle state.
 * The launcher can continue supplying its native AppInfo objects while Caddy owns
 * category grouping and presentation independently.
 */
data class CaddyCategoryGroup<T>(
    val id: String,
    val title: String,
    val apps: List<T>,
)

/**
 * Builds a stable, complete set of Caddy category groups.
 *
 * Every app from [allApps] is assigned to at most one category. Apps omitted by the
 * classifier are placed in "Other"; duplicate assignments are ignored. The caller
 * supplies a stable key (for example, a component/package key) so app identity does
 * not depend on object instance equality.
 */
object CaddyCategoryEngine {

    fun <T, K : Any> buildGroups(
        allApps: List<T>,
        categorizedApps: Map<String, List<T>>,
        keyOf: (T) -> K,
        otherTitle: String = "Other",
    ): List<CaddyCategoryGroup<T>> {
        if (allApps.isEmpty()) return emptyList()

        val availableApps = LinkedHashMap<K, T>()
        allApps.forEach { app ->
            availableApps.putIfAbsent(keyOf(app), app)
        }

        val assignedKeys = LinkedHashSet<K>()
        val result = mutableListOf<CaddyCategoryGroup<T>>()
        val otherApps = mutableListOf<T>()

        categorizedApps.forEach { (category, apps) ->
            if (category == otherTitle) {
                apps.forEach { app ->
                    val key = keyOf(app)
                    val canonicalApp = availableApps[key]
                    if (canonicalApp != null && assignedKeys.add(key)) {
                        otherApps.add(canonicalApp)
                    }
                }
            } else {
                val categoryApps = mutableListOf<T>()
                apps.forEach { app ->
                    val key = keyOf(app)
                    val canonicalApp = availableApps[key]
                    if (canonicalApp != null && assignedKeys.add(key)) {
                        categoryApps.add(canonicalApp)
                    }
                }
                if (categoryApps.isNotEmpty()) {
                    result.add(
                        CaddyCategoryGroup(
                            id = category,
                            title = category,
                            apps = categoryApps.toList(),
                        ),
                    )
                }
            }
        }

        availableApps.forEach { (key, app) ->
            if (assignedKeys.add(key)) otherApps.add(app)
        }

        if (otherApps.isNotEmpty()) {
            result.add(
                CaddyCategoryGroup(
                    id = "other",
                    title = otherTitle,
                    apps = otherApps.toList(),
                ),
            )
        }

        return result
    }
}
