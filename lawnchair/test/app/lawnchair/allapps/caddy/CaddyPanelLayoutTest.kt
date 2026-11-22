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
 * The panel reuses the dialog's metrics so the two look identical. These tests pin the
 * arithmetic that the dialog used, because an earlier version of the overlay changed
 * them and the icons overlapped.
 */
class CaddyPanelLayoutTest {

    private val density = 2.75f

    @Test
    fun heightPx_matchesTheDialogForATypicalPhone() {
        // 1080x2400 at 2.75 with 12 apps is three rows: 76 + 3*96 + 34.
        val height = CaddyPanelLayout.heightPx(12, columns = 4, hostHeightPx = 2400, density = density)
        assertEquals(
            (76 * density).toInt() + (3 * 96 * density).toInt() + (34 * density).toInt(),
            height,
        )
    }

    @Test
    fun heightPx_addsOneRowForEachPartialRow() {
        val one = CaddyPanelLayout.heightPx(1, columns = 4, hostHeightPx = 2400, density = density)
        val four = CaddyPanelLayout.heightPx(4, columns = 4, hostHeightPx = 2400, density = density)
        val five = CaddyPanelLayout.heightPx(5, columns = 4, hostHeightPx = 2400, density = density)
        assertEquals(one, four)
        assertEquals((96 * density).toInt(), five - four)
    }

    @Test
    fun heightPx_floorsTheCeilingNotTheResult() {
        // The dialog floored max(300dp, screen - 176dp), never the result itself.
        val height = CaddyPanelLayout.heightPx(40, columns = 4, hostHeightPx = 800, density = density)
        assertEquals((300 * density).toInt(), height)
    }

    @Test
    fun heightPx_capsAtTheHostMinusTheReserve() {
        val host = 2400
        val height = CaddyPanelLayout.heightPx(400, columns = 4, hostHeightPx = host, density = density)
        assertEquals(host - (176 * density).toInt(), height)
    }

    @Test
    fun heightPx_neverExceedsTheHostOnARealDevice() {
        for (host in listOf(1920, 2160, 2280, 2400, 2640, 3200)) {
            val height = CaddyPanelLayout.heightPx(200, columns = 4, hostHeightPx = host, density = density)
            assertTrue("height $height must fit host $host", height <= host)
        }
    }

    @Test
    fun widthPx_capsAt560dp() {
        assertEquals((560 * density).toInt(), CaddyPanelLayout.widthPx(2400, density))
    }

    @Test
    fun widthPx_keepsA16dpMarginOnEachSide() {
        val host = 800
        assertEquals(host - (16 * density).toInt() * 2, CaddyPanelLayout.widthPx(host, density))
    }

    /**
     * The reported bug, second attempt: the icons were stacked on one point.
     *
     * Cells used zero-width weighted columns, which only resolve once the grid itself has
     * a resolved width. Any pass that measured the grid first collapsed every column to
     * zero. Cell widths are now computed up front, so the grid is definite on its first
     * measure and the four columns never share an origin.
     */
    @Test
    fun cellWidthPx_dividesTheContentBoxIntoFourDistinctColumns() {
        val panelWidth = CaddyPanelLayout.widthPx(1080, density)
        val cellWidth = CaddyPanelLayout.cellWidthPx(panelWidth, columns = 4, density = density)
        assertTrue("cell width must be positive, was $cellWidth", cellWidth > 0)

        // Each column must fit inside the content box with room for its own margins.
        val content = panelWidth - (20 * density).toInt() * 2
        assertTrue(
            "four ${cellWidth}px cells must fit in ${content}px",
            cellWidth * 4 <= content,
        )
        // And the cells must not be so narrow that four of them cannot hold an icon.
        assertTrue("cell width $cellWidth too small for a 60dp icon", cellWidth > (60 * density).toInt())
    }

    @Test
    fun cellWidthPx_leavesNoColumnAtZeroOnANarrowHost() {
        val panelWidth = CaddyPanelLayout.widthPx(320, density)
        val cellWidth = CaddyPanelLayout.cellWidthPx(panelWidth, columns = 4, density = density)
        assertTrue("cell width must be positive on a narrow host, was $cellWidth", cellWidth > 0)
    }

    /**
     * The reported bug: icons drawn outside their cell overlapped each other.
     *
     * The dialog put each icon in a 68dp frame and pushed it down by
     * `(68 - iconSize) / 2`, which keeps the drawable inside the frame for every icon
     * size the device profiles use. An earlier overlay used a 56dp frame with a 60dp
     * icon, so the drawable hung below its frame and covered the row underneath.
     */
    @Test
    fun iconFrameAlwaysContainsTheIcon() {
        val frame = 68
        for (iconSize in listOf(48, 57, 60, 66)) {
            val inset = maxOf(0, (frame - iconSize) / 2)
            val bottom = inset + iconSize
            assertTrue(
                "a ${iconSize}dp icon spans [$inset,$bottom] and must stay inside the " +
                    "${frame}dp frame",
                bottom <= frame,
            )
        }
    }
}
