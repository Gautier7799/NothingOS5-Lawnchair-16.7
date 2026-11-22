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

package app.lawnchair.homecards

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Regression tests for the attach decision and for preserving saved placements across an
 * incomplete app snapshot.
 *
 * The controller hosts each card view inside a CellLayout's inner `ShortcutAndWidgetContainer`, so
 * `view.parent` is never the CellLayout. The owner must therefore be resolved from the live
 * hierarchy, and a view hosted on a different page must be moved rather than re-laid out in place.
 */
class CaddyHomeCardAttachmentTest {

    private fun card(id: Int, keys: List<String> = listOf("a", "b"), screenId: Int = 0) =
        CaddyHomeCardInfo(
            id = id,
            title = "Card $id",
            appKeys = keys,
            screenId = screenId,
        )

    @Test
    fun attachAction_addsWhenTheViewHasNoOwner() {
        assertThat(CaddyHomeCardAttachment.attachAction(hasOwner = false, ownerOnTargetPage = false))
            .isEqualTo(CaddyHomeCardAttachment.Action.ADD)
        // The target-page flag is irrelevant without an owner; it must not change the decision.
        assertThat(CaddyHomeCardAttachment.attachAction(hasOwner = false, ownerOnTargetPage = true))
            .isEqualTo(CaddyHomeCardAttachment.Action.ADD)
    }

    @Test
    fun attachAction_relayoutsWhenTheOwnerIsTheTargetPage() {
        assertThat(CaddyHomeCardAttachment.attachAction(hasOwner = true, ownerOnTargetPage = true))
            .isEqualTo(CaddyHomeCardAttachment.Action.RELAYOUT)
    }

    @Test
    fun attachAction_movesWhenTheOwnerIsADifferentPage() {
        // This is the cross-page case: the view is hosted by another page's container, so it must be
        // removed from that real owner before it can be added to the target.
        assertThat(CaddyHomeCardAttachment.attachAction(hasOwner = true, ownerOnTargetPage = false))
            .isEqualTo(CaddyHomeCardAttachment.Action.MOVE)
    }

    @Test
    fun carriedOver_keepsACardWhoseAppsAreStillInstalled() {
        val stored = listOf(card(1, keys = listOf("pkg/a", "pkg/b")))
        val carried = CaddyHomeCardAttachment.carriedOver(
            stored = stored,
            desiredIds = emptySet(),
            liveAppKeys = setOf("pkg/a", "pkg/b"),
        )
        assertThat(carried.map { it.id }).containsExactly(1)
    }

    @Test
    fun carriedOver_dropsACardWhoseAppsAreGone() {
        val stored = listOf(card(1, keys = listOf("pkg/gone")))
        val carried = CaddyHomeCardAttachment.carriedOver(
            stored = stored,
            desiredIds = emptySet(),
            liveAppKeys = setOf("pkg/other"),
        )
        assertThat(carried).isEmpty()
    }

    @Test
    fun carriedOver_ignoresCardsThatAreStillDesired() {
        val stored = listOf(card(1, keys = listOf("pkg/a")))
        val carried = CaddyHomeCardAttachment.carriedOver(
            stored = stored,
            desiredIds = setOf(1),
            liveAppKeys = setOf("pkg/a"),
        )
        assertThat(carried).isEmpty()
    }

    @Test
    fun carriedOver_keepsAPartiallyResolvedCard() {
        // Only one of the card's apps is present in the snapshot, but the card still holds a live
        // app, so its saved placement must survive the incomplete snapshot.
        val stored = listOf(card(1, keys = listOf("pkg/a", "pkg/not-yet-loaded")))
        val carried = CaddyHomeCardAttachment.carriedOver(
            stored = stored,
            desiredIds = emptySet(),
            liveAppKeys = setOf("pkg/a"),
        )
        assertThat(carried.map { it.id }).containsExactly(1)
    }

    @Test
    fun carriedOver_preservesTheSavedPlacementUnchanged() {
        val stored = listOf(
            CaddyHomeCardInfo(
                id = 7,
                title = "Tools",
                appKeys = listOf("pkg/a"),
                screenId = 2,
                cellX = 3,
                cellY = 1,
                spanX = 4,
                spanY = 2,
            ),
        )
        val carried = CaddyHomeCardAttachment.carriedOver(stored, emptySet(), setOf("pkg/a"))
        assertThat(carried.single().screenId).isEqualTo(2)
        assertThat(carried.single().cellX).isEqualTo(3)
        assertThat(carried.single().cellY).isEqualTo(1)
        assertThat(carried.single().spanX).isEqualTo(4)
        assertThat(carried.single().spanY).isEqualTo(2)
    }

    @Test
    fun resizeAction_keepsTheViewAttachedWhenItStaysOnItsPage() {
        // A resize on the same page must not detach the view. Detaching removed it from the
        // hierarchy, so the card disappeared for a frame and came back at the new size, which the
        // user saw as the card blinking on every resize.
        assertThat(CaddyHomeCardAttachment.resizeAction(hasView = true, viewOnTargetPage = true))
            .isEqualTo(CaddyHomeCardAttachment.ResizeAction.IN_PLACE)
    }

    @Test
    fun needsRebuild_trueWhenThereIsNoPreviousGeometry() {
        val next = CaddyHomeCardAttachment.CardGeometry(columns = 2, cellPx = 100, maxIcons = 4, rows = 2)
        assertThat(CaddyHomeCardAttachment.needsRebuild(null, next)).isTrue()
    }

    @Test
    fun needsRebuild_falseWhenCellSizeAndIconCountAreUnchanged() {
        // A resize that lands on the same geometry must not rebuild, or the card would be torn
        // down and repainted for nothing.
        val previous = CaddyHomeCardAttachment.CardGeometry(columns = 2, cellPx = 100, maxIcons = 4, rows = 2)
        val next = previous.copy()
        assertThat(CaddyHomeCardAttachment.needsRebuild(previous, next)).isFalse()
    }

    @Test
    fun needsRebuild_trueWhenTheCellSizeChanges() {
        val previous = CaddyHomeCardAttachment.CardGeometry(columns = 2, cellPx = 100, maxIcons = 4, rows = 2)
        val next = previous.copy(cellPx = 140)
        assertThat(CaddyHomeCardAttachment.needsRebuild(previous, next)).isTrue()
    }

    @Test
    fun needsRebuild_trueWhenOnlyTheIconCountChanges() {
        // This is the case a cell-size-only comparison missed: the cells stay the same size but
        // more rows fit, so the card must show more icons.
        val previous = CaddyHomeCardAttachment.CardGeometry(columns = 2, cellPx = 100, maxIcons = 4, rows = 2)
        val next = previous.copy(maxIcons = 6, rows = 3)
        assertThat(CaddyHomeCardAttachment.needsRebuild(previous, next)).isTrue()
    }

    @Test
    fun resizeAction_reattachesWhenTheViewIsMissingOrOnAnotherPage() {
        // No view yet: there is nothing on screen to preserve.
        assertThat(CaddyHomeCardAttachment.resizeAction(hasView = false, viewOnTargetPage = false))
            .isEqualTo(CaddyHomeCardAttachment.ResizeAction.REATTACH)
        // A stale target-page flag must not be trusted without a view.
        assertThat(CaddyHomeCardAttachment.resizeAction(hasView = false, viewOnTargetPage = true))
            .isEqualTo(CaddyHomeCardAttachment.ResizeAction.REATTACH)
        // Hosted on another page: the view must leave its owner, so a detach is required.
        assertThat(CaddyHomeCardAttachment.resizeAction(hasView = true, viewOnTargetPage = false))
            .isEqualTo(CaddyHomeCardAttachment.ResizeAction.REATTACH)
    }
}
