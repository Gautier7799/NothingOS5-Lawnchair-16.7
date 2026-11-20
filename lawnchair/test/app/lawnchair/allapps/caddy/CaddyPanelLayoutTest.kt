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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bounds rules for the Caddy category panel.
 *
 * The panel is a DragLayer overlay, so it has to be sized from the host the launcher
 * reports and must stay inside it. These tests pin the clamping rules that keep it on
 * screen for every app count, column count and host size.
 */
class CaddyPanelLayoutTest {

    private val density = 2f

    @Test
    fun widthPx_keepsSymmetricMarginsOnAPhone() {
        // 1080px at density 2 is a 540dp phone: margins win over the 560dp cap.
        assertEquals(
            1080 - 2 * CaddyPanelLayout.SIDE_MARGIN_DP * density.toInt(),
            CaddyPanelLayout.widthPx(hostWidthPx = 1080, density = density),
        )
    }

    @Test
    fun widthPx_capsOnATablet() {
        // 2400px at density 2 is 1200dp, far above the 560dp ceiling.
        assertEquals(
            CaddyPanelLayout.MAX_WIDTH_DP * density.toInt(),
            CaddyPanelLayout.widthPx(hostWidthPx = 2400, density = density),
        )
    }

    @Test
    fun widthPx_neverNegativeOnATinyHost() {
        assertEquals(0, CaddyPanelLayout.widthPx(hostWidthPx = 10, density = density))
    }

    @Test
    fun heightPx_growsWithTheRowCount() {
        // Two and three rows both sit above the minimum height, so the growth is the row cost.
        val twoRows = CaddyPanelLayout.heightPx(8, columns = 4, hostHeightPx = 4000, density = density)
        val threeRows = CaddyPanelLayout.heightPx(12, columns = 4, hostHeightPx = 4000, density = density)
        assertTrue("more rows must be taller", threeRows > twoRows)
        assertEquals(2 * CaddyPanelLayout.ROW_HEIGHT_DP, threeRows - twoRows)
    }

    @Test
    fun heightPx_clampsToLeaveRoomForTheWorkspace() {
        val height = CaddyPanelLayout.heightPx(40, columns = 4, hostHeightPx = 2000, density = density)
        assertEquals(2000 - 2 * CaddyPanelLayout.VERTICAL_RESERVE_DP, height)
    }

    @Test
    fun heightPx_isTheNaturalContentHeightWhenThereIsRoom() {
        // One row on a tall host: chrome + one row + the label reserve, in px.
        val height = CaddyPanelLayout.heightPx(1, columns = 4, hostHeightPx = 4000, density = density)
        assertEquals(
            2 * (CaddyPanelLayout.CHROME_HEIGHT_DP +
                CaddyPanelLayout.ROW_HEIGHT_DP +
                CaddyPanelLayout.LABEL_RESERVE_DP),
            height,
        )
    }

    @Test
    fun heightPx_keepsTheMinimumWhenTheContentIsSmall() {
        // No apps at all still gives a panel tall enough to tap, not a zero-height strip.
        val height = CaddyPanelLayout.heightPx(0, columns = 4, hostHeightPx = 4000, density = density)
        assertEquals(2 * CaddyPanelLayout.MIN_HEIGHT_DP, height)
    }

    @Test
    fun heightPx_neverExceedsTheHost() {
        val host = 500
        val height = CaddyPanelLayout.heightPx(40, columns = 4, hostHeightPx = host, density = density)
        assertTrue("panel must fit the host, was $height", height <= host)
    }

    @Test
    fun heightPx_handlesZeroAppsAndColumns() {
        val height = CaddyPanelLayout.heightPx(0, columns = 0, hostHeightPx = 4000, density = density)
        assertEquals(2 * CaddyPanelLayout.MIN_HEIGHT_DP, height)
    }
}
