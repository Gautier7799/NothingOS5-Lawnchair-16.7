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

/**
 * Android-free bookkeeping for the Caddy cards hosted on the workspace.
 *
 * It owns the two facts the controller needs and that previously lived in loose maps that
 * could silently desynchronise from the view hierarchy:
 *
 *  - one stable view token per card id, so a resize or a move reuses the existing view
 *    instead of building a second one (the duplicate-view defect); and
 *  - which cells each attached card occupies, so releasing a card frees exactly its cells
 *    and a later placement can reuse them.
 *
 * Keeping this free of Android types lets the invariants be unit tested on the JVM.
 */
class CaddyHomeCardPlacement {

    /** card id -> view token. Presence means a view is attached for that card. */
    private val tokens = LinkedHashMap<Int, Int>()

    /** screen id -> the cards occupying that screen. */
    private val occupancy = LinkedHashMap<Int, MutableMap<Int, CaddyHomeCardInfo>>()

    private var nextToken = 0

    /** The stable view token for [id]; the same token is returned until [release] is called. */
    fun tokenFor(id: Int): Int = tokens.getOrPut(id) { nextToken++ }

    fun isAttached(id: Int): Boolean = tokens.containsKey(id)

    fun attachedIds(): List<Int> = tokens.keys.toList()

    fun attachedCount(): Int = tokens.size

    /** Marks [card] as attached and occupying its cells on its screen. */
    fun occupy(card: CaddyHomeCardInfo) {
        tokens.getOrPut(card.id) { nextToken++ }
        occupancy.getOrPut(card.screenId) { LinkedHashMap() }[card.id] = card
    }

    /** Frees [id]'s cells but keeps its view token, so the existing view can be re-hosted. */
    fun releaseCells(id: Int) {
        occupancy.values.forEach { it.remove(id) }
        occupancy.entries.removeAll { it.value.isEmpty() }
    }

    /** Frees [id]'s cells and forgets its view token. Returns true when it was attached. */
    fun release(id: Int): Boolean {
        if (tokens.remove(id) == null) return false
        releaseCells(id)
        return true
    }

    fun clear() {
        tokens.clear()
        occupancy.clear()
    }

    /** True when the [spanX] x [spanY] region at ([x], [y]) is inside [columns] x [rows] and free. */
    fun isRegionFree(
        screenId: Int,
        x: Int,
        y: Int,
        spanX: Int,
        spanY: Int,
        columns: Int,
        rows: Int,
    ): Boolean {
        if (x < 0 || y < 0) return false
        if (x + spanX > columns || y + spanY > rows) return false
        val onScreen = occupancy[screenId]?.values ?: return true
        return onScreen.none { overlaps(x, y, spanX, spanY, it.cellX, it.cellY, it.spanX, it.spanY) }
    }

    /** The attached card whose rectangle intersects the region, if any. */
    fun cardAt(screenId: Int, x: Int, y: Int, spanX: Int, spanY: Int): CaddyHomeCardInfo? = occupancy[screenId]?.values?.firstOrNull {
        overlaps(x, y, spanX, spanY, it.cellX, it.cellY, it.spanX, it.spanY)
    }

    private fun overlaps(
        ax: Int,
        ay: Int,
        aSpanX: Int,
        aSpanY: Int,
        bx: Int,
        by: Int,
        bSpanX: Int,
        bSpanY: Int,
    ): Boolean = ax < bx + bSpanX && ax + aSpanX > bx && ay < by + bSpanY && ay + aSpanY > by
}
