package app.lawnchair.util

import android.content.Context
import app.lawnchair.flowerpot.Flowerpot
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.ApplicationInfoWrapper
import java.util.Locale

/**
 * Categorizes apps into System Apps, Google Apps, and Flowerpot categories.
 *
 * Each app is included at most once, and apps within a category are ordered
 * consistently so the drawer does not jump between refreshes.
 */
fun categorizeAppsWithSystemAndGoogle(
    apps: List<AppInfo>,
    context: Context,
): Map<String, List<AppInfo>> {
    val systemApps = mutableListOf<AppInfo>()
    val googleApps = mutableListOf<AppInfo>()
    val otherApps = mutableListOf<AppInfo>()
    val seenKeys = mutableSetOf<String>()

    apps.forEach { app ->
        val packageName = app.targetPackage ?: return@forEach
        val componentKey = app.toComponentKey().toString()
        if (!seenKeys.add(componentKey)) return@forEach

        val intent = app.intent

        // Check Google apps first because some are also preinstalled system apps.
        when {
            packageName.startsWith("com.google.") -> googleApps.add(app)
            intent != null && ApplicationInfoWrapper(context, intent).isSystem() -> systemApps.add(app)
            else -> otherApps.add(app)
        }
    }

    val result = linkedMapOf<String, List<AppInfo>>()

    fun addCategory(name: String, categoryApps: List<AppInfo>) {
        if (categoryApps.isNotEmpty()) {
            result[name] = categoryApps.sortedBy { it.title.toString().lowercase(Locale.ROOT) }
        }
    }

    addCategory("System Apps", systemApps)
    addCategory("Google Apps", googleApps)

    // Categorize remaining apps using Lawnchair's existing Flowerpot rules.
    val categorizedApps = Flowerpot.Manager.getInstance(context).categorizeApps(otherApps)
    val categorizedKeys = seenKeys.filter { key ->
        apps.any { it.toComponentKey().toString() == key && it !in otherApps }
    }.toMutableSet()

    categorizedApps.toSortedMap(String.CASE_INSENSITIVE_ORDER).forEach { (category, categoryApps) ->
        val uniqueApps = categoryApps.filter { app ->
            categorizedKeys.add(app.toComponentKey().toString())
        }
        addCategory(category, uniqueApps)
    }

    // Keep any app that no rule recognized accessible in a catch-all category.
    val categorizedOtherKeys = categorizedApps.values.flatten()
        .mapTo(mutableSetOf()) { it.toComponentKey().toString() }
    val uncategorized = otherApps.filter { it.toComponentKey().toString() !in categorizedOtherKeys }
    addCategory("Other", uncategorized)

    return result
}
