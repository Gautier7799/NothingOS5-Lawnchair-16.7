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

package app.lawnchair.ui.dock

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DockCapsuleGeometryTest {

    private val pixel8Density = 2.625f

    @Test
    fun sideMargin_isTwoDp() {
        assertThat(DockCapsuleGeometry.sideMarginPx(pixel8Density)).isEqualTo(5)
    }

    @Test
    fun containerPadding_isSideMarginPlusInnerPadding() {
        assertThat(DockCapsuleGeometry.innerPaddingPx(pixel8Density)).isEqualTo(32)
        assertThat(DockCapsuleGeometry.containerPaddingPx(pixel8Density)).isEqualTo(5 + 32)
    }

    @Test
    fun panelHeight_isCappedToAvailableSpace() {
        // Plenty of room: use the full 160dp (420px on the Pixel 8).
        assertThat(DockCapsuleGeometry.preferredHeightPx(pixel8Density)).isEqualTo(420)
        assertThat(DockCapsuleGeometry.panelHeightPx(500, pixel8Density)).isEqualTo(420)
        // Tight container: never exceed what is available.
        assertThat(DockCapsuleGeometry.panelHeightPx(120, pixel8Density)).isEqualTo(120)
    }

    @Test
    fun extraHeight_isPositive() {
        assertThat(DockCapsuleGeometry.extraHeightPx(pixel8Density)).isGreaterThan(0)
    }

    @Test
    fun cornerRadius_keepsStraightSides() {
        val panelHeight = DockCapsuleGeometry.panelHeightPx(400, pixel8Density)
        val radius = DockCapsuleGeometry.cornerRadiusPx(panelHeight, pixel8Density)
        // 16dp on the Pixel 8, and below half the height: the sides stay straight.
        assertThat(radius).isEqualTo(16 * pixel8Density)
        assertThat(radius).isLessThan(panelHeight / 2f)
    }

    @Test
    fun cornerRadius_isClampedOnShortPanels() {
        // A very short panel must not turn into a pill: the radius stays below half the height.
        val short = DockCapsuleGeometry.cornerRadiusPx(60, pixel8Density)
        assertThat(short).isLessThan(30f)
    }
}
