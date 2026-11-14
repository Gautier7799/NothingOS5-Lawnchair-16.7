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

package app.lawnchair.dossier

import app.lawnchair.dossier.DossierResizeBounds.Placement
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DossierResizeBoundsTest {

    private val start = Placement(cellX = 1, cellY = 1, spanX = 2, spanY = 2)

    @Test
    fun smallDeltaBelowThresholdDoesNotChangeSpan() {
        val result = DossierResizeBounds.resize(
            start, deltaCellsX = 0.5f, deltaCellsY = 0.5f,
            left = false, right = true, top = false, bottom = true,
            countX = 5, countY = 5,
        )
        assertThat(result).isEqualTo(start)
    }

    @Test
    fun rightBorderGrowsWidthOnly() {
        val result = DossierResizeBounds.resize(
            start, deltaCellsX = 1f, deltaCellsY = 0f,
            left = false, right = true, top = false, bottom = false,
            countX = 5, countY = 5,
        )
        assertThat(result).isEqualTo(Placement(cellX = 1, cellY = 1, spanX = 3, spanY = 2))
    }

    @Test
    fun leftBorderMovesOriginAndGrowsWidth() {
        val result = DossierResizeBounds.resize(
            start, deltaCellsX = -1f, deltaCellsY = 0f,
            left = true, right = false, top = false, bottom = false,
            countX = 5, countY = 5,
        )
        assertThat(result).isEqualTo(Placement(cellX = 0, cellY = 1, spanX = 3, spanY = 2))
    }

    @Test
    fun bottomBorderGrowsHeightOnly() {
        val result = DossierResizeBounds.resize(
            start, deltaCellsX = 0f, deltaCellsY = 2f,
            left = false, right = false, top = false, bottom = true,
            countX = 5, countY = 5,
        )
        assertThat(result).isEqualTo(Placement(cellX = 1, cellY = 1, spanX = 2, spanY = 4))
    }

    @Test
    fun topBorderMovesOriginUpAndGrowsHeight() {
        val result = DossierResizeBounds.resize(
            start, deltaCellsX = 0f, deltaCellsY = -1f,
            left = false, right = false, top = true, bottom = false,
            countX = 5, countY = 5,
        )
        assertThat(result).isEqualTo(Placement(cellX = 1, cellY = 0, spanX = 2, spanY = 3))
    }

    @Test
    fun spanIsClampedToGridAndMinimum() {
        val shrunk = DossierResizeBounds.resize(
            start, deltaCellsX = -5f, deltaCellsY = -5f,
            left = false, right = true, top = false, bottom = true,
            countX = 5, countY = 5,
        )
        assertThat(shrunk.spanX).isEqualTo(DossierCardInfo.MIN_SPAN)
        assertThat(shrunk.spanY).isEqualTo(DossierCardInfo.MIN_SPAN)

        val grown = DossierResizeBounds.resize(
            start, deltaCellsX = 20f, deltaCellsY = 20f,
            left = false, right = true, top = false, bottom = true,
            countX = 4, countY = 3,
        )
        assertThat(grown.spanX).isEqualTo(4)
        assertThat(grown.spanY).isEqualTo(3)
    }

    @Test
    fun draggingBordersOutwardKeepsOriginAndSpanInsideGrid() {
        // Growing from the left/bottom moves the origin while widening the card.
        val result = DossierResizeBounds.resize(
            Placement(cellX = 4, cellY = 4, spanX = 1, spanY = 1),
            deltaCellsX = -3f, deltaCellsY = -3f,
            left = true, right = false, top = true, bottom = false,
            countX = 5, countY = 5,
        )
        assertThat(result.cellX).isEqualTo(1)
        assertThat(result.spanX).isEqualTo(4)
        assertThat(result.cellY).isEqualTo(1)
        assertThat(result.spanY).isEqualTo(4)
    }

    @Test
    fun placementNeverEscapesTheGrid() {
        // A card at the far corner cannot be pushed further out.
        val result = DossierResizeBounds.resize(
            Placement(cellX = 4, cellY = 4, spanX = 1, spanY = 1),
            deltaCellsX = -5f, deltaCellsY = -5f,
            left = true, right = false, top = true, bottom = false,
            countX = 5, countY = 5,
        )
        assertThat(result.cellX).isAtLeast(0)
        assertThat(result.cellY).isAtLeast(0)
        assertThat(result.cellX + result.spanX).isAtMost(5)
        assertThat(result.cellY + result.spanY).isAtMost(5)
    }
}
