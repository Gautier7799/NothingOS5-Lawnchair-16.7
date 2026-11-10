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

class CaddyHomeCardSyncPolicyTest {

    private fun card(id: Int, title: String, apps: List<String>) = CaddyHomeCardInfo(
        id = id,
        title = title,
        appKeys = apps,
    )

    @Test
    fun select_seedsHomeFromDrawerOnlyWhenNoHomeCardsAreSaved() {
        val seed = listOf(card(1, "Utilities", listOf("a")))
        assertThat(CaddyHomeCardSyncPolicy.select(emptyList(), seed)).isEqualTo(seed)
    }

    @Test
    fun select_savedHomeContentWinsOverLaterDrawerChanges() {
        val saved = listOf(card(1, "My Home Group", listOf("a", "b")))
        val changedDrawer = listOf(card(2, "New Drawer Category", listOf("c")))
        assertThat(CaddyHomeCardSyncPolicy.select(saved, changedDrawer)).isEqualTo(saved)
    }

    @Test
    fun visible_hidesOnlyHomeCardsWithoutDeletingTheirStoredRecords() {
        val cards = listOf(
            card(1, "Keep", listOf("a")),
            card(2, "Hidden", listOf("b")),
        )
        assertThat(CaddyHomeCardSyncPolicy.visible(cards, setOf(2))).containsExactly(cards[0])
        assertThat(cards).hasSize(2)
    }

    @Test
    fun visible_restoresCardWhenItsHiddenIdIsRemoved() {
        val saved = card(3, "Restored", listOf("c"))
        assertThat(CaddyHomeCardSyncPolicy.visible(listOf(saved), emptySet()))
            .containsExactly(saved)
    }
}
