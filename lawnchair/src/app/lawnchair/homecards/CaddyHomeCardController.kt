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

import android.content.Context
import com.android.launcher3.CellLayout
import com.android.launcher3.Launcher
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.ComponentKey

/**
 * Places the Caddy cards on the workspace and keeps them in sync with the drawer.
 *
 * Cards are native workspace views hosted in a [CellLayout] and occupy a 2x2 grid
 * area, matching the drawer card's 2x2 app arrangement. Card content comes from
 * [CaddyHomeCardSource]; only grid placement is persisted here, so app data is
 * never duplicated. Existing folders, shortcuts and widgets are untouched.
 */
class CaddyHomeCardController(
    private val launcher: Launcher,
    private val repository: CaddyHomeCardRepository,
) {

    private val context: Context get() = launcher

    private val attached = mutableMapOf<Int, CaddyHomeCardView>()

    // Views need unique ids inside their CellLayout, so assign them sequentially
    // instead of deriving one from the category id (whose hash could collide).
    private val viewIds = mutableMapOf<Int, Int>()
    private var nextViewId = 0

    private fun resolveApp(key: String): AppInfo? {
        val componentKey = ComponentKey.fromString(key) ?: return null
        return launcher.appsView?.appsStore?.getApp(componentKey)
    }

    /** Rebuilds the workspace cards from the current app list and category sources. */
    fun sync(apps: List<AppInfo>) {
        val desired = CaddyHomeCardSource.build(context, apps)
        if (desired.isEmpty()) {
            detachAll()
            repository.save(emptyList())
            return
        }

        val placementById = repository.load().associateBy { it.id }
        val desiredIds = desired.mapTo(mutableSetOf()) { it.id }

        // Drop views whose category no longer exists.
        attached.keys.toList().forEach { id ->
            if (id !in desiredIds) removeView(id)
        }

        val persisted = mutableListOf<CaddyHomeCardInfo>()
        desired.forEach { card ->
            val existing = placementById[card.id]
            val placed = if (existing != null) {
                card.copy(
                    screenId = existing.screenId,
                    cellX = existing.cellX,
                    cellY = existing.cellY,
                    spanX = existing.spanX,
                    spanY = existing.spanY,
                )
            } else {
                placeAtFreeCells(card) ?: return@forEach
            }
            attach(placed)
            persisted.add(placed)
        }
        repository.save(persisted)
    }

    /**
     * Persists a new grid size for one card and re-lays out its view in place.
     *
     * Called by the card's resize handle. The card keeps its top-left cell and grows or shrinks
     * to the right and downwards; the launcher grid clamps the result to the page bounds.
     */
    fun resize(id: Int, spanX: Int, spanY: Int): Boolean {
        val current = repository.load().firstOrNull { it.id == id } ?: return false
        val clampedX = spanX.coerceIn(MIN_SPAN, maxSpanX())
        val clampedY = spanY.coerceIn(MIN_SPAN, maxSpanY())
        if (clampedX == current.spanX && clampedY == current.spanY) return true
        val updated = current.copy(spanX = clampedX, spanY = clampedY)
        if (!reattach(updated)) {
            // The requested size does not fit next to its neighbours. Put the card
            // back at its previous size instead of letting it disappear.
            reattach(current)
            return false
        }
        repository.update(updated)
        return true
    }

    private fun reattach(card: CaddyHomeCardInfo): Boolean {
        val view = attached[card.id] ?: return false
        (view.parent as? CellLayout)?.removeView(view)
        attached.remove(card.id)
        viewIds.remove(card.id)
        return attach(card)
    }

    private fun maxSpanX(): Int = (launcher.deviceProfile?.inv?.numColumns ?: MIN_SPAN).coerceAtLeast(MIN_SPAN)

    private fun maxSpanY(): Int = (launcher.deviceProfile?.inv?.numRows ?: MIN_SPAN).coerceAtLeast(MIN_SPAN)

    fun detachAll() {
        attached.keys.toList().forEach(::removeView)
        viewIds.clear()
    }

    private fun removeView(id: Int) {
        attached.remove(id)?.let { view ->
            (view.parent as? CellLayout)?.removeView(view)
        }
        viewIds.remove(id)
    }

    private fun placeAtFreeCells(card: CaddyHomeCardInfo): CaddyHomeCardInfo? {
        val placement = findPlacement(card.spanX, card.spanY) ?: return null
        return card.copy(
            screenId = placement.first,
            cellX = placement.second[0],
            cellY = placement.second[1],
        )
    }

    private fun attach(card: CaddyHomeCardInfo): Boolean {
        val cellLayout = cellLayoutFor(card) ?: return false
        val view = attached.getOrPut(card.id) {
            CaddyHomeCardView(context).apply {
                // Consume the long press so the workspace never tries to drag a
                // card that carries no launcher ItemInfo tag.
                onCardLongClick = { true }
            }
        }
        view.bind(card, ::resolveApp)
        view.onResize = { spanX, spanY -> resize(card.id, spanX, spanY) }
        if (view.parent == null) {
            val params = CellLayoutLayoutParams(card.cellX, card.cellY, card.spanX, card.spanY)
            val viewId = viewIds.getOrPut(card.id) { nextViewId++ }
            if (!cellLayout.addViewToCellLayout(view, -1, viewId, params, true)) {
                attached.remove(card.id)
                viewIds.remove(card.id)
                return false
            }
        }
        return true
    }

    private fun cellLayoutFor(card: CaddyHomeCardInfo): CellLayout? {
        val workspace = launcher.workspace ?: return null
        val screenOrder = workspace.screenOrder
        if (screenOrder.size() == 0) return null
        val screenId = if (card.screenId >= 0) card.screenId else screenOrder.get(0)
        return workspace.getScreenWithId(screenId)
    }

    /** Finds a free region of [spanX] x [spanY] cells, adding a new page when all pages are full. */
    private fun findPlacement(spanX: Int, spanY: Int): Pair<Int, IntArray>? {
        val workspace = launcher.workspace ?: return null
        val screenOrder = workspace.screenOrder
        for (index in 0 until screenOrder.size()) {
            val screenId = screenOrder.get(index)
            val cellLayout = workspace.getScreenWithId(screenId) ?: continue
            val cellXY = IntArray(2)
            if (cellLayout.findCellForSpan(cellXY, spanX, spanY)) {
                return screenId to cellXY
            }
        }
        var maxScreenId = 0
        for (index in 0 until screenOrder.size()) {
            maxScreenId = maxOf(maxScreenId, screenOrder.get(index))
        }
        val newScreenId = maxScreenId + 1
        workspace.insertNewWorkspaceScreen(newScreenId)
        val cellLayout = workspace.getScreenWithId(newScreenId) ?: return null
        val cellXY = IntArray(2)
        return if (cellLayout.findCellForSpan(cellXY, spanX, spanY)) {
            newScreenId to cellXY
        } else {
            null
        }
    }

    private companion object {
        const val MIN_SPAN = 2
    }
}
