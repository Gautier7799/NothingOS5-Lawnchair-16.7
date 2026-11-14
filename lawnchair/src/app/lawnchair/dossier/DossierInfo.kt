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

import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.ContentWriter
import org.json.JSONArray
import org.json.JSONObject

/**
 * Database-backed Dossier workspace item.
 *
 * Placement and spans use the existing Favorites columns inherited from ItemInfo.
 * The existing INTENT text column stores the Dossier-specific app keys and grid
 * limits, so no database schema migration is required.
 */
class DossierInfo(
    title: String,
    appKeys: List<String>,
    gridColumns: Int = DEFAULT_GRID_COLUMNS,
    gridRows: Int = DEFAULT_GRID_ROWS,
) : ItemInfo() {

    val appKeys: List<String> = appKeys.toList()
    val gridColumns: Int = gridColumns.coerceIn(MIN_GRID_SIZE, MAX_GRID_SIZE)
    val gridRows: Int = gridRows.coerceIn(MIN_GRID_SIZE, MAX_GRID_SIZE)

    init {
        itemType = Favorites.ITEM_TYPE_DOSSIER
        this.title = title
        spanX = DEFAULT_SPAN_X
        spanY = DEFAULT_SPAN_Y
    }

    override fun onAddToDatabase(writer: ContentWriter) {
        super.onAddToDatabase(writer)
        writer.put(Favorites.TITLE, title?.toString().orEmpty())
        writer.put(Favorites.INTENT, encodePayload())
    }

    private fun encodePayload(): String = JSONObject()
        .put("version", PAYLOAD_VERSION)
        .put("gridColumns", gridColumns)
        .put("gridRows", gridRows)
        .put("apps", JSONArray(appKeys))
        .toString()

    companion object {
        const val DEFAULT_SPAN_X = 2
        const val DEFAULT_SPAN_Y = 2
        const val DEFAULT_GRID_COLUMNS = 3
        const val DEFAULT_GRID_ROWS = 3
        const val MIN_GRID_SIZE = 1
        const val MAX_GRID_SIZE = 8
        private const val PAYLOAD_VERSION = 1

        /** Returns null for unknown or malformed payloads so the loader can discard them safely. */
        @JvmStatic
        fun fromDatabase(title: String?, payload: String?): DossierInfo? = runCatching {
            if (payload.isNullOrBlank()) return null
            val json = JSONObject(payload)
            if (json.optInt("version", -1) != PAYLOAD_VERSION) return null
            val apps = json.optJSONArray("apps") ?: return null
            val keys = buildList {
                for (index in 0 until apps.length()) {
                    val key = apps.optString(index).trim()
                    if (key.isNotEmpty()) add(key)
                }
            }
            DossierInfo(
                title = title.orEmpty(),
                appKeys = keys,
                gridColumns = json.optInt("gridColumns", DEFAULT_GRID_COLUMNS),
                gridRows = json.optInt("gridRows", DEFAULT_GRID_ROWS),
            )
        }.getOrNull()
    }
}
