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
            titleReservePx = 44,
        )
        assertThat(cell).isEqualTo(100)
    }

    @Test
    fun cellSize_doesNotReserveTitleSpaceWhenTheTitleIsHidden() {
        // With no title, only the host/card padding and the grid gap remain.
        val cell = CaddyHomeCardLayout.cellSizePx(
            width = 400,
            height = 244,
            columns = 2,
            rows = 2,
            sideInsetPx = 36,
            titleReservePx = 28,
        )
        assertThat(cell).isEqualTo(108)
    }

    @Test
    fun cellSize_neverExceedsAvailableSlotOnADenseGrid() {
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
    fun cellSize_neverExceedsEitherAvailableAxis() {
        val cell = CaddyHomeCardLayout.cellSizePx(
            width = 90,
            height = 100,
            columns = 3,
            rows = 2,
            sideInsetPx = 30,
            titleReservePx = 20,
        )
        assertThat(cell).isEqualTo(20)
    }

    @Test
    fun cellSize_isZeroWhenInsetsConsumeTheViewport() {
        val cell = CaddyHomeCardLayout.cellSizePx(
            width = 30,
            height = 40,
            columns = 2,
            rows = 2,
            sideInsetPx = 36,
            titleReservePx = 44,
        )
        assertThat(cell).isEqualTo(0)
    }

    @Test
    fun overlaps_detectsPartialAndContainedCardIntersections() {
        assertThat(CaddyHomeCardLayout.overlaps(0, 0, 2, 2, 1, 1, 2, 2)).isTrue()
        assertThat(CaddyHomeCardLayout.overlaps(0, 0, 4, 4, 1, 1, 1, 1)).isTrue()
    }

    @Test
    fun overlaps_doesNotTreatTouchingEdgesAsAnOverlap() {
        assertThat(CaddyHomeCardLayout.overlaps(0, 0, 2, 2, 2, 0, 2, 2)).isFalse()
        assertThat(CaddyHomeCardLayout.overlaps(0, 0, 2, 2, 0, 2, 2, 2)).isFalse()
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
}
