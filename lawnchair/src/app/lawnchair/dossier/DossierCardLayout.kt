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

/**
 * Pure geometry for a Dossier card's icon grid.
 *
 * Kept free of Android dependencies so it can be exercised on the JVM. The title row
 * is reserved before the grid is measured, which is what keeps the title inside the
 * card no matter the span; the icon grid may only use what is left.
 */
object DossierCardLayout {

    /** The resolved grid: how many columns and rows fit, and the square cell size. */
    data class Grid(val columns: Int, val rows: Int, val cellSizePx: Int)

    /**
     * Resolves the icon grid for a card of [widthPx] x [heightPx].
     *
     * @param gutterPx outer gutter plus card padding applied on every side.
     * @param titlePx height reserved for the title row.
     * @param targetCellPx preferred cell size; the grid is capped so a cell is never
     *   smaller than this when the space allows, and never larger than it otherwise.
     * @param preferredColumns / [preferredRows] the user's configured grid limits.
     */
    fun compute(
        widthPx: Int,
        heightPx: Int,
        gutterPx: Int,
        titlePx: Int,
        targetCellPx: Int,
        preferredColumns: Int,
        preferredRows: Int,
    ): Grid {
        val availableWidth = (widthPx - 2 * gutterPx).coerceAtLeast(0)
        val availableHeight = (heightPx - 2 * gutterPx - titlePx).coerceAtLeast(0)
        val cell = targetCellPx.coerceAtLeast(1)
        val pixelColumns = (availableWidth / cell).coerceAtLeast(1)
        val pixelRows = (availableHeight / cell).coerceAtLeast(1)
        val columns = minOf(preferredColumns, pixelColumns).coerceAtLeast(1)
        val rows = minOf(preferredRows, pixelRows).coerceAtLeast(1)
        val cellSizePx = minOf(availableWidth / columns, availableHeight / rows).coerceAtLeast(0)
        return Grid(columns = columns, rows = rows, cellSizePx = cellSizePx)
    }
}
