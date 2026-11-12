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
 * Pure geometry for a workspace Caddy card, kept free of Android types so it can be unit tested.
 *
 * The card's icon slot has to be derived from the card's real measured size: a workspace card is
 * laid out in a CellLayout span whose cell height varies with the active grid, so a fixed icon
 * size makes the icons overflow their row and overlap.
 */
object CaddyHomeCardLayout {

    /** The square icon slot for a card of [width] x [height] px holding [columns] x [rows] icons. */
    fun cellSizePx(
        width: Int,
        height: Int,
        columns: Int,
        rows: Int,
        sideInsetPx: Int,
        titleReservePx: Int,
    ): Int {
        if (width <= 0 || height <= 0) return 0
        val safeColumns = columns.coerceAtLeast(1)
        val safeRows = rows.coerceAtLeast(1)
        val horizontal = (width - sideInsetPx) / safeColumns
        val vertical = (height - titleReservePx) / safeRows
        return minOf(horizontal, vertical).coerceAtLeast(0)
    }

    /**
     * Total vertical space reserved by the host/card chrome around the icon grid.
     * Inputs are measured pixel values from the rendered hierarchy.
     */
    fun verticalReservePx(
        hostPaddingPx: Int,
        cardPaddingPx: Int,
        titleHeightPx: Int,
        gridTopMarginPx: Int,
    ): Int = hostPaddingPx.coerceAtLeast(0) +
        cardPaddingPx.coerceAtLeast(0) +
        titleHeightPx.coerceAtLeast(0) +
        gridTopMarginPx.coerceAtLeast(0)

    /** True when two workspace card rectangles share at least one grid cell. */
    fun overlaps(
        firstX: Int,
        firstY: Int,
        firstSpanX: Int,
        firstSpanY: Int,
        secondX: Int,
        secondY: Int,
        secondSpanX: Int,
        secondSpanY: Int,
    ): Boolean = firstX < secondX + secondSpanX &&
        firstX + firstSpanX > secondX &&
        firstY < secondY + secondSpanY &&
        firstY + firstSpanY > secondY

    /** The scale to apply to an icon laid out at [nativePx] so it fits a [slotPx] slot. */
    fun iconScale(slotPx: Int, nativePx: Int): Float {
        if (slotPx <= 0 || nativePx <= 0) return 1f
        return (slotPx.toFloat() / nativePx).coerceAtMost(1f)
    }
}
