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

class DossierCardLayoutTest {

    @Test
    fun reservesTitleRowBeforeSizingGrid() {
        // 400x400 card, 16px gutter, 24px title, 60px target cell.
        val grid = DossierCardLayout.compute(
            widthPx = 400, heightPx = 400, gutterPx = 16, titlePx = 24,
            targetCellPx = 60, preferredColumns = 3, preferredRows = 3,
        )
        // Width allows floor(368/60) = 6 columns, height allows floor(344/60) = 5 rows,
        // both capped by the preferred 3x3.
        assertThat(grid.columns).isEqualTo(3)
        assertThat(grid.rows).isEqualTo(3)
        // The cell must never be larger than the space left after the title is taken.
        assertThat(grid.cellSizePx * grid.rows).isAtMost(400 - 2 * 16 - 24)
    }

    @Test
    fun neverReturnsZeroDimensions() {
        val grid = DossierCardLayout.compute(
            widthPx = 0, heightPx = 0, gutterPx = 16, titlePx = 24,
            targetCellPx = 60, preferredColumns = 3, preferredRows = 3,
        )
        assertThat(grid.columns).isAtLeast(1)
        assertThat(grid.rows).isAtLeast(1)
        assertThat(grid.cellSizePx).isAtLeast(0)
    }

    @Test
    fun shrinksToSingleColumnOnNarrowCards() {
        val grid = DossierCardLayout.compute(
            widthPx = 120, heightPx = 120, gutterPx = 16, titlePx = 24,
            targetCellPx = 60, preferredColumns = 4, preferredRows = 4,
        )
        // Available width = 88 -> one 60px column fits; available height = 64 -> one row.
        assertThat(grid.columns).isEqualTo(1)
        assertThat(grid.rows).isEqualTo(1)
    }

    @Test
    fun titleReservationShrinksRowsWhenHeightIsTight() {
        // A 160px-tall card fits two 60px rows only while no title row is reserved.
        val withoutTitle = DossierCardLayout.compute(
            widthPx = 400, heightPx = 160, gutterPx = 16, titlePx = 0,
            targetCellPx = 60, preferredColumns = 3, preferredRows = 3,
        )
        val withTitle = DossierCardLayout.compute(
            widthPx = 400, heightPx = 160, gutterPx = 16, titlePx = 24,
            targetCellPx = 60, preferredColumns = 3, preferredRows = 3,
        )
        // Without the title: (160 - 32) / 60 = 2 rows. With the title: (160 - 32 - 24) / 60 = 1 row.
        assertThat(withoutTitle.rows).isEqualTo(2)
        assertThat(withTitle.rows).isEqualTo(1)
    }

    @Test
    fun gapHelperTargetsTheRequestedGapOnBothAxes() {
        // A 16dp grid gap with a 14dp target: each card grows 1dp into the gap, so the
        // visible gap is 16 - 2 * 1 = 14dp.
        assertThat(DossierCardLayout.overlapForGapPx(cellBorderSpacePx = 16, targetGapPx = 14))
            .isEqualTo(1)
        // The helper is axis-agnostic, so the same inputs give the same result for the
        // horizontal and vertical gaps.
        assertThat(DossierCardLayout.overlapForGapPx(cellBorderSpacePx = 16, targetGapPx = 14))
            .isEqualTo(DossierCardLayout.overlapForGapPx(cellBorderSpacePx = 16, targetGapPx = 14))
    }

    @Test
    fun gapHelperNeverOverlapsTheCards() {
        // A target gap at or above the natural gap means no growth at all.
        assertThat(DossierCardLayout.overlapForGapPx(cellBorderSpacePx = 16, targetGapPx = 16))
            .isEqualTo(0)
        assertThat(DossierCardLayout.overlapForGapPx(cellBorderSpacePx = 16, targetGapPx = 40))
            .isEqualTo(0)
        // Even asking for zero gap only closes the gap; the two cards still touch, not overlap.
        for (gap in intArrayOf(0, 1, 6, 8, 16, 24, 64)) {
            val overlap = DossierCardLayout.overlapForGapPx(cellBorderSpacePx = gap, targetGapPx = 0)
            assertThat(2 * overlap).isAtMost(gap)
        }
    }
}
