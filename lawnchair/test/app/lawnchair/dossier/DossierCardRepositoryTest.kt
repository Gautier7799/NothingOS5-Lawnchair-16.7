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

class DossierCardRepositoryTest {

    /** In-memory store so the repository is exercised without Android. */
    private class FakeStore : DossierCardStore {
        var value: String? = null
        override fun read(): String? = value
        override fun write(value: String) {
            this.value = value
        }
    }

    private fun card(id: Int, title: String = "T$id") =
        DossierCardInfo(id = id, title = title, appKeys = listOf("0:p$id/.C"))

    @Test
    fun load_emptyStoreYieldsEmptyList() {
        assertThat(DossierCardRepository(FakeStore()).load()).isEmpty()
    }

    @Test
    fun saveThenLoad_roundTrips() {
        val repo = DossierCardRepository(FakeStore())
        val cards = listOf(card(1), card(2))

        repo.save(cards)

        assertThat(repo.load()).isEqualTo(cards)
    }

    @Test
    fun add_replacesSameIdAndKeepsOthers() {
        val repo = DossierCardRepository(FakeStore())
        repo.save(listOf(card(1), card(2)))

        repo.add(card(1, title = "updated"))

        assertThat(repo.load()).containsExactly(card(1, "updated"), card(2))
    }

    @Test
    fun update_changesMatchingCardOnly() {
        val repo = DossierCardRepository(FakeStore())
        repo.save(listOf(card(1), card(2)))

        repo.update(card(2).copy(spanX = 4, spanY = 3))

        assertThat(repo.load().first { it.id == 2 }.spanX).isEqualTo(4)
        assertThat(repo.load().first { it.id == 2 }.spanY).isEqualTo(3)
        assertThat(repo.load().first { it.id == 1 }.spanX).isEqualTo(DossierCardInfo.DEFAULT_SPAN)
    }

    @Test
    fun remove_dropsOnlyRequestedId() {
        val repo = DossierCardRepository(FakeStore())
        repo.save(listOf(card(1), card(2), card(3)))

        repo.remove(2)

        assertThat(repo.load().map { it.id }).containsExactly(1, 3).inOrder()
    }

    @Test
    fun nextId_isOnePastTheHighest() {
        val repo = DossierCardRepository(FakeStore())
        repo.save(listOf(card(3), card(11), card(5)))

        assertThat(repo.nextId()).isEqualTo(12)
    }

    @Test
    fun nextId_onEmptyStoreIsOne() {
        assertThat(DossierCardRepository(FakeStore()).nextId()).isEqualTo(1)
    }
}
