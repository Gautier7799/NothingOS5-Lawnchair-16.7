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
    fun previewIconSizePx_keepsTheRequestedSizeWhenTheCellIsLargeEnough() {
        assertThat(CaddyCardMetrics.previewIconSizePx(400, 300, 150)).isEqualTo(150)
    }

    @Test
    fun previewIconSizePx_shrinksToTheNarrowerCellEdge() {
        // A drawer card cell is wider than it is tall; the icon must never exceed either edge,
        // otherwise the cell clips the icon on the sides and it looks squeezed.
        assertThat(CaddyCardMetrics.previewIconSizePx(cellWidthPx = 90, cellHeightPx = 300, requestedSizePx = 150))
            .isEqualTo(90)
        assertThat(CaddyCardMetrics.previewIconSizePx(cellWidthPx = 300, cellHeightPx = 70, requestedSizePx = 150))
            .isEqualTo(70)
    }

    @Test
    fun previewIconSizePx_isZeroWhenThereIsNoIcon() {
        assertThat(CaddyCardMetrics.previewIconSizePx(200, 200, 0)).isEqualTo(0)
    }

    @Test
    fun previewIconSizePx_keepsTheRequestedSizeBeforeTheCellIsMeasured() {
        // A not-yet-measured cell must not collapse the icon to zero.
        assertThat(CaddyCardMetrics.previewIconSizePx(0, 0, 150)).isEqualTo(150)
        assertThat(CaddyCardMetrics.previewIconSizePx(200, 0, 150)).isEqualTo(150)
    }

    @Test
    fun centeredTopInsetPx_centresTheIconInTheCell() {
        assertThat(CaddyCardMetrics.centeredTopInsetPx(cellHeightPx = 64, iconSizePx = 40)).isEqualTo(12)
        assertThat(CaddyCardMetrics.centeredTopInsetPx(cellHeightPx = 100, iconSizePx = 50)).isEqualTo(25)
    }

    @Test
    fun centeredTopInsetPx_isZeroWhenTheIconFillsOrExceedsTheCell() {
        assertThat(CaddyCardMetrics.centeredTopInsetPx(cellHeightPx = 40, iconSizePx = 40)).isEqualTo(0)
        assertThat(CaddyCardMetrics.centeredTopInsetPx(cellHeightPx = 40, iconSizePx = 64)).isEqualTo(0)
    }

    @Test
    fun centeredTopInsetPx_isZeroBeforeTheCellIsMeasured() {
        assertThat(CaddyCardMetrics.centeredTopInsetPx(cellHeightPx = 0, iconSizePx = 40)).isEqualTo(0)
        assertThat(CaddyCardMetrics.centeredTopInsetPx(cellHeightPx = 64, iconSizePx = 0)).isEqualTo(0)
    }
}
