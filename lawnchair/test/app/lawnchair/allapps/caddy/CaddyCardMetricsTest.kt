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

package app.lawnchair.allapps.caddy

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CaddyCardMetricsTest {

    @Test
    fun iconSizePx_scalesWithTheCell() {
        assertThat(CaddyCardMetrics.iconSizePx(cellSizePx = 100, fillRatio = 0.6f)).isEqualTo(60)
        assertThat(CaddyCardMetrics.iconSizePx(cellSizePx = 200, fillRatio = 0.6f)).isEqualTo(120)
    }

    @Test
    fun iconSizePx_isZeroBeforeTheCardIsMeasured() {
        assertThat(CaddyCardMetrics.iconSizePx(cellSizePx = 0, fillRatio = 0.6f)).isEqualTo(0)
        assertThat(CaddyCardMetrics.iconSizePx(cellSizePx = -5, fillRatio = 0.6f)).isEqualTo(0)
    }

    @Test
    fun iconSizePx_clampsAnOverLargeRatioSoIconsNeverOverlap() {
        // A ratio above the maximum would let an icon spill past its cell and overlap a peer.
        val size = CaddyCardMetrics.iconSizePx(cellSizePx = 100, fillRatio = 1.5f)
        assertThat(size).isEqualTo((100 * CaddyCardMetrics.MAX_FILL_RATIO).toInt())
        assertThat(size).isLessThan(100)
    }

    @Test
    fun iconSizePx_clampsAnUnderSmallRatioSoIconsStayTappable() {
        val size = CaddyCardMetrics.iconSizePx(cellSizePx = 100, fillRatio = 0.01f)
        assertThat(size).isEqualTo((100 * CaddyCardMetrics.MIN_FILL_RATIO).toInt())
        assertThat(size).isGreaterThan(0)
    }

    @Test
    fun iconScale_shrinksANativeIconIntoANarrowDrawerCell() {
        // Regression for the squeezed Caddy icons: a two-column card at six apps per row
        // leaves a 118px cell for a 168px native icon. The view must shrink to 118/168 so the
        // drawable never overflows its view and the cell cannot crop its sides.
        val scale = CaddyCardMetrics.iconScale(
            cellWidthPx = 118,
            cellHeightPx = 168,
            nativeSizePx = 168,
        )
        assertThat(scale).isWithin(1e-6f).of(118f / 168f)
        assertThat(168 * scale).isWithin(1e-3f).of(118f)
    }

    @Test
    fun iconScale_shrinksToTheNarrowerCellEdge() {
        // Whichever edge is smaller wins, so the icon is never cropped on either axis.
        assertThat(CaddyCardMetrics.iconScale(90, 300, 168)).isWithin(1e-6f).of(90f / 168f)
        assertThat(CaddyCardMetrics.iconScale(300, 70, 168)).isWithin(1e-6f).of(70f / 168f)
    }

    @Test
    fun iconScale_neverEnlargesAnIconThatAlreadyFits() {
        assertThat(CaddyCardMetrics.iconScale(cellWidthPx = 400, cellHeightPx = 300, nativeSizePx = 168))
            .isEqualTo(1f)
        assertThat(CaddyCardMetrics.iconScale(cellWidthPx = 168, cellHeightPx = 168, nativeSizePx = 168))
            .isEqualTo(1f)
    }

    @Test
    fun iconScale_leavesTheIconAloneBeforeTheCellIsMeasured() {
        // A cell that has not been laid out yet must not collapse the icon to nothing.
        assertThat(CaddyCardMetrics.iconScale(0, 0, 168)).isEqualTo(1f)
        assertThat(CaddyCardMetrics.iconScale(200, 0, 168)).isEqualTo(1f)
        assertThat(CaddyCardMetrics.iconScale(200, 200, 0)).isEqualTo(1f)
    }

    @Test
    fun iconScale_isAlwaysASquareScaleBetweenZeroAndOne() {
        for (cell in 1..200 step 7) {
            val scale = CaddyCardMetrics.iconScale(cell, cell, 168)
            assertThat(scale).isAtLeast(0f)
            assertThat(scale).isAtMost(1f)
        }
    }

    @Test
    fun iconScale_honoursTheTargetSizeWhenTheCellIsRoomierThanTheTarget() {
        // A card asks for 86% of a 200px cell: the icon must render at 172px, not fill the cell.
        // The native size is larger than the target here, so the target is what limits it.
        val scale = CaddyCardMetrics.iconScale(
            cellWidthPx = 200,
            cellHeightPx = 200,
            nativeSizePx = 300,
            targetSizePx = 172,
        )
        assertThat(300 * scale).isWithin(1e-3f).of(172f)
        assertThat(scale).isLessThan(1f)
    }

    @Test
    fun iconScale_neverUpscalesPastTheNativeSize() {
        // A target larger than the native icon must not stretch it; a 168px drawable stays 168px.
        val scale = CaddyCardMetrics.iconScale(
            cellWidthPx = 400,
            cellHeightPx = 400,
            nativeSizePx = 168,
            targetSizePx = 172,
        )
        assertThat(scale).isEqualTo(1f)
    }

    @Test
    fun iconScale_prefersTheCellOverAnOversizedTarget() {
        // A target wider than the cell must still be clamped to the cell, never cropped.
        val scale = CaddyCardMetrics.iconScale(
            cellWidthPx = 100,
            cellHeightPx = 200,
            nativeSizePx = 168,
            targetSizePx = 400,
        )
        assertThat(168 * scale).isWithin(1e-3f).of(100f)
    }

    @Test
    fun iconScale_neverRendersLargerThanEitherCellEdge() {
        // The regression guard: whatever the native size, target and cell, the drawn icon
        // always fits inside the cell on both axes.
        val natives = listOf(48, 108, 168, 192, 256)
        val cells = listOf(60, 96, 118, 126, 168, 200, 320)
        val targets = listOf(0, 40, 56, 100, 172, 400)
        for (native in natives) {
            for (cell in cells) {
                for (target in targets) {
                    val scale = CaddyCardMetrics.iconScale(cell, cell, native, target)
                    val rendered = native * scale
                    assertThat(rendered).isAtMost(cell.toFloat() + 1e-3f)
                }
            }
        }
    }
}
