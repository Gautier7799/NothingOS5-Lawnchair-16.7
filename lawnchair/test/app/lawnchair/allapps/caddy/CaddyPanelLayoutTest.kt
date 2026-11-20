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
 * reports and must stay inside it. These tests pin the rules that keep it on screen and
 * keep it identical in size to the dialog it replaced.
 */
class CaddyPanelLayoutTest {

    private val density = 2f

    @Test
    fun widthPx_keepsSymmetricalMarginsOnAPhone() {
        // 1080px at density 2 is a 540dp phone: the margins win over the 560dp cap.
        assertEquals(
            1080 - CaddyPanelLayout.SIDE_MARGIN_DP * 2 * density.toInt(),
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
    fun heightPx_growsByOneRowPerRowOfApps() {
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
            density.toInt() * (CaddyPanelLayout.CHROME_HEIGHT_DP +
                CaddyPanelLayout.ROW_HEIGHT_DP +
                CaddyPanelLayout.LABEL_RESERVE_DP),
            height,
        )
    }

    @Test
    fun heightPx_floorsTheCeilingNotTheResult() {
        // A tall category on a short host keeps the 300dp floor, exactly like the dialog.
        val height = CaddyPanelLayout.heightPx(40, columns = 4, hostHeightPx = 800, density = density)
        assertEquals(density.toInt() * CaddyPanelLayout.MIN_HEIGHT_DP, height)
    }

    @Test
    fun heightPx_matchesTheDialogForATypicalPhone() {
        // 1080x2400 at density 2.75 with 12 apps is three rows: the dialog gave 76+3*96+34.
        val height = CaddyPanelLayout.heightPx(12, columns = 4, hostHeightPx = 2400, density = 2.75f)
        assertEquals(
            (2.75f * CaddyPanelLayout.CHROME_HEIGHT_DP).toInt() +
                (2.75f * 3 * CaddyPanelLayout.ROW_HEIGHT_DP).toInt() +
                (2.75f * CaddyPanelLayout.LABEL_RESERVE_DP).toInt(),
            height,
        )
    }

    @Test
    fun heightPx_handlesZeroAppsAndColumns() {
        val height = CaddyPanelLayout.heightPx(0, columns = 0, hostHeightPx = 4000, density = density)
        assertEquals(
            density.toInt() * CaddyPanelLayout.CHROME_HEIGHT_DP +
                density.toInt() * CaddyPanelLayout.LABEL_RESERVE_DP,
            height,
        )
    }
}
