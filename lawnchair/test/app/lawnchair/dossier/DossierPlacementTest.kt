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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * JVM unit tests for the Dossier card placement rules.
 *
 * These tests cover the grid-bounds and overlap decisions only. They do not
 * exercise Android's CellLayout occupancy or the workspace drop paths.
 */
class DossierPlacementTest {

    /** A vacancy function over an explicit set of occupied cells. */
    private fun occupied(vararg cells: Pair<Int, Int>): DossierPlacement.RegionVacancy {
        val blocked = cells.toSet()
        return DossierPlacement.RegionVacancy { x, y, spanX, spanY ->
            (x until x + spanX).all { cx ->
                (y until y + spanY).none { cy -> blocked.contains(cx to cy) }
            }
        }
    }

    @Test
    fun resolveSpan_usesTwoByTwoWhenTheRegionIsFree() {
        val span = DossierPlacement.resolveSpan(
            cellX = 0, cellY = 0, gridCountX = 4, gridCountY = 4,
            sourceSpanX = 1, sourceSpanY = 1, vacancy = occupied(),
        )
        assertThat(span).isNotNull()
        assertThat(span!!.toList()).containsExactly(2, 2).inOrder()
    }

    @Test
    fun resolveSpan_clampsToTheGridBoundsAtTheRightEdge() {
        // Dropping at the last column leaves room for only one column of card.
        val span = DossierPlacement.resolveSpan(
            cellX = 3, cellY = 0, gridCountX = 4, gridCountY = 4,
            sourceSpanX = 1, sourceSpanY = 1, vacancy = occupied(),
        )
        assertThat(span).isNotNull()
        assertThat(span!![0]).isEqualTo(1)
        assertThat(span[1]).isEqualTo(2)
    }

    @Test
    fun resolveSpan_shrinksToAvoidOverlapping() {
        // The 2x2 region overlaps a neighbour; the card shrinks to a free 2x1 region
        // rather than covering the occupied cell.
        val span = DossierPlacement.resolveSpan(
            cellX = 0, cellY = 0, gridCountX = 4, gridCountY = 4,
            sourceSpanX = 1, sourceSpanY = 1, vacancy = occupied(1 to 1),
        )
        assertThat(span).isNotNull()
        assertThat(span!!.toList()).containsExactly(2, 1).inOrder()
    }

    @Test
    fun resolveSpan_returnsNullWhenEvenTheSourcesSizeDoesNotFit() {
        // The drop cell itself is occupied, so no card can be placed without overlap.
        val span = DossierPlacement.resolveSpan(
            cellX = 0, cellY = 0, gridCountX = 4, gridCountY = 4,
            sourceSpanX = 1, sourceSpanY = 1, vacancy = occupied(0 to 0),
        )
        assertThat(span).isNull()
    }

    @Test
    fun resolveSpan_neverExceedsTheGridEvenWhenSourcesAreLarger() {
        // A 3x3 source union cannot exceed the 2x2 grid; it is clamped, then it fits.
        val span = DossierPlacement.resolveSpan(
            cellX = 0, cellY = 0, gridCountX = 2, gridCountY = 2,
            sourceSpanX = 3, sourceSpanY = 3, vacancy = occupied(),
        )
        assertThat(span).isNotNull()
        assertThat(span!!.toList()).containsExactly(2, 2).inOrder()
    }

    @Test
    fun canPlace_acceptsACardFullyInsideTheGrid() {
        assertThat(
            DossierPlacement.canPlace(
                screenId = 0, isDropTargetAScreen = true,
                cellX = 1, cellY = 1, spanX = 2, spanY = 2,
                gridCountX = 4, gridCountY = 4,
            )
        ).isTrue()
    }

    @Test
    fun canPlace_acceptsACardThatExactlyFillsTheGrid() {
        // The last valid position: the span ends on the final column and row.
        assertThat(
            DossierPlacement.canPlace(
                screenId = 2, isDropTargetAScreen = true,
                cellX = 2, cellY = 2, spanX = 2, spanY = 2,
                gridCountX = 4, gridCountY = 4,
            )
        ).isTrue()
    }

    @Test
    fun canPlace_rejectsACardThatOverflowsTheGrid() {
        // This is the case addInScreen drops silently, leaving no layout params for
        // onDropChild to write to.
        assertThat(
            DossierPlacement.canPlace(
                screenId = 0, isDropTargetAScreen = true,
                cellX = 3, cellY = 0, spanX = 2, spanY = 2,
                gridCountX = 4, gridCountY = 4,
            )
        ).isFalse()
    }

    @Test
    fun canPlace_rejectsAnUnknownScreenId() {
        // getCellLayoutId returns -1 when the layout is not a known workspace screen.
        assertThat(
            DossierPlacement.canPlace(
                screenId = -1, isDropTargetAScreen = true,
                cellX = 0, cellY = 0, spanX = 2, spanY = 2,
                gridCountX = 4, gridCountY = 4,
            )
        ).isFalse()
    }

    @Test
    fun canPlace_rejectsTheDockAsATarget() {
        // The dock reports a page index as its screen id, which is >= 0, so the
        // screen check alone would accept it. A workspace card cannot live there.
        assertThat(
            DossierPlacement.canPlace(
                screenId = 0, isDropTargetAScreen = false,
                cellX = 0, cellY = 0, spanX = 2, spanY = 2,
                gridCountX = 4, gridCountY = 4,
            )
        ).isFalse()
    }

    @Test
    fun canPlace_rejectsANegativeCell() {
        assertThat(
            DossierPlacement.canPlace(
                screenId = 0, isDropTargetAScreen = true,
                cellX = -1, cellY = 0, spanX = 2, spanY = 2,
                gridCountX = 4, gridCountY = 4,
            )
        ).isFalse()
    }

    @Test
    fun canPlace_rejectsADegenerateSpan() {
        assertThat(
            DossierPlacement.canPlace(
                screenId = 0, isDropTargetAScreen = true,
                cellX = 0, cellY = 0, spanX = 0, spanY = 2,
                gridCountX = 4, gridCountY = 4,
            )
        ).isFalse()
    }
}
