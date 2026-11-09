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

    /**
     * Compares two app lists by a caller-supplied stable key rather than object identity.
     *
     * [AdapterItem.isContentSame] decides whether a diffed card keeps its current view. Launcher
     * `AppInfo` objects have no `equals`, so the previous identity-based `List.equals` check treated
     * freshly rebuilt but otherwise identical card contents as changed. That forced DiffUtil to
     * rebind every Caddy card on each drawer refresh, which made the preview icons flicker. Comparing
     * by stable key keeps an unchanged card stable.
     */
    fun <T> sameByKey(
        first: List<T>?,
        second: List<T>?,
        keyOf: java.util.function.Function<T, String>,
    ): Boolean {
        if (first == null || second == null) return first === second
        if (first.size != second.size) return false
        for (index in first.indices) {
            if (keyOf.apply(first[index]) != keyOf.apply(second[index])) return false
        }
        return true
    }

    /** Stable component key for a launcher app, independent of object identity. */
    fun appKey(app: com.android.launcher3.model.data.AppInfo): String = app.componentName?.let { "${app.user.hashCode()}:${it.flattenToString()}" }
        ?: "${app.user.hashCode()}:${app.targetPackage}"

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
