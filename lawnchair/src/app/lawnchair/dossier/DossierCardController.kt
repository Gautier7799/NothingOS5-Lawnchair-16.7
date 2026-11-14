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
import com.android.launcher3.CellLayout
import com.android.launcher3.Launcher
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.ComponentKey

/**
 * Places the Dossier cards on the workspace and keeps them in sync with the drawer.
 *
 * Cards are native workspace views hosted in a [CellLayout] and occupy their own
 * grid area (2x2 by default, resizable). Card content comes from
 * [DossierCardSource]; only grid placement is persisted here, so app data is
 * never duplicated. Existing folders, shortcuts and widgets are untouched.
 */
class DossierCardController(
    private val launcher: Launcher,
    private val repository: DossierCardRepository,
) {

    private val context: Context get() = launcher

    private val attached = mutableMapOf<Int, DossierCardView>()

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
        if (apps.isEmpty()) return

        // Seed Home from drawer categories only for a fresh installation.
        // Once saved, Home owns its cards independently of drawer categories.
        val storedCards = repository.load()
        val desired = if (storedCards.isEmpty()) {
            DossierCardSource.build(context, apps).also(repository::save)
        } else {
            storedCards
        }
        if (desired.isEmpty()) {
            detachAll()
            return
        }

        val desiredIds = desired.mapTo(mutableSetOf()) { it.id }
        attached.keys.toList().forEach { id ->
            if (id !in desiredIds) removeView(id)
        }

        val persisted = mutableListOf<DossierCardInfo>()
        desired.forEach { card ->
            val placed = if (card.isPlaced) {
                card.withClampedSpan()
            } else {
                placeAtFreeCells(card) ?: return@forEach
            }
            attach(placed)
            persisted.add(placed)
        }
        repository.save(persisted)
    }

    fun detachAll() {
        attached.keys.toList().forEach(::removeView)
        viewIds.clear()
    }

    /**
     * Applies a new grid placement to an existing card and persists it. Called by
     * the resize UI when the user finishes resizing a Dossier.
     */
    fun updatePlacement(card: DossierCardInfo): Boolean {
        val clamped = card.withClampedSpan()
        repository.update(clamped)
        attach(clamped)
        return true
    }

    /** Returns the placement currently stored for [id], or null if the card is gone. */
    fun placementOf(id: Int): DossierCardInfo? = repository.load().firstOrNull { it.id == id }

    private fun removeView(id: Int) {
        attached.remove(id)?.let { view ->
            (view.parent as? CellLayout)?.removeView(view)
        }
        viewIds.remove(id)
    }

    private fun placeAtFreeCells(card: DossierCardInfo): DossierCardInfo? {
        val placement = findPlacement(card.spanX, card.spanY) ?: return null
        return card.copy(
            screenId = placement.first,
            cellX = placement.second[0],
            cellY = placement.second[1],
        )
    }

    private fun attach(card: DossierCardInfo) {
        val cellLayout = cellLayoutFor(card) ?: return
        val view = attached.getOrPut(card.id) {
            DossierCardView(context).apply {
                onCardResizeRequested = { requestResize(this, card.id) }
            }
        }
        view.bind(card, ::resolveApp)
        val currentParams = view.layoutParams as? CellLayoutLayoutParams
        val placementChanged = view.parent !== cellLayout ||
            currentParams?.cellX != card.cellX ||
            currentParams?.cellY != card.cellY ||
            currentParams?.cellHSpan != card.spanX ||
            currentParams?.cellVSpan != card.spanY
        if (placementChanged) {
            (view.parent as? CellLayout)?.removeView(view)
            val params = CellLayoutLayoutParams(card.cellX, card.cellY, card.spanX, card.spanY)
            val viewId = viewIds.getOrPut(card.id) { nextViewId++ }
            if (!cellLayout.addViewToCellLayout(view, -1, viewId, params, true)) {
                attached.remove(card.id)
                viewIds.remove(card.id)
            }
        }
    }

    private fun requestResize(view: DossierCardView, id: Int) {
        val card = placementOf(id) ?: view.currentCard() ?: return
        val cellLayout = view.parent as? CellLayout ?: return
        DossierResizeFrame.showForCard(
            launcher = launcher,
            cardView = view,
            card = card,
            cellLayout = cellLayout,
            onResized = { updated -> updatePlacement(updated) },
        )
    }

    private fun cellLayoutFor(card: DossierCardInfo): CellLayout? {
        val workspace = launcher.workspace ?: return null
        val screenOrder = workspace.screenOrder
        if (screenOrder.size() == 0) return null
        val screenId = if (card.isPlaced) card.screenId else screenOrder.get(0)
        return workspace.getScreenWithId(screenId)
    }

    /** Finds a free region, adding a new page when every existing page is full. */
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
}
