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

package app.lawnchair.homecards

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads and writes the custom Caddy categories stored by the drawer.
 *
 * The on-disk shape is fixed by [com.android.launcher3.allapps.BaseAllAppsAdapter]: a JSON array of
 * `{"title": String, "apps": [componentKey, ...]}`. Everything here is pure and Android-free so the
 * format stays verifiable on the JVM; the caller owns the [android.content.SharedPreferences] IO.
 */
object CaddyCategoryStore {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Appends a category to [existing] and returns the new JSON array as a string.
     *
     * A blank title or an empty app list is a no-op, and unparsable [existing] input is treated as
     * empty rather than throwing, so a corrupted preference cannot block creating a category.
     */
    fun addCategory(existing: String?, title: String, appKeys: List<String>): String {
        val cleanTitle = title.trim()
        val cleanKeys = appKeys.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanTitle.isEmpty() || cleanKeys.isEmpty()) return existing ?: EMPTY_ARRAY
        val categories = parse(existing).toMutableList()
        categories.add(
            buildJsonObject {
                put("title", JsonPrimitive(cleanTitle))
                put(
                    "apps",
                    buildJsonArray { cleanKeys.forEach { add(JsonPrimitive(it)) } },
                )
            },
        )
        return JsonArray(categories).toString()
    }

    /** The titles currently stored in [existing], in order. */
    fun titles(existing: String?): List<String> = parse(existing).mapNotNull { it["title"]?.jsonPrimitive?.content }

    /**
     * Removes every category whose title equals [title] and returns the new JSON array as a string.
     * A blank title or unparsable [existing] input leaves the stored value unchanged.
     */
    fun removeCategory(existing: String?, title: String): String {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return existing ?: EMPTY_ARRAY
        val remaining = parse(existing).filterNot { it["title"]?.jsonPrimitive?.content == cleanTitle }
        return JsonArray(remaining).toString()
    }

    private fun parse(existing: String?): List<JsonObject> {
        if (existing.isNullOrBlank()) return emptyList()
        return runCatching { json.parseToJsonElement(existing).jsonArray }
            .getOrElse { return emptyList() }
            .mapNotNull { element -> runCatching { element.jsonObject }.getOrNull() }
    }

    private const val EMPTY_ARRAY = "[]"
}
