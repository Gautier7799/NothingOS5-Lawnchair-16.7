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

package app.lawnchair.sidebar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The panel geometry decides whether the sidebar fits on screen without covering the dock or
 * running under the status bar. These tests pin that arithmetic across real screen sizes
 * rather than one hand-picked device.
 */
class SidebarMetricsTest {

    private val density = 2.75f

    private fun dp(value: Int) = (value * density).toInt()

    @Test
    fun panelHeightPx_growsWithContentBelowTheCeiling() {
        // 4 apps: padding plus four cells, well under the ceiling on a 2400 px screen.
        val height = SidebarMetrics.panelHeightPx(4, hostHeightPx = 2400, density = density)
        assertEquals(dp(SidebarMetrics.PANEL_PADDING_V_DP * 2) + 4 * dp(SidebarMetrics.CELL_HEIGHT_DP), height)
    }

    @Test
    fun panelHeightPx_clampsToTheHostLessTheReserve() {
        val host = 2400
        val ceiling = host - dp(SidebarMetrics.VERTICAL_RESERVE_DP)
        // Far more apps than fit: the panel must stop at the ceiling and scroll.
        val height = SidebarMetrics.panelHeightPx(100, hostHeightPx = host, density = density)
        assertEquals(ceiling, height)
    }

    @Test
    fun panelHeightPx_neverExceedsTheHost() {
        for (host in listOf(1600, 1920, 2340, 2400, 3200)) {
            val height = SidebarMetrics.panelHeightPx(100, hostHeightPx = host, density = density)
            assertTrue("height $height exceeded host $host", height <= host)
        }
    }

    @Test
    fun panelHeightPx_honoursTheMinimumWhenTheHostHasRoom() {
        // Content smaller than the minimum still gets a usable column.
        val height = SidebarMetrics.panelHeightPx(0, hostHeightPx = 2400, density = density)
        assertEquals(dp(SidebarMetrics.MIN_PANEL_HEIGHT_DP), height)
    }

    @Test
    fun panelHeightPx_isZeroWhenTheHostIsShorterThanTheReserve() {
        // A host with no room left cannot host a panel at all; returning the minimum here
        // would push the panel off the screen.
        assertEquals(0, SidebarMetrics.panelHeightPx(4, hostHeightPx = 100, density = density))
    }

    @Test
    fun panelHeightPx_shrinksToTheAvailableSpaceOnAShortHost() {
        val host = 500
        val available = host - dp(SidebarMetrics.VERTICAL_RESERVE_DP)
        val height = SidebarMetrics.panelHeightPx(10, hostHeightPx = host, density = density)
        assertEquals(available, height)
    }

    @Test
    fun panelHeightPx_treatsNegativeCountsAsEmpty() {
        assertEquals(
            SidebarMetrics.panelHeightPx(0, hostHeightPx = 2400, density = density),
            SidebarMetrics.panelHeightPx(-5, hostHeightPx = 2400, density = density),
        )
    }

    @Test
    fun slideDistancePx_followsTheEdge() {
        assertEquals(dp(SidebarMetrics.PANEL_WIDTH_DP).toFloat(), SidebarMetrics.slideDistancePx(true, density))
        assertEquals(-dp(SidebarMetrics.PANEL_WIDTH_DP).toFloat(), SidebarMetrics.slideDistancePx(false, density))
    }

    @Test
    fun slideDistancePx_isMirroredAcrossEdges() {
        val right = SidebarMetrics.slideDistancePx(true, density)
        val left = SidebarMetrics.slideDistancePx(false, density)
        assertEquals(0f, right + left, 0.001f)
    }

    @Test
    fun isWithinHandleSlop_acceptsAPointOnTheHandle() {
        assertTrue(SidebarMetrics.isWithinHandleSlop(0f, 0f, density))
    }

    @Test
    fun isWithinHandleSlop_rejectsAVerticalMissBeyondTheSlop() {
        val justInside = dp(SidebarMetrics.HANDLE_SLOP_Y_DP).toFloat() - 1f
        val justOutside = dp(SidebarMetrics.HANDLE_SLOP_Y_DP).toFloat() + 1f
        assertTrue(SidebarMetrics.isWithinHandleSlop(0f, justInside, density))
        assertFalse(SidebarMetrics.isWithinHandleSlop(0f, justOutside, density))
    }

    @Test
    fun isWithinHandleSlop_rejectsAHorizontalMissBeyondTheSlop() {
        val justOutside = dp(SidebarMetrics.HANDLE_SLOP_X_DP).toFloat() + 1f
        assertFalse(SidebarMetrics.isWithinHandleSlop(justOutside, 0f, density))
    }

    @Test
    fun panelFitsAlongsideTheDockOnEveryCommonScreen() {
        // The panel is vertically centred, so it must fit in host - reserve with the reserve
        // split above and below. This is the check the prototype failed: its content height
        // was computed before the panel had a width, so rows collapsed onto one point.
        for (host in listOf(1600, 1920, 2340, 2400, 3200)) {
            val height = SidebarMetrics.panelHeightPx(12, hostHeightPx = host, density = density)
            val top = (host - height) / 2
            assertTrue("top $top must not be negative on host $host", top >= 0)
            assertTrue("panel must end inside host $host", top + height <= host)
        }
    }

    @Test
    fun clampColumns_keepsRequestsInsideTheSupportedRange() {
        assertEquals(SidebarMetrics.MIN_COLUMNS, SidebarMetrics.clampColumns(0))
        assertEquals(SidebarMetrics.MIN_COLUMNS, SidebarMetrics.clampColumns(-3))
        assertEquals(SidebarMetrics.MAX_COLUMNS, SidebarMetrics.clampColumns(99))
        assertEquals(2, SidebarMetrics.clampColumns(2))
    }

    @Test
    fun panelWidthPx_oneColumnIsTheBaseWidth() {
        assertEquals(
            dp(SidebarMetrics.PANEL_WIDTH_DP),
            SidebarMetrics.panelWidthPx(1, density),
        )
    }

    @Test
    fun panelWidthPx_growsByOneCellPerExtraColumn() {
        val one = SidebarMetrics.panelWidthPx(1, density)
        val two = SidebarMetrics.panelWidthPx(2, density)
        assertEquals(dp(SidebarMetrics.CELL_WIDTH_DP), two - one)
    }

    @Test
    fun panelWidthPx_clampsOutOfRangeColumns() {
        assertEquals(
            SidebarMetrics.panelWidthPx(SidebarMetrics.MIN_COLUMNS, density),
            SidebarMetrics.panelWidthPx(-5, density),
        )
        assertEquals(
            SidebarMetrics.panelWidthPx(SidebarMetrics.MAX_COLUMNS, density),
            SidebarMetrics.panelWidthPx(50, density),
        )
    }

    @Test
    fun maxColumnsForScreen_neverExceedsTheDisplayWidth() {
        for (screenWidth in listOf(720, 1080, 1440)) {
            val columns = SidebarMetrics.maxColumnsForScreen(screenWidth, density)
            assertTrue(
                "columns $columns overflow screen $screenWidth",
                SidebarMetrics.panelWidthPx(columns, density) <= screenWidth,
            )
        }
    }

    @Test
    fun maxColumnsForScreen_allowsAllColumnsOnAWideDisplay() {
        assertEquals(
            SidebarMetrics.MAX_COLUMNS,
            SidebarMetrics.maxColumnsForScreen(1440, density),
        )
    }

    @Test
    fun maxColumnsForScreen_fallsBackToOneColumnOnANarrowDisplay() {
        assertEquals(SidebarMetrics.MIN_COLUMNS, SidebarMetrics.maxColumnsForScreen(100, density))
    }

    @Test
    fun handleCenterPx_centresAtTheRequestedFraction() {
        val host = 2000
        val handle = dp(SidebarMetrics.HANDLE_HEIGHT_DP)
        assertEquals(host / 2, SidebarMetrics.handleCenterPx(0.5f, host, handle))
    }

    @Test
    fun handleCenterPx_keepsTheWholeHandleInsideTheHost() {
        val host = 2000
        val handle = dp(SidebarMetrics.HANDLE_HEIGHT_DP)
        for (fraction in listOf(-1f, 0f, 0.25f, 1f, 2f)) {
            val centre = SidebarMetrics.handleCenterPx(fraction, host, handle)
            assertTrue("centre $centre too high", centre - handle / 2 >= 0)
            assertTrue("centre $centre too low", centre + handle / 2 <= host)
        }
    }

    @Test
    fun handleCenterPx_clampsTheFractionToTheHost() {
        val host = 2000
        val handle = dp(SidebarMetrics.HANDLE_HEIGHT_DP)
        assertEquals(
            SidebarMetrics.handleCenterPx(0f, host, handle),
            SidebarMetrics.handleCenterPx(-5f, host, handle),
        )
        assertEquals(
            SidebarMetrics.handleCenterPx(1f, host, handle),
            SidebarMetrics.handleCenterPx(5f, host, handle),
        )
    }

    @Test
    fun opacityFraction_mapsPercentToAlpha() {
        assertEquals(1f, SidebarMetrics.opacityFraction(100), 0.001f)
        assertEquals(0.5f, SidebarMetrics.opacityFraction(50), 0.001f)
    }

    @Test
    fun opacityFraction_neverGoesBelowTheReadableFloor() {
        assertEquals(
            SidebarMetrics.MIN_OPACITY_PERCENT / 100f,
            SidebarMetrics.opacityFraction(0),
            0.001f,
        )
        assertEquals(1f, SidebarMetrics.opacityFraction(500), 0.001f)
    }

    @Test
    fun isPanelUsable_rejectsAZeroHeightPanel() {
        assertFalse(SidebarMetrics.isPanelUsable(0))
        assertTrue(SidebarMetrics.isPanelUsable(1))
    }

    @Test
    fun isOpenPull_opensOnTheRightEdgeWhenPulledLeft() {
        assertTrue(SidebarMetrics.isOpenPull(deltaX = -40f, onRightEdge = true, slopPx = 10f))
        assertFalse(SidebarMetrics.isOpenPull(deltaX = 40f, onRightEdge = true, slopPx = 10f))
    }

    @Test
    fun isOpenPull_opensOnTheLeftEdgeWhenPulledRight() {
        assertTrue(SidebarMetrics.isOpenPull(deltaX = 40f, onRightEdge = false, slopPx = 10f))
        assertFalse(SidebarMetrics.isOpenPull(deltaX = -40f, onRightEdge = false, slopPx = 10f))
    }

    @Test
    fun isOpenPull_treatsAShortMoveAsATapOnEitherEdge() {
        assertFalse(SidebarMetrics.isOpenPull(-5f, onRightEdge = true, slopPx = 10f))
        assertFalse(SidebarMetrics.isOpenPull(5f, onRightEdge = false, slopPx = 10f))
    }

    @Test
    fun isOpenPull_rejectsAnExactSlopMove() {
        assertFalse(SidebarMetrics.isOpenPull(-10f, onRightEdge = true, slopPx = 10f))
    }
}
