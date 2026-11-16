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

/**
 * JVM tests for the Caddy folder resize geometry.
 *
 * The frame itself is Android UI (exercised on device), but the span/placement maths it
 * feeds into is pure and is what decides the folder's final size, so it is tested here.
 */
class CaddyFolderResizeTest {

    private fun start(cellX: Int = 0, cellY: Int = 0, spanX: Int = 1, spanY: Int = 1) =
        CaddyFolderResize.Placement(cellX, cellY, spanX, spanY)

    @Test
    fun draggingRightEdge_addsColumns() {
        val result = CaddyFolderResize.resize(
            start = start(spanX = 1, spanY = 1),
            deltaCellsX = 1.0f,
            deltaCellsY = 0f,
            left = false, right = true, top = false, bottom = false,
            countX = 4, countY = 4,
        )

        assertThat(result).isEqualTo(CaddyFolderResize.Placement(0, 0, 2, 1))
    }

    @Test
    fun draggingLeftEdgeOutward_growsTheFolder() {
        val result = CaddyFolderResize.resize(
            start = start(cellX = 2, spanX = 1),
            deltaCellsX = -1.0f,
            deltaCellsY = 0f,
            left = true, right = false, top = false, bottom = false,
            countX = 4, countY = 4,
        )

        // The right edge stays at column 3; the left edge moves one cell to the left.
        assertThat(result).isEqualTo(CaddyFolderResize.Placement(1, 0, 2, 1))
    }

    @Test
    fun draggingLeftEdgeInward_shrinksTheFolder() {
        val result = CaddyFolderResize.resize(
            start = start(cellX = 1, spanX = 3),
            deltaCellsX = 1.0f,
            deltaCellsY = 0f,
            left = true, right = false, top = false, bottom = false,
            countX = 4, countY = 4,
        )

        assertThat(result).isEqualTo(CaddyFolderResize.Placement(2, 0, 2, 1))
    }

    @Test
    fun smallMovementBelowThreshold_isIgnored() {
        val result = CaddyFolderResize.resize(
            start = start(),
            deltaCellsX = 0.3f,
            deltaCellsY = 0.3f,
            left = false, right = true, top = false, bottom = true,
            countX = 4, countY = 4,
        )

        assertThat(result).isEqualTo(CaddyFolderResize.Placement(0, 0, 1, 1))
    }

    @Test
    fun spanNeverShrinksBelowOneCell() {
        val result = CaddyFolderResize.resize(
            start = start(spanX = 1, spanY = 1),
            deltaCellsX = -3.0f,
            deltaCellsY = -3.0f,
            left = false, right = true, top = false, bottom = true,
            countX = 4, countY = 4,
        )

        assertThat(result.spanX).isEqualTo(CaddyFolderResize.MIN_SPAN)
        assertThat(result.spanY).isEqualTo(CaddyFolderResize.MIN_SPAN)
    }

    @Test
    fun placementIsClampedToTheGrid() {
        val result = CaddyFolderResize.resize(
            start = start(cellX = 0, spanX = 1),
            deltaCellsX = 5.0f,
            deltaCellsY = 5.0f,
            left = false, right = true, top = false, bottom = true,
            countX = 4, countY = 4,
        )

        // The span grows to fill the grid but never beyond it.
        assertThat(result.spanX).isEqualTo(4)
        assertThat(result.spanY).isEqualTo(4)
        assertThat(result.cellX).isEqualTo(0)
        assertThat(result.cellY).isEqualTo(0)
    }

    @Test
    fun leftEdgeCannotPushOriginOffGrid() {
        val result = CaddyFolderResize.resize(
            start = start(cellX = 0, spanX = 2),
            deltaCellsX = -2.0f,
            deltaCellsY = 0f,
            left = true, right = false, top = false, bottom = false,
            countX = 4, countY = 4,
        )

        // The left edge cannot move past column 0, so the span stays 2.
        assertThat(result.cellX).isEqualTo(0)
        assertThat(result.spanX).isEqualTo(2)
    }

    @Test
    fun draggingStartEdgeToShrinkStopsAtMinSpan() {
        val result = CaddyFolderResize.resize(
            start = start(cellX = 0, spanX = 2),
            deltaCellsX = 2.0f,
            deltaCellsY = 0f,
            left = true, right = false, top = false, bottom = false,
            countX = 4, countY = 4,
        )

        assertThat(result.cellX).isEqualTo(1)
        assertThat(result.spanX).isEqualTo(CaddyFolderResize.MIN_SPAN)
    }
}
