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
        // Plenty of room: use the full 90dp (236px on the Pixel 8).
        assertThat(DockCapsuleGeometry.preferredHeightPx(pixel8Density)).isEqualTo(236)
        assertThat(DockCapsuleGeometry.panelHeightPx(500, pixel8Density)).isEqualTo(236)
        // Tight container: never exceed what is available.
        assertThat(DockCapsuleGeometry.panelHeightPx(120, pixel8Density)).isEqualTo(120)
    }

    @Test
    fun extraHeight_isPositive() {
        assertThat(DockCapsuleGeometry.extraHeightPx(pixel8Density)).isGreaterThan(0)
    }

    @Test
    fun glassBorder_isThin() {
        // The specular edge must read as a hairline highlight, not a frame.
        assertThat(DockCapsuleGeometry.GLASS_BORDER_WIDTH_DP).isAtMost(2)
    }

    @Test
    fun glassTint_keepsWallpaperVisible() {
        // Ultra-thin glass: the tint stays low enough for the wallpaper to show through.
        assertThat(DockCapsuleGeometry.GLASS_TINT_PERCENT).isIn(8..20)
    }

    @Test
    fun cornerRadius_keepsStraightSides() {
        val panelHeight = DockCapsuleGeometry.panelHeightPx(400, pixel8Density)
        val radius = DockCapsuleGeometry.cornerRadiusPx(panelHeight, pixel8Density)
        // 32dp on the Pixel 8, and below half the height: the sides stay straight.
        assertThat(radius).isEqualTo(32 * pixel8Density)
        assertThat(radius).isLessThan(panelHeight / 2f)
    }

    @Test
    fun cornerRadius_isClampedOnShortPanels() {
        // A very short panel must not turn into a pill: the radius stays below half the height.
        val short = DockCapsuleGeometry.cornerRadiusPx(60, pixel8Density)
        assertThat(short).isLessThan(30f)
    }

    @Test
    fun preferredCornerRadius_defaultsToApprovedIosRadius() {
        val panelHeight = DockCapsuleGeometry.preferredHeightPx(pixel8Density)
        // No stored setting: the approved 32dp iOS radius is used.
        assertThat(DockCapsuleGeometry.preferredCornerRadiusPx(panelHeight, pixel8Density, null))
            .isEqualTo(32 * pixel8Density)
        // A negative value is treated as "unset".
        assertThat(DockCapsuleGeometry.preferredCornerRadiusPx(panelHeight, pixel8Density, -1f))
            .isEqualTo(32 * pixel8Density)
    }

    @Test
    fun preferredCornerRadius_honoursUserSetting() {
        val panelHeight = DockCapsuleGeometry.preferredHeightPx(pixel8Density)
        // The user's slider value wins over the built-in default.
        assertThat(DockCapsuleGeometry.preferredCornerRadiusPx(panelHeight, pixel8Density, 18f))
            .isEqualTo(18 * pixel8Density)
        // 0 means square corners, not "unset".
        assertThat(DockCapsuleGeometry.preferredCornerRadiusPx(panelHeight, pixel8Density, 0f))
            .isEqualTo(0f)
    }

    @Test
    fun preferredCornerRadius_isClampedOnShortPanels() {
        // Even a large user value cannot exceed half the panel height.
        val short = DockCapsuleGeometry.preferredCornerRadiusPx(60, pixel8Density, 100f)
        assertThat(short).isLessThan(30f)
    }

    @Test
    fun searchPillTop_isAboveThePanel() {
        // The dock container is the full 90dp panel plus the bottom margin (236 + 32 = 268px).
        val containerHeight = DockCapsuleGeometry.preferredHeightPx(pixel8Density) +
            DockCapsuleGeometry.bottomMarginPx(pixel8Density)
        val pillTop = DockCapsuleGeometry.searchPillTopPx(containerHeight, pixel8Density)
        val panelTop = (containerHeight - DockCapsuleGeometry.preferredHeightPx(pixel8Density)) / 2
        val pillHeight = DockCapsuleGeometry.searchPillHeightPx(pixel8Density)
        val gap = DockCapsuleGeometry.searchPillGapPx(pixel8Density)

        // Exactly one gap above the panel, so the pill bottom touches the gap.
        assertThat(pillTop).isEqualTo(panelTop - gap - pillHeight)
        assertThat(pillTop + pillHeight).isEqualTo(panelTop - gap)
        // It floats outside the container: that is why it cannot be a child of the dock.
        assertThat(pillTop).isLessThan(0)
    }

    @Test
    fun searchPillTop_usesTheClampedPanelHeight() {
        // A container shorter than the panel must not push the pill off the top of the screen.
        val tight = DockCapsuleGeometry.searchPillTopPx(120, pixel8Density)
        assertThat(tight).isEqualTo(-DockCapsuleGeometry.searchPillGapPx(pixel8Density) -
            DockCapsuleGeometry.searchPillHeightPx(pixel8Density))
    }
}
