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
 * JVM tests for which edge of the Caddy resize frame a touch is grabbing.
 */
class CaddyFolderResizeHandlesTest {

    private val width = 400
    private val height = 300
    private val target = 40

    @Test
    fun middleOfTheFrame_grabsNothing() {
        val borders = CaddyFolderResizeHandles.activeBorders(
            x = 200, y = 150, width = width, height = height, touchTargetWidth = target,
        )

        assertThat(borders.any).isFalse()
    }

    @Test
    fun nearLeftEdge_grabsLeftOnly() {
        val borders = CaddyFolderResizeHandles.activeBorders(
            x = 10, y = 150, width = width, height = height, touchTargetWidth = target,
        )

        assertThat(borders.left).isTrue()
        assertThat(borders.right).isFalse()
        assertThat(borders.top).isFalse()
        assertThat(borders.bottom).isFalse()
    }

    @Test
    fun nearBottomRightCorner_grabsBothEdges() {
        val borders = CaddyFolderResizeHandles.activeBorders(
            x = width - 5, y = height - 5, width = width, height = height, touchTargetWidth = target,
        )

        assertThat(borders.right).isTrue()
        assertThat(borders.bottom).isTrue()
        assertThat(borders.left).isFalse()
        assertThat(borders.top).isFalse()
    }

    @Test
    fun nearTopLeftCorner_grabsBothEdges() {
        val borders = CaddyFolderResizeHandles.activeBorders(
            x = 1, y = 1, width = width, height = height, touchTargetWidth = target,
        )

        assertThat(borders.left).isTrue()
        assertThat(borders.top).isTrue()
    }

    @Test
    fun revealAllowed_whenPendingFolderIsSettledInNormal() {
        assertThat(
            CaddyFolderResizeHandles.shouldRevealFrame(
                hasPendingFolder = true,
                inNormalState = true,
                inStateTransition = false,
                inPageTransition = false,
            ),
        ).isTrue()
    }

    @Test
    fun revealBlocked_whenNoPendingFolder() {
        assertThat(
            CaddyFolderResizeHandles.shouldRevealFrame(
                hasPendingFolder = false,
                inNormalState = true,
                inStateTransition = false,
                inPageTransition = false,
            ),
        ).isFalse()
    }

    @Test
    fun revealBlocked_duringStateTransition() {
        assertThat(
            CaddyFolderResizeHandles.shouldRevealFrame(
                hasPendingFolder = true,
                inNormalState = true,
                inStateTransition = true,
                inPageTransition = false,
            ),
        ).isFalse()
    }

    @Test
    fun revealBlocked_duringPageTransition() {
        // The empty-screen strip that follows a drop is a page transition, which closes
        // TYPE_WIDGET_RESIZE_FRAME; revealing then made the handles flash and vanish.
        assertThat(
            CaddyFolderResizeHandles.shouldRevealFrame(
                hasPendingFolder = true,
                inNormalState = true,
                inStateTransition = false,
                inPageTransition = true,
            ),
        ).isFalse()
    }

    @Test
    fun revealBlocked_whenNotInNormalState() {
        assertThat(
            CaddyFolderResizeHandles.shouldRevealFrame(
                hasPendingFolder = true,
                inNormalState = false,
                inStateTransition = false,
                inPageTransition = false,
            ),
        ).isFalse()
    }

    // --- effectiveTouchTarget: keeps a free centre on small folders ---------------------

    @Test
    fun touchTarget_largeFrame_keepsDesiredThickness() {
        // 400x300: a third of the shorter side is 100, so the 48dp target is not capped.
        assertThat(
            CaddyFolderResizeHandles.effectiveTouchTarget(
                width = 400, height = 300, desired = 48, minTouchTarget = 13,
            ),
        ).isEqualTo(48)
    }

    @Test
    fun touchTarget_singleCellFolder_isCappedToLeaveACentre() {
        // 90x90 (one cell): a third is 30, so the desired 48 is capped to 30.
        assertThat(
            CaddyFolderResizeHandles.effectiveTouchTarget(
                width = 90, height = 90, desired = 48, minTouchTarget = 13,
            ),
        ).isEqualTo(30)
    }

    @Test
    fun touchTarget_neverDropsBelowMinimum() {
        // 24x24: a third is 8, below the 13 minimum, so the minimum wins.
        assertThat(
            CaddyFolderResizeHandles.effectiveTouchTarget(
                width = 24, height = 24, desired = 48, minTouchTarget = 13,
            ),
        ).isEqualTo(13)
    }

    @Test
    fun touchTarget_smallFrame_leavesTheMiddleFree() {
        val width = 90
        val height = 90
        val target = CaddyFolderResizeHandles.effectiveTouchTarget(
            width = width, height = height, desired = 48, minTouchTarget = 13,
        )

        // A tap in the exact centre must not read as a resize grab.
        val middle = CaddyFolderResizeHandles.activeBorders(
            x = width / 2, y = height / 2, width = width, height = height,
            touchTargetWidth = target,
        )

        assertThat(middle.any).isFalse()
    }

    @Test
    fun touchTarget_degenerateSize_fallsBackToMinimum() {
        assertThat(
            CaddyFolderResizeHandles.effectiveTouchTarget(
                width = 0, height = 0, desired = 48, minTouchTarget = 13,
            ),
        ).isEqualTo(13)
    }
}
