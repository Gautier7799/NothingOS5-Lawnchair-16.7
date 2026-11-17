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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DossierResizeHandlesTest {

    // 200x200 frame, 20px touch target on each edge.
    private val size = 200
    private val target = 20

    private fun borders(x: Int, y: Int) =
        DossierResizeHandles.activeBorders(x, y, size, size, target)

    @Test
    fun centerPointGrabsNoBorder() {
        val result = borders(100, 100)
        assertThat(result.any).isFalse()
        assertThat(result.left).isFalse()
        assertThat(result.right).isFalse()
        assertThat(result.top).isFalse()
        assertThat(result.bottom).isFalse()
    }

    @Test
    fun leftEdgeGrabsLeftOnly() {
        val result = borders(5, 100)
        assertThat(result.left).isTrue()
        assertThat(result.right).isFalse()
        assertThat(result.any).isTrue()
    }

    @Test
    fun rightEdgeGrabsRightOnly() {
        val result = borders(195, 100)
        assertThat(result.right).isTrue()
        assertThat(result.left).isFalse()
    }

    @Test
    fun topEdgeGrabsTopOnly() {
        val result = borders(100, 5)
        assertThat(result.top).isTrue()
        assertThat(result.bottom).isFalse()
    }

    @Test
    fun bottomEdgeGrabsBottomOnly() {
        val result = borders(100, 195)
        assertThat(result.bottom).isTrue()
        assertThat(result.top).isFalse()
    }

    @Test
    fun cornerGrabsBothEdges() {
        val result = borders(5, 5)
        assertThat(result.left).isTrue()
        assertThat(result.top).isTrue()
        assertThat(result.any).isTrue()
    }

    @Test
    fun boundaryIsExclusiveAtTouchTargetWidth() {
        // The comparison is strict, so exactly touchTargetWidth is already the inner region.
        val result = borders(target, target)
        assertThat(result.any).isFalse()
    }

    // --- DragLayer-coordinate dispatch path ---

    @Test
    fun dragLayerPointResolvesRelativeToFrameOrigin() {
        // Frame placed at (100, 200) in the DragLayer; a touch 5px inside its left edge must
        // read as a left grab, exactly as the local-coordinate path would.
        val result = DossierResizeHandles.activeBordersForDragLayerPoint(
            dragLayerX = 105,
            dragLayerY = 300,
            frameLeft = 100,
            frameTop = 200,
            frameWidth = size,
            frameHeight = size,
            touchTargetWidth = target,
        )
        assertThat(result.left).isTrue()
        assertThat(result.right).isFalse()
        assertThat(result.any).isTrue()
    }

    @Test
    fun dragLayerPointAndLocalPointAgree() {
        val frameLeft = 40
        val frameTop = 60
        val localX = 195
        val localY = 100
        val viaLocal = borders(localX, localY)
        val viaDragLayer = DossierResizeHandles.activeBordersForDragLayerPoint(
            dragLayerX = frameLeft + localX,
            dragLayerY = frameTop + localY,
            frameLeft = frameLeft,
            frameTop = frameTop,
            frameWidth = size,
            frameHeight = size,
            touchTargetWidth = target,
        )
        assertThat(viaDragLayer).isEqualTo(viaLocal)
    }

    // --- Touch-target clamping for small (single-cell) cards ---

    @Test
    fun touchTargetKeepsAFreeCentreOnASmallCard() {
        // A 100px card: the desired 48px target would leave only 4px of free centre, so it is
        // capped at a third of the side.
        val effective = DossierResizeHandles.effectiveTouchTarget(
            width = 100,
            height = 100,
            desired = 48,
            minTouchTarget = 12,
        )
        assertThat(effective).isEqualTo(33)
        // The centre must not read as a grab.
        assertThat(DossierResizeHandles.activeBorders(50, 50, 100, 100, effective).any).isFalse()
    }

    @Test
    fun touchTargetNeverDropsBelowTheMinimum() {
        val effective = DossierResizeHandles.effectiveTouchTarget(
            width = 30,
            height = 30,
            desired = 48,
            minTouchTarget = 12,
        )
        assertThat(effective).isAtLeast(12)
    }

    @Test
    fun touchTargetUnchangedWhenCardIsLarge() {
        val effective = DossierResizeHandles.effectiveTouchTarget(
            width = 600,
            height = 400,
            desired = 48,
            minTouchTarget = 12,
        )
        assertThat(effective).isEqualTo(48)
    }

    @Test
    fun touchTargetFallsBackToMinimumForUnmeasuredCard() {
        val effective = DossierResizeHandles.effectiveTouchTarget(
            width = 0,
            height = 0,
            desired = 48,
            minTouchTarget = 12,
        )
        assertThat(effective).isEqualTo(12)
    }
}
