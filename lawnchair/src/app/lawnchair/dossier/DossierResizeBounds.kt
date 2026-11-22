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

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure grid math for resizing a Dossier card.
 *
 * Kept free of Android types so the behaviour can be unit tested on the JVM. The
 * caller converts the finger movement to whole cells and reports which borders
 * the user grabbed; this object returns the resulting placement, always clamped
 * so the card stays inside the grid.
 */
object DossierResizeBounds {

    /** A grid placement: origin cell and span. */
    data class Placement(val cellX: Int, val cellY: Int, val spanX: Int, val spanY: Int)

    /** Fraction of a cell the finger must travel before the span changes by one. */
    const val STEP_THRESHOLD = 0.66f

    /**
     * Returns the placement after dragging the active borders by [deltaCellsX] / [deltaCellsY]
     * whole cells.
     *
     * @param start the placement when the gesture began.
     * @param deltaCellsX finger movement along X, in cells (positive = right).
     * @param deltaCellsY finger movement along Y, in cells (positive = down).
     * @param left/right/top/bottom which border the user grabbed.
     */
    fun resize(
        start: Placement,
        deltaCellsX: Float,
        deltaCellsY: Float,
        left: Boolean,
        right: Boolean,
        top: Boolean,
        bottom: Boolean,
        countX: Int,
        countY: Int,
        minSpan: Int = DossierCardInfo.MIN_SPAN,
        maxSpan: Int = DossierCardInfo.MAX_SPAN,
    ): Placement {
        val hInc = increment(deltaCellsX)
        val vInc = increment(deltaCellsY)

        var spanX = start.spanX
        var cellX = start.cellX
        when {
            right -> spanX = start.spanX + hInc

            left -> {
                spanX = start.spanX - hInc
                cellX = start.cellX + hInc
            }
        }

        var spanY = start.spanY
        var cellY = start.cellY
        when {
            bottom -> spanY = start.spanY + vInc

            top -> {
                spanY = start.spanY - vInc
                cellY = start.cellY + vInc
            }
        }

        spanX = spanX.coerceIn(minSpan, maxSpan.coerceAtMost(countX.coerceAtLeast(minSpan)))
        spanY = spanY.coerceIn(minSpan, maxSpan.coerceAtMost(countY.coerceAtLeast(minSpan)))
        cellX = cellX.coerceIn(0, (countX - spanX).coerceAtLeast(0))
        cellY = cellY.coerceIn(0, (countY - spanY).coerceAtLeast(0))

        return Placement(cellX, cellY, spanX, spanY)
    }

    private fun increment(deltaCells: Float): Int = if (abs(deltaCells) > STEP_THRESHOLD) deltaCells.roundToInt() else 0
}
