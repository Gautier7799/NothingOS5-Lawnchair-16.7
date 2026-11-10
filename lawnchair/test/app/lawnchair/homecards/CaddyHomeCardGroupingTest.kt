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

class CaddyHomeCardGroupingTest {

    private fun card(id: Int, apps: List<String> = listOf("pkg$id#0"), cell: Pair<Int, Int> = 0 to 0) =
        CaddyHomeCardInfo(id = id, title = "Card $id", appKeys = apps, cellX = cell.first, cellY = cell.second)

    @Test
    fun stack_putsBothCardsInOneGroupAtTheTargetCell() {
        val cards = listOf(card(1, cell = 0 to 0), card(2, cell = 3 to 2))
        val merged = CaddyHomeCardGrouping.merge(cards, draggedId = 2, targetId = 1, mode = CaddyHomeCardGrouping.MODE_STACK)
        assertThat(merged).hasSize(2)
        val a = merged.first { it.id == 1 }
        val b = merged.first { it.id == 2 }
        assertThat(a.group).isNotEmpty()
        assertThat(b.group).isEqualTo(a.group)
        assertThat(b.cellX).isEqualTo(a.cellX)
        assertThat(b.cellY).isEqualTo(a.cellY)
        // Apps stay separate in a stack.
        assertThat(a.appKeys).containsExactly("pkg1#0")
        assertThat(b.appKeys).containsExactly("pkg2#0")
    }

    @Test
    fun combine_unionsAppsAndRemovesTheDraggedCard() {
        val cards = listOf(card(1, apps = listOf("a#0")), card(2, apps = listOf("b#0")))
        val merged = CaddyHomeCardGrouping.merge(cards, draggedId = 2, targetId = 1, mode = CaddyHomeCardGrouping.MODE_COMBINE)
        assertThat(merged).hasSize(1)
        assertThat(merged[0].id).isEqualTo(1)
        assertThat(merged[0].appKeys).containsExactly("a#0", "b#0")
        assertThat(merged[0].group).isEmpty()
    }

    @Test
    fun merge_sameOrMissingId_isNoOp() {
        val cards = listOf(card(1), card(2))
        assertThat(CaddyHomeCardGrouping.merge(cards, 1, 1, CaddyHomeCardGrouping.MODE_STACK)).isEqualTo(cards)
        assertThat(CaddyHomeCardGrouping.merge(cards, 9, 1, CaddyHomeCardGrouping.MODE_STACK)).isEqualTo(cards)
    }

    @Test
    fun cycle_rotatesTheVisibleMember() {
        var cards = listOf(card(1), card(2), card(3))
        cards = CaddyHomeCardGrouping.merge(cards, draggedId = 2, targetId = 1, mode = CaddyHomeCardGrouping.MODE_STACK)
        cards = CaddyHomeCardGrouping.merge(cards, draggedId = 3, targetId = 1, mode = CaddyHomeCardGrouping.MODE_STACK)
        val group = cards.first { it.id == 1 }.group
        assertThat(CaddyHomeCardGrouping.visible(cards, group)!!.id).isEqualTo(1)
        cards = CaddyHomeCardGrouping.cycle(cards, group)
        assertThat(CaddyHomeCardGrouping.visible(cards, group)!!.id).isEqualTo(2)
        cards = CaddyHomeCardGrouping.cycle(cards, group)
        assertThat(CaddyHomeCardGrouping.visible(cards, group)!!.id).isEqualTo(3)
        cards = CaddyHomeCardGrouping.cycle(cards, group)
        assertThat(CaddyHomeCardGrouping.visible(cards, group)!!.id).isEqualTo(1)
    }

    @Test
    fun removeFromGroup_dissolvesAGroupLeftWithOneCard() {
        var cards = listOf(card(1), card(2))
        cards = CaddyHomeCardGrouping.merge(cards, 2, 1, CaddyHomeCardGrouping.MODE_STACK)
        val group = cards.first { it.id == 1 }.group
        val remaining = CaddyHomeCardGrouping.removeFromGroup(cards, 2)
        assertThat(remaining).hasSize(1)
        assertThat(remaining[0].id).isEqualTo(1)
        assertThat(remaining[0].group).isEmpty()
        assertThat(group).isNotEmpty()
    }

    @Test
    fun roundTrip_preservesGroupAndIndex() {
        var cards = listOf(card(1), card(2))
        cards = CaddyHomeCardGrouping.merge(cards, 2, 1, CaddyHomeCardGrouping.MODE_STACK)
        val decoded = CaddyHomeCardCodec.decode(CaddyHomeCardCodec.encode(cards))
        assertThat(decoded).isEqualTo(cards)
    }
}
