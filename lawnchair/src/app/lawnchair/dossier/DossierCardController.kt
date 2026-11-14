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
import com.android.launcher3.CellLayout
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.data.AppInfo

/**
 * Migrates the legacy Dossier card list into real Favorites workspace rows.
 *
 * Once inserted, LauncherModel owns placement, occupancy, view binding and
 * persistence. This controller only seeds the initial set and never attaches
 * unmanaged views to CellLayout.
 */
class DossierCardController(
    private val launcher: Launcher,
    private val repository: DossierCardRepository,
) {
    private val context: Context get() = launcher
    private val preferences by lazy { PreferenceManager2.getInstance(launcher) }
    private var seedRequested = false

    /** Seed the workspace once; subsequent launches load DossierInfo from Favorites. */
    fun sync(apps: List<AppInfo>) {
        if (apps.isEmpty() || seedRequested) return
        val workspace = launcher.workspace ?: return
        if (workspace.screenOrder.size() == 0) return
        if (hasBoundDossier(workspace)) {
            seedRequested = true
            return
        }

        val storedCards = repository.load()
        val desired = if (storedCards.isEmpty()) {
            DossierCardSource.build(context, apps).also(repository::save)
        } else {
            storedCards
        }
        if (desired.isEmpty()) return

        val columns = preferences.dossierGridColumns.firstCached()
            .coerceIn(DossierInfo.MIN_GRID_SIZE, DossierInfo.MAX_GRID_SIZE)
        val rows = preferences.dossierGridRows.firstCached()
            .coerceIn(DossierInfo.MIN_GRID_SIZE, DossierInfo.MAX_GRID_SIZE)

        var inserted = false
        desired.forEach { card ->
            val placement = findPlacement(card.spanX, card.spanY) ?: return@forEach
            val info = DossierInfo(
                title = card.title,
                appKeys = card.appKeys,
                gridColumns = columns,
                gridRows = rows,
            ).apply {
                spanX = card.spanX.coerceIn(DossierCardInfo.MIN_SPAN, DossierCardInfo.MAX_SPAN)
                spanY = card.spanY.coerceIn(DossierCardInfo.MIN_SPAN, DossierCardInfo.MAX_SPAN)
            }
            launcher.modelWriter.addItemToDatabase(
                info,
                Favorites.CONTAINER_DESKTOP,
                placement.first,
                placement.second[0],
                placement.second[1],
            )
            inserted = true
        }
        seedRequested = inserted
    }

    /** No manually attached views remain; the model owns their lifecycle. */
    fun detachAll() = Unit

    private fun hasBoundDossier(workspace: com.android.launcher3.Workspace): Boolean {
        val order = workspace.screenOrder
        for (index in 0 until order.size()) {
            val cellLayout = workspace.getScreenWithId(order.get(index)) ?: continue
            val children = cellLayout.shortcutsAndWidgets
            for (childIndex in 0 until children.childCount) {
                if (children.getChildAt(childIndex)?.tag is DossierInfo) return true
            }
        }
        return false
    }

    private fun findPlacement(spanX: Int, spanY: Int): Pair<Int, IntArray>? {
        val workspace = launcher.workspace ?: return null
        val order = workspace.screenOrder
        for (index in 0 until order.size()) {
            val screenId = order.get(index)
            val cellLayout = workspace.getScreenWithId(screenId) ?: continue
            val cellXY = IntArray(2)
            if (cellLayout.findCellForSpan(cellXY, spanX, spanY)) {
                return screenId to cellXY
            }
        }

        var maxScreenId = 0
        for (index in 0 until order.size()) {
            maxScreenId = maxOf(maxScreenId, order.get(index))
        }
        val newScreenId = maxScreenId + 1
        workspace.insertNewWorkspaceScreen(newScreenId)
        val newLayout: CellLayout = workspace.getScreenWithId(newScreenId) ?: return null
        val cellXY = IntArray(2)
        return if (newLayout.findCellForSpan(cellXY, spanX, spanY)) {
            newScreenId to cellXY
        } else {
            null
        }
    }
}
