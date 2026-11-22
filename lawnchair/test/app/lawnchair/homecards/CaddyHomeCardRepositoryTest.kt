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

class CaddyHomeCardRepositoryTest {

    private class InMemoryStore(var value: String? = null) : CaddyHomeCardStore {
        override fun read(): String? = value
        override fun write(value: String) {
            this.value = value
        }
    }

    private fun card(id: Int, title: String = "Cat $id") = CaddyHomeCardInfo(
        id = id,
        title = title,
        appKeys = listOf("com.pkg$id#0"),
    )

    @Test
    fun load_emptyStore_returnsEmptyList() {
        assertThat(CaddyHomeCardRepository(InMemoryStore()).load()).isEmpty()
    }

    @Test
    fun add_persistsAndReturnsUpdatedList() {
        val repository = CaddyHomeCardRepository(InMemoryStore())
        val result = repository.add(card(1))
        assertThat(result).hasSize(1)
        assertThat(repository.load()).hasSize(1)
    }

    @Test
    fun add_sameId_replacesInsteadOfDuplicating() {
        val repository = CaddyHomeCardRepository(InMemoryStore())
        repository.add(card(1, "First"))
        repository.add(card(1, "Second"))
        val loaded = repository.load()
        assertThat(loaded).hasSize(1)
        assertThat(loaded[0].title).isEqualTo("Second")
    }

    @Test
    fun remove_deletesOnlyMatchingId() {
        val repository = CaddyHomeCardRepository(InMemoryStore())
        repository.add(card(1))
        repository.add(card(2))
        val result = repository.remove(1)
        assertThat(result.map { it.id }).containsExactly(2)
    }

    @Test
    fun remove_missingId_isNoOp() {
        val repository = CaddyHomeCardRepository(InMemoryStore())
        repository.add(card(1))
        val result = repository.remove(99)
        assertThat(result.map { it.id }).containsExactly(1)
    }

    @Test
    fun update_replacesMatchingCard() {
        val repository = CaddyHomeCardRepository(InMemoryStore())
        repository.add(card(1, "Old"))
        val result = repository.update(card(1, "New"))
        assertThat(result).hasSize(1)
        assertThat(result[0].title).isEqualTo("New")
    }

    @Test
    fun nextId_emptyStore_returnsOne() {
        assertThat(CaddyHomeCardRepository(InMemoryStore()).nextId()).isEqualTo(1)
    }

    @Test
    fun nextId_returnsMaxPlusOne() {
        val repository = CaddyHomeCardRepository(InMemoryStore())
        repository.add(card(3))
        repository.add(card(7))
        assertThat(repository.nextId()).isEqualTo(8)
    }

    @Test
    fun save_thenLoad_isStableAcrossInstances() {
        val store = InMemoryStore()
        CaddyHomeCardRepository(store).save(listOf(card(1), card(2)))
        val reloaded = CaddyHomeCardRepository(store).load()
        assertThat(reloaded.map { it.id }).containsExactly(1, 2).inOrder()
    }

    @Test
    fun update_persistsResizedSpans() {
        val repository = CaddyHomeCardRepository(InMemoryStore())
        repository.add(card(1))
        repository.update(card(1).copy(spanX = 4, spanY = 3))
        val loaded = repository.load()
        assertThat(loaded[0].spanX).isEqualTo(4)
        assertThat(loaded[0].spanY).isEqualTo(3)
    }
}
