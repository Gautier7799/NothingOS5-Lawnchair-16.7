/*
 * Copyright 2026, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.dossier

import android.content.Context
import app.lawnchair.util.categorizeAppsWithSystemAndGoogle
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.ComponentKey

/**
 * Builds the Dossier cards that belong on the workspace.
 *
 * Content is derived from the same sources the app drawer uses, so a fresh
 * installation gets exactly the drawer's category cards: the user's persisted
 * custom categories first, then the automatic categories, with single-app
 * categories folded into "Other" the way the drawer does. Nothing about the
 * content is invented; the drawer remains the place where categories are created
 * and edited, and this source mirrors the result.
 */
object DossierCardSource {

    /** Preference file and key the drawer uses for persisted custom categories. */
    const val CUSTOM_CATEGORIES_PREFS = "caddy_custom_categories"
    const val CUSTOM_CATEGORIES_KEY = "categories"

    private const val OTHER_TITLE = "Other"

    /**
     * Stable identity for an app, matching [ComponentKey] so a stored key can be
     * resolved back to the live launcher app with [ComponentKey.fromString].
     */
    fun appKeyOf(app: AppInfo): String? = app.componentName?.let { ComponentKey(it, app.user).toString() }

    fun build(context: Context, apps: List<AppInfo>): List<DossierCardInfo> {
        if (apps.isEmpty()) return emptyList()

        val customCategories = loadCustomCategories(context, apps)
        val customAssignedKeys = customCategories
            .flatMap { it.second }
            .mapNotNull { appKeyOf(it) }
            .toSet()

        val automaticApps = apps.filterNot { appKeyOf(it) in customAssignedKeys }

        val categories = linkedMapOf<String, MutableList<AppInfo>>()
        customCategories.forEach { (title, categoryApps) ->
            categories.getOrPut(title) { mutableListOf() }.addAll(categoryApps)
        }

        categorizeAppsWithSystemAndGoogle(automaticApps, context).forEach { (title, categoryApps) ->
            // Mirror the drawer: categories with a single app collapse into "Other"
            // so every rendered card shows at least two apps.
            when {
                categoryApps.size > 1 ->
                    categories.getOrPut(title) { mutableListOf() }.addAll(categoryApps)

                categoryApps.size == 1 ->
                    categories.getOrPut(OTHER_TITLE) { mutableListOf() }.addAll(categoryApps)
            }
        }

        return categories
            .filterValues { it.isNotEmpty() }
            .map { (title, categoryApps) ->
                DossierCardInfo(
                    id = title.hashCode(),
                    title = title,
                    appKeys = categoryApps.mapNotNull(::appKeyOf),
                )
            }
    }

    private fun loadCustomCategories(
        context: Context,
        apps: List<AppInfo>,
    ): List<Pair<String, List<AppInfo>>> {
        val result = mutableListOf<Pair<String, List<AppInfo>>>()
        runCatching {
            val stored = context.getSharedPreferences(CUSTOM_CATEGORIES_PREFS, Context.MODE_PRIVATE)
                .getString(CUSTOM_CATEGORIES_KEY, "[]") ?: "[]"
            val json = org.json.JSONArray(stored)
            for (index in 0 until json.length()) {
                val category = json.optJSONObject(index) ?: continue
                val title = category.optString("title").trim()
                val keys = category.optJSONArray("apps") ?: continue
                if (title.isEmpty()) continue
                val selectedKeys = (0 until keys.length()).mapNotNull { keys.optString(it) }
                val selectedApps = apps.filter { app ->
                    val key = appKeyOf(app)
                    val legacy = app.componentName?.flattenToString()
                    key in selectedKeys || (legacy != null && legacy in selectedKeys)
                }
                if (selectedApps.isNotEmpty()) {
                    result.add(title to selectedApps)
                }
            }
        }
        return result
    }
}
