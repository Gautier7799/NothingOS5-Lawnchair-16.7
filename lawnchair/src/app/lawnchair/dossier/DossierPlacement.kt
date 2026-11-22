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
 * Android-free placement decisions for a Dossier card.
 *
 * A card groups two app icons that occupied two neighbouring cells. The card
 * prefers to cover exactly those two cells, but must never exceed the grid it is
 * dropped on and must never overlap an unrelated item. These checks are pure so
 * the bounds and overlap rules can be tested on the JVM; the caller supplies the
 * already-observed vacancy of the candidate region.
 */
object DossierPlacement {

    /** Reports whether a candidate region is free on the target grid. */
    fun interface RegionVacancy {
        fun isVacant(cellX: Int, cellY: Int, spanX: Int, spanY: Int): Boolean
    }

    /**
     * Chooses the span for a card dropped at `(cellX, cellY)`.
     *
     * Starts from the union of the two source spans, clamps it to the grid bounds
     * starting at the drop cell, then shrinks it toward the sources' own size while
     * the region is not vacant. Returns null when even the sources' size does not
     * fit, which means the caller must not create a card.
     */
    @JvmStatic
    fun resolveSpan(
        cellX: Int,
        cellY: Int,
        gridCountX: Int,
        gridCountY: Int,
        sourceSpanX: Int,
        sourceSpanY: Int,
        vacancy: RegionVacancy,
    ): IntArray? {
        val maxX = (gridCountX - cellX).coerceAtLeast(1)
        val maxY = (gridCountY - cellY).coerceAtLeast(1)
        val wantedX = maxOf(DEFAULT_SPAN_X, sourceSpanX).coerceAtMost(maxX)
        val wantedY = maxOf(DEFAULT_SPAN_Y, sourceSpanY).coerceAtMost(maxY)
        val minX = sourceSpanX.coerceIn(1, maxX)
        val minY = sourceSpanY.coerceIn(1, maxY)

        var spanX = wantedX
        while (spanX >= minX) {
            var spanY = wantedY
            while (spanY >= minY) {
                if (vacancy.isVacant(cellX, cellY, spanX, spanY)) {
                    return intArrayOf(spanX, spanY)
                }
                spanY--
            }
            spanX--
        }
        return null
    }

    /**
     * Whether a resolved card can actually be attached to the drop target.
     *
     * `addInScreen` returns without attaching layout params when the screen id is
     * unknown or the cell lies outside the grid, and the following `onDropChild`
     * then writes to those missing params. Callers check this first so an
     * unplaceable drop leaves the workspace untouched instead of crashing, and so
     * no database row is written for a card that can never be shown.
     *
     * A Dossier card is a workspace item, so `isDropTargetAScreen` must be true: the
     * dock is not a valid parent even though it reports a page index.
     */
    @JvmStatic
    fun canPlace(
        screenId: Int,
        isDropTargetAScreen: Boolean,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int,
        gridCountX: Int,
        gridCountY: Int,
    ): Boolean {
        if (screenId < 0 || !isDropTargetAScreen) return false
        if (cellX < 0 || cellY < 0) return false
        if (spanX < 1 || spanY < 1) return false
        return cellX + spanX <= gridCountX && cellY + spanY <= gridCountY
    }

    const val DEFAULT_SPAN_X = 2
    const val DEFAULT_SPAN_Y = 2
}
