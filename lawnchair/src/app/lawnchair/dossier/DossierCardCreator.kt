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
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.data.ItemInfo

/**
 * Builds a new Dossier card when the user drops one app icon onto another.
 *
 * This is the model-side half of the original Dossier idea: the card is born from
 * the user's own gesture instead of a pre-seeded template. The view assembly
 * (removing the two icons, adding the card) stays in {@code Workspace} because it
 * needs package-private {@code CellLayout} access; this object only decides
 * whether a card should be created and produces the persisted [DossierInfo].
 */
object DossierCardCreator {

    /** True when the Dossier feature is enabled in preferences. */
    @JvmStatic
    fun isEnabled(context: Context): Boolean = PreferenceManager2.getInstance(context).dossierEnabled.firstCached()

    /** True for the item types that may be grouped into a card. */
    @JvmStatic
    fun isGroupableApp(info: ItemInfo?): Boolean = info != null &&
        (
            info.itemType == Favorites.ITEM_TYPE_APPLICATION ||
                info.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT
            )

    /** Stable component key for [info], or null when it cannot be grouped. */
    @JvmStatic
    fun componentKeyOf(info: ItemInfo?): String? = info?.componentKey?.toString()

    /**
     * Builds the card that groups [targetInfo] and [draggedInfo].
     *
     * Returns null when either side is not a groupable app, when they resolve to
     * the same app, or when a key is missing — the caller then leaves the drop to
     * the ordinary paths. The returned item is not yet written to the database.
     */
    @JvmStatic
    fun build(
        context: Context,
        targetInfo: ItemInfo?,
        draggedInfo: ItemInfo?,
        spanX: Int,
        spanY: Int,
    ): DossierInfo? {
        if (targetInfo == null || draggedInfo == null) return null
        if (!isGroupableApp(targetInfo) || !isGroupableApp(draggedInfo)) return null
        val targetKey = componentKeyOf(targetInfo) ?: return null
        val draggedKey = componentKeyOf(draggedInfo) ?: return null
        if (targetKey == draggedKey) return null

        val preferences = PreferenceManager2.getInstance(context)
        val columns = preferences.dossierGridColumns.firstCached()
            .coerceIn(DossierInfo.MIN_GRID_SIZE, DossierInfo.MAX_GRID_SIZE)
        val rows = preferences.dossierGridRows.firstCached()
            .coerceIn(DossierInfo.MIN_GRID_SIZE, DossierInfo.MAX_GRID_SIZE)
        val title = DossierCreation.defaultTitle(listOf(targetInfo.title, draggedInfo.title))

        return DossierInfo(
            title = title,
            appKeys = listOf(targetKey, draggedKey),
            gridColumns = columns,
            gridRows = rows,
        ).apply {
            this.spanX = spanX.coerceAtLeast(1)
            this.spanY = spanY.coerceAtLeast(1)
        }
    }
}
