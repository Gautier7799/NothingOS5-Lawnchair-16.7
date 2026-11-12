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
 * Regression tests for the Caddy Home duplicate-view and stale-occupancy defects.
 *
 * The controller reuses a card's view token across resize/move and releases its cells on removal.
 * These tests pin that bookkeeping so a future change cannot silently reintroduce a second view for
 * the same card id or leave cells occupied after a card is gone.
 */
class CaddyHomeCardPlacementTest {

    private fun card(
        id: Int,
        x: Int = 0,
        y: Int = 0,
        spanX: Int = 2,
        spanY: Int = 2,
        screenId: Int = 0,
    ) = CaddyHomeCardInfo(
        id = id,
        title = "Card $id",
        appKeys = listOf("a", "b"),
        screenId = screenId,
        cellX = x,
        cellY = y,
        spanX = spanX,
        spanY = spanY,
    )

    @Test
    fun tokenFor_isStableAcrossResizeAndMove() {
        val placement = CaddyHomeCardPlacement()
        placement.occupy(card(1, x = 0, y = 0))
        val token = placement.tokenFor(1)

        // A resize/move releases cells but keeps the token, then re-occupies the same card.
        placement.releaseCells(1)
        placement.occupy(card(1, x = 0, y = 0, spanX = 4, spanY = 4))

        assertThat(placement.tokenFor(1)).isEqualTo(token)
        assertThat(placement.attachedCount()).isEqualTo(1)
    }

    @Test
    fun resize_doesNotCreateDuplicateViews() {
        val placement = CaddyHomeCardPlacement()
        placement.occupy(card(1))
        placement.releaseCells(1)
        placement.occupy(card(1, spanX = 4, spanY = 2))

        assertThat(placement.attachedCount()).isEqualTo(1)
        assertThat(placement.attachedIds()).containsExactly(1)
    }

    @Test
    fun move_doesNotCreateDuplicateViews() {
        val placement = CaddyHomeCardPlacement()
        placement.occupy(card(1, x = 0, y = 0))
        placement.releaseCells(1)
        placement.occupy(card(1, x = 2, y = 0))

        assertThat(placement.attachedCount()).isEqualTo(1)
        assertThat(placement.attachedIds()).containsExactly(1)
    }

    @Test
    fun removal_releasesOccupiedCells() {
        val placement = CaddyHomeCardPlacement()
        placement.occupy(card(1, x = 0, y = 0))

        assertThat(placement.release(1)).isTrue()
        assertThat(placement.isAttached(1)).isFalse()
        // The freed region must be reusable by another card.
        assertThat(placement.isRegionFree(0, 0, 0, 2, 2, columns = 4, rows = 4)).isTrue()
    }

    @Test
    fun releaseCells_keepsViewTokenButFreesCells() {
        val placement = CaddyHomeCardPlacement()
        placement.occupy(card(1, x = 0, y = 0))

        placement.releaseCells(1)

        assertThat(placement.isAttached(1)).isTrue()
        assertThat(placement.isRegionFree(0, 0, 0, 2, 2, columns = 4, rows = 4)).isTrue()
    }

    @Test
    fun detachAll_leavesNoOrphanedViewsOrCells() {
        val placement = CaddyHomeCardPlacement()
        placement.occupy(card(1, x = 0, y = 0))
        placement.occupy(card(2, x = 2, y = 0))

        placement.attachedIds().forEach { placement.release(it) }
        placement.clear()

        assertThat(placement.attachedCount()).isEqualTo(0)
        assertThat(placement.isRegionFree(0, 0, 0, 2, 2, columns = 4, rows = 4)).isTrue()
        assertThat(placement.isRegionFree(0, 2, 0, 2, 2, columns = 4, rows = 4)).isTrue()
    }

    @Test
    fun reattach_doesNotProduceOverlappingInstances() {
        val placement = CaddyHomeCardPlacement()
        placement.occupy(card(1, x = 0, y = 0))

        // Re-attaching the same card at the same place must not stack a second record.
        placement.occupy(card(1, x = 0, y = 0))

        assertThat(placement.attachedCount()).isEqualTo(1)
        assertThat(placement.cardAt(0, 0, 0, 2, 2)?.id).isEqualTo(1)
    }

    @Test
    fun occupy_movingCardBetweenScreensReleasesOldScreenCells() {
        val placement = CaddyHomeCardPlacement()
        placement.occupy(card(1, x = 0, y = 0, screenId = 0))

        placement.occupy(card(1, x = 2, y = 0, screenId = 1))

        assertThat(placement.isRegionFree(0, 0, 0, 2, 2, columns = 4, rows = 4)).isTrue()
        assertThat(placement.isRegionFree(1, 2, 0, 2, 2, columns = 4, rows = 4)).isFalse()
        assertThat(placement.attachedIds()).containsExactly(1)
    }

    @Test
    fun isRegionFree_rejectsOverlapWithAnotherCard() {
        val placement = CaddyHomeCardPlacement()
        placement.occupy(card(1, x = 0, y = 0, spanX = 2, spanY = 2))

        assertThat(placement.isRegionFree(0, 1, 1, 2, 2, columns = 4, rows = 4)).isFalse()
        assertThat(placement.isRegionFree(0, 2, 0, 2, 2, columns = 4, rows = 4)).isTrue()
    }

    @Test
    fun isRegionFree_rejectsOutOfBounds() {
        val placement = CaddyHomeCardPlacement()

        assertThat(placement.isRegionFree(0, 3, 0, 2, 2, columns = 4, rows = 4)).isFalse()
        assertThat(placement.isRegionFree(0, -1, 0, 2, 2, columns = 4, rows = 4)).isFalse()
    }

    @Test
    fun cardAt_findsTheCardUnderTheDropRegion() {
        val placement = CaddyHomeCardPlacement()
        placement.occupy(card(7, x = 2, y = 2))

        assertThat(placement.cardAt(0, 2, 2, 2, 2)?.id).isEqualTo(7)
        assertThat(placement.cardAt(0, 0, 0, 1, 1)).isNull()
    }
}
