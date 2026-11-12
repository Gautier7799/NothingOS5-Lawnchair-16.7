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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CaddyHomeCardLayoutTest {

    @Test
    fun cellSize_usesTheSmallerAxisSoIconsNeverOverflow() {
        // Wide but short card: the height is the binding constraint.
        val cell = CaddyHomeCardLayout.cellSizePx(
            width = 400,
            height = 200,
            columns = 2,
            rows = 2,
            sideInsetPx = 0,
            titleReservePx = 0,
        )
        assertThat(cell).isEqualTo(100)
    }

    @Test
    fun cellSize_subtractsTheTitleReserveFromTheHeight() {
        val cell = CaddyHomeCardLayout.cellSizePx(
            width = 400,
            height = 244,
            columns = 2,
            rows = 2,
            sideInsetPx = 0,
            titleReservePx = 52,
        )
        assertThat(cell).isEqualTo(96)
    }

    @Test
    fun cellSize_neverExceedsTheAvailableSpaceOnADenseGrid() {
        val cell = CaddyHomeCardLayout.cellSizePx(
            width = 120,
            height = 120,
            columns = 5,
            rows = 4,
            sideInsetPx = 36,
            titleReservePx = 44,
        )
        assertThat(cell).isEqualTo(16)
    }

    @Test
    fun cellSize_isZeroBeforeTheCardHasBeenMeasured() {
        assertThat(
            CaddyHomeCardLayout.cellSizePx(0, 0, 2, 2, 36, 44),
        ).isEqualTo(0)
    }

    @Test
    fun cellSize_treatsNonPositiveSpansAsOneSoItNeverDividesByZero() {
        val cell = CaddyHomeCardLayout.cellSizePx(
            width = 200,
            height = 200,
            columns = 0,
            rows = 0,
            sideInsetPx = 0,
            titleReservePx = 0,
        )
        assertThat(cell).isEqualTo(200)
    }

    @Test
    fun overlaps_detectsPartialIntersection() {
        assertThat(CaddyHomeCardLayout.overlaps(0, 0, 2, 2, 1, 1, 2, 2)).isTrue()
    }

    @Test
    fun overlaps_allowsAdjacentCards() {
        assertThat(CaddyHomeCardLayout.overlaps(0, 0, 2, 2, 2, 0, 2, 2)).isFalse()
    }

    @Test
    fun iconScale_shrinksAnIconThatIsLargerThanItsSlot() {
        assertThat(CaddyHomeCardLayout.iconScale(slotPx = 60, nativePx = 120)).isEqualTo(0.5f)
    }

    @Test
    fun iconScale_neverEnlargesAnIconPastItsNativeSize() {
        assertThat(CaddyHomeCardLayout.iconScale(slotPx = 240, nativePx = 120)).isEqualTo(1f)
    }

    @Test
    fun iconScale_isOneForInvalidSizes() {
        assertThat(CaddyHomeCardLayout.iconScale(0, 120)).isEqualTo(1f)
        assertThat(CaddyHomeCardLayout.iconScale(60, 0)).isEqualTo(1f)
    }

    // --- icon area from the real measured title height (the 4-icon clipping defect) ---

    @Test
    fun iconArea_usesTheMeasuredTitleHeightInsteadOfTheFixedReserve() {
        // A tall title (large font scale) must reduce the icon area below what the fixed fallback
        // reserve would allow; otherwise the last row is pushed past the card's bottom edge.
        val area = CaddyHomeCardLayout.iconAreaHeightPx(
            cardHeightPx = 400,
            titleHeightPx = 60,
            verticalPaddingPx = 24,
            gridGapPx = 6,
            fallbackReservePx = 72,
        )
        assertThat(area).isEqualTo(310)
    }

    @Test
    fun iconArea_fallsBackToTheReserveBeforeTheTitleIsMeasured() {
        val area = CaddyHomeCardLayout.iconAreaHeightPx(
            cardHeightPx = 400,
            titleHeightPx = 0,
            verticalPaddingPx = 24,
            gridGapPx = 6,
            fallbackReservePx = 72,
        )
        assertThat(area).isEqualTo(328)
    }

    @Test
    fun iconArea_neverGoesNegativeOnATinyCard() {
        val area = CaddyHomeCardLayout.iconAreaHeightPx(
            cardHeightPx = 40,
            titleHeightPx = 60,
            verticalPaddingPx = 24,
            gridGapPx = 6,
            fallbackReservePx = 72,
        )
        assertThat(area).isEqualTo(0)
    }

    @Test
    fun visibleRows_showsAllRequestedRowsWhenTheyFit() {
        // 4-icon card: 2 rows of 100px in a 220px area.
        assertThat(CaddyHomeCardLayout.visibleRows(areaHeightPx = 220, cellPx = 100, requestedRows = 2))
            .isEqualTo(2)
    }

    @Test
    fun visibleRows_clampsToTheRowsThatActuallyFit() {
        // Only one row fits, so the renderer must not draw the second (the clipped row).
        assertThat(CaddyHomeCardLayout.visibleRows(areaHeightPx = 150, cellPx = 100, requestedRows = 2))
            .isEqualTo(1)
    }

    @Test
    fun visibleRows_neverExceedsTheRequestedRows() {
        assertThat(CaddyHomeCardLayout.visibleRows(areaHeightPx = 1000, cellPx = 100, requestedRows = 2))
            .isEqualTo(2)
    }

    @Test
    fun visibleRows_keepsAtLeastOneRowWhileThereIsAnyRoom() {
        assertThat(CaddyHomeCardLayout.visibleRows(areaHeightPx = 10, cellPx = 100, requestedRows = 2))
            .isEqualTo(1)
    }

    @Test
    fun visibleRows_returnsRequestedWhenTheCellIsUnmeasured() {
        assertThat(CaddyHomeCardLayout.visibleRows(areaHeightPx = 0, cellPx = 0, requestedRows = 3))
            .isEqualTo(3)
    }

    @Test
    fun fourIconCard_keepsBothRowsOnACompactGrid() {
        // A 2x2 card on a compact home grid: the measured title + padding must still leave room for
        // both rows, which is exactly the case that used to clip the bottom row.
        val cardHeight = 300
        val area = CaddyHomeCardLayout.iconAreaHeightPx(
            cardHeightPx = cardHeight,
            titleHeightPx = 46,
            verticalPaddingPx = 24,
            gridGapPx = 6,
            fallbackReservePx = 72,
        )
        val cell = CaddyHomeCardLayout.cellSizePx(
            width = 300,
            height = cardHeight,
            columns = 2,
            rows = 2,
            sideInsetPx = 36,
            titleReservePx = cardHeight - area,
        )
        val rows = CaddyHomeCardLayout.visibleRows(area, cell, requestedRows = 2)
        assertThat(rows).isEqualTo(2)
        assertThat(cell * rows).isAtMost(area)
    }
}
