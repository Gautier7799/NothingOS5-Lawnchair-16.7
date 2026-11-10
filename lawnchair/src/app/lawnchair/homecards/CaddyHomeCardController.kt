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
    private val hiddenStore: CaddyHomeCardHiddenStore,
) {

    private val context: Context get() = launcher

    private val attached = mutableMapOf<Int, CaddyHomeCardView>()

    // Views need unique ids inside their CellLayout, so assign them sequentially
    // instead of deriving one from the category id (whose hash could collide).
    private val viewIds = mutableMapOf<Int, Int>()
    private var nextViewId = 0

    /** How two cards merge when one is dropped on another: [CaddyHomeCardGrouping.MODE_STACK] or MODE_COMBINE. */
    var mergeMode: String = CaddyHomeCardGrouping.MODE_STACK

    /** Whether workspace cards draw their title. */
    var showTitles: Boolean = true

    /** Icons per row on a workspace card. */
    var columns: Int = 2

    private fun resolveApp(key: String): AppInfo? {
        val componentKey = ComponentKey.fromString(key) ?: return null
        return launcher.appsView?.appsStore?.getApp(componentKey)
    }

    /** Rebuilds the workspace cards from the current app list and category sources. */
    fun sync(apps: List<AppInfo>) {
        val hidden = hiddenStore.load()
        val desired = CaddyHomeCardSource.build(context, apps).filterNot { it.id in hidden }
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
        val cellLayout = cellLayoutFor(current) ?: return false
        val updated = current.copy(spanX = clampedX, spanY = clampedY)
        // Free the card's own cells first, then check the requested region. The grid does not
        // reject overlaps itself, so without this a bigger card could grow over its neighbours.
        detach(id)
        if (!isRegionFree(cellLayout, updated.cellX, updated.cellY, clampedX, clampedY)) {
            attach(current)
            return false
        }
        if (!attach(updated)) {
            attach(current)
            return false
        }
        repository.update(updated)
        return true
    }

    /**
     * Moves one card by a whole number of cells, keeping its size, and persists the new cell.
     *
     * Called after a drag. The target cell is clamped to the page and the move is rejected if
     * another card (or launcher item) already occupies it, so cards never overlap.
     */
    fun move(id: Int, deltaCellsX: Int, deltaCellsY: Int): Boolean {
        if (deltaCellsX == 0 && deltaCellsY == 0) return true
        val current = repository.load().firstOrNull { it.id == id } ?: return false
        val cellLayout = cellLayoutFor(current) ?: return false
        val targetX = current.cellX + deltaCellsX
        val targetY = current.cellY + deltaCellsY
        // Free the card's own cells first, otherwise a small move would collide with itself.
        detach(id)
        if (!isRegionFree(cellLayout, targetX, targetY, current.spanX, current.spanY)) {
            // The drop landed on another card: merge the two instead of rejecting the move.
            val target = cardAt(cellLayout, current.screenId, targetX, targetY, id)
            if (target != null) {
                return mergeOnto(id, target.id)
            }
            attach(current)
            return false
        }
        val moved = current.copy(cellX = targetX, cellY = targetY)
        if (!attach(moved)) {
            attach(current)
            return false
        }
        repository.update(moved)
        return true
    }

    /** The other card whose grid region contains the cell ([x], [y]) on [screenId], if any. */
    private fun cardAt(cellLayout: CellLayout, screenId: Int, x: Int, y: Int, exceptId: Int): CaddyHomeCardInfo? {
        val cards = repository.load()
        val inRangeX = x in 0 until cellLayout.countX
        val inRangeY = y in 0 until cellLayout.countY
        if (!inRangeX || !inRangeY) return null
        return cards.firstOrNull { card ->
            card.id != exceptId && card.screenId == screenId &&
                x >= card.cellX && x < card.cellX + card.spanX &&
                y >= card.cellY && y < card.cellY + card.spanY
        }
    }

    /**
     * Merges the card [draggedId] onto [targetId] using [mergeMode] and repaints the affected cards.
     * STACK keeps both cards' apps and puts them in one place; COMBINE folds them into one card.
     */
    fun mergeOnto(draggedId: Int, targetId: Int): Boolean {
        val before = repository.load()
        val after = CaddyHomeCardGrouping.merge(before, draggedId, targetId, mergeMode)
        if (after == before) return false
        repository.save(after)
        repaint(after)
        return true
    }

    /** Advances the visible card of a stack group, iOS style. Called when a stacked card is tapped. */
    fun cycleGroup(groupId: String): Boolean {
        val before = repository.load()
        val after = CaddyHomeCardGrouping.cycle(before, groupId)
        if (after == before) return false
        repository.save(after)
        repaint(after)
        return true
    }

    /** Detaches every card and re-attaches the given set, used after a merge or cycle. */
    private fun repaint(cards: List<CaddyHomeCardInfo>) {
        val ids = cards.mapTo(mutableSetOf()) { it.id }
        attached.keys.toList().forEach { id -> if (id !in ids) removeView(id) }
        cards.forEach { attach(it) }
    }

    /** True when the [spanX] x [spanY] region at ([x], [y]) is inside the page and unoccupied. */
    private fun isRegionFree(cellLayout: CellLayout, x: Int, y: Int, spanX: Int, spanY: Int): Boolean {
        if (x < 0 || y < 0) return false
        if (x + spanX > cellLayout.countX || y + spanY > cellLayout.countY) return false
        for (cx in x until x + spanX) {
            for (cy in y until y + spanY) {
                if (cellLayout.isOccupied(cx, cy)) return false
            }
        }
        return true
    }

    private fun detach(id: Int) {
        attached.remove(id)?.let { view -> (view.parent as? CellLayout)?.removeView(view) }
        viewIds.remove(id)
    }

    private fun maxSpanX(): Int = (launcher.deviceProfile?.inv?.numColumns ?: MIN_SPAN).coerceAtLeast(MIN_SPAN)

    private fun maxSpanY(): Int = (launcher.deviceProfile?.inv?.numRows ?: MIN_SPAN).coerceAtLeast(MIN_SPAN)

    /**
     * Removes one card from the home screen and remembers it, so it is not re-added on the next
     * sync. Only the card is deleted: its apps, and the drawer category it mirrors, are untouched.
     */
    fun removeCard(id: Int): Boolean {
        val existing = repository.load().firstOrNull { it.id == id } ?: return false
        hiddenStore.hide(id)
        detach(id)
        repository.remove(existing.id)
        return true
    }

    /** Brings a previously removed card back on the next sync. */
    fun restoreCard(id: Int) {
        hiddenStore.unhide(id)
    }

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
        // A stack shows one card at a time, so only the visible member is attached; the
        // others stay persisted and appear when the user cycles the stack.
        if (card.group.isNotEmpty()) {
            val visible = CaddyHomeCardGrouping.visible(repository.load(), card.group)
            if (visible != null && visible.id != card.id) {
                removeView(card.id)
                return true
            }
        }
        val cellLayout = cellLayoutFor(card) ?: return false
        val view = attached.getOrPut(card.id) { CaddyHomeCardView(context) }
        view.showTitle = showTitles
        view.iconColumns = columns
        view.bind(card, ::resolveApp)
        view.onMove = { dx, dy -> move(card.id, dx, dy) }
        view.onResize = { spanX, spanY -> resize(card.id, spanX, spanY) }
        view.onDelete = { removeCard(card.id) }
        view.onGroupTap = { card.group.takeIf { it.isNotEmpty() }?.let { cycleGroup(it) } ?: false }
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
