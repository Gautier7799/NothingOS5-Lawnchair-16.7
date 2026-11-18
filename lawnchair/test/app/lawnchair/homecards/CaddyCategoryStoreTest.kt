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

class CaddyCategoryStoreTest {

    @Test
    fun addCategory_startsANewListFromNull() {
        val stored = CaddyCategoryStore.addCategory(null, "Games", listOf("com.a#0"))
        assertThat(CaddyCategoryStore.titles(stored)).containsExactly("Games")
    }

    @Test
    fun addCategory_appendsToExistingCategories() {
        val first = CaddyCategoryStore.addCategory(null, "Games", listOf("com.a#0"))
        val second = CaddyCategoryStore.addCategory(first, "Work", listOf("com.b#0"))
        assertThat(CaddyCategoryStore.titles(second)).containsExactly("Games", "Work").inOrder()
    }

    @Test
    fun addCategory_trimsTitleAndDropsBlankKeys() {
        val stored = CaddyCategoryStore.addCategory(null, "  Games  ", listOf("com.a#0", "  ", "com.b#0"))
        assertThat(CaddyCategoryStore.titles(stored)).containsExactly("Games")
        assertThat(stored).contains("com.a#0")
        assertThat(stored).doesNotContain("\"\"")
    }

    @Test
    fun addCategory_blankTitleIsNoOp() {
        val existing = CaddyCategoryStore.addCategory(null, "Games", listOf("com.a#0"))
        assertThat(CaddyCategoryStore.addCategory(existing, "   ", listOf("com.b#0"))).isEqualTo(existing)
    }

    @Test
    fun addCategory_emptyAppListIsNoOp() {
        val existing = CaddyCategoryStore.addCategory(null, "Games", listOf("com.a#0"))
        assertThat(CaddyCategoryStore.addCategory(existing, "Work", emptyList())).isEqualTo(existing)
    }

    @Test
    fun addCategory_recoversFromCorruptStoredJson() {
        val stored = CaddyCategoryStore.addCategory("not-json{", "Games", listOf("com.a#0"))
        assertThat(CaddyCategoryStore.titles(stored)).containsExactly("Games")
    }

    @Test
    fun titles_ignoresEntriesWithoutATitle() {
        val stored = "[{\"apps\":[\"com.x#0\"]},{\"title\":\"Games\",\"apps\":[\"com.a#0\"]}]"
        assertThat(CaddyCategoryStore.titles(stored)).containsExactly("Games")
    }

    @Test
    fun titles_returnsEmptyForNullAndGarbage() {
        assertThat(CaddyCategoryStore.titles(null)).isEmpty()
        assertThat(CaddyCategoryStore.titles("garbage")).isEmpty()
    }
}
