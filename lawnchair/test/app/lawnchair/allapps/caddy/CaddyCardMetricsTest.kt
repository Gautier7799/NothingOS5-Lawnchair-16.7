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
}
