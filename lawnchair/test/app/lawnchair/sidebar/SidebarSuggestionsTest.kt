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

package app.lawnchair.sidebar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the merge rules that decide what the sidebar shows, pinned apps first. */
class SidebarSuggestionsTest {

    @Test
    fun `pinned apps come first in their chosen order`() {
        val merged = SidebarSuggestions.merge(
            pinned = listOf("b", "a"),
            ranked = listOf("c", "d"),
            suggestionCount = 2,
        )
        assertEquals(listOf("b", "a", "c", "d"), merged)
    }

    @Test
    fun `a pinned app is never repeated as a suggestion`() {
        val merged = SidebarSuggestions.merge(
            pinned = listOf("a"),
            ranked = listOf("a", "b"),
            suggestionCount = 2,
        )
        assertEquals(listOf("a", "b"), merged)
    }

    @Test
    fun `duplicate pins collapse to one entry`() {
        val merged = SidebarSuggestions.merge(
            pinned = listOf("a", "a", "b"),
            ranked = emptyList(),
            suggestionCount = 0,
        )
        assertEquals(listOf("a", "b"), merged)
    }

    @Test
    fun `suggestion count caps the tail`() {
        val merged = SidebarSuggestions.merge(
            pinned = listOf("a"),
            ranked = listOf("b", "c", "d", "e"),
            suggestionCount = 2,
        )
        assertEquals(listOf("a", "b", "c"), merged)
    }

    @Test
    fun `disabled suggestions leave only the pinned apps`() {
        val merged = SidebarSuggestions.merge(
            pinned = listOf("a"),
            ranked = listOf("b", "c"),
            enabled = false,
        )
        assertEquals(listOf("a"), merged)
    }

    @Test
    fun `a zero count leaves only the pinned apps`() {
        val merged = SidebarSuggestions.merge(
            pinned = listOf("a"),
            ranked = listOf("b"),
            suggestionCount = 0,
        )
        assertEquals(listOf("a"), merged)
    }

    @Test
    fun `blank keys are dropped so the panel shows no empty cells`() {
        val merged = SidebarSuggestions.merge(
            pinned = listOf("", "a"),
            ranked = listOf("", "b"),
            suggestionCount = 2,
        )
        assertEquals(listOf("a", "b"), merged)
    }

    @Test
    fun `pinned count marks where the suggestions start`() {
        val pinned = listOf("a", "b")
        val merged = SidebarSuggestions.merge(pinned, listOf("c"), suggestionCount = 1)
        assertEquals(2, SidebarSuggestions.pinnedCount(pinned, merged))
    }

    @Test
    fun `pinned count is zero when nothing is pinned`() {
        val merged = SidebarSuggestions.merge(emptyList(), listOf("c"), suggestionCount = 1)
        assertEquals(0, SidebarSuggestions.pinnedCount(emptyList(), merged))
    }

    @Test
    fun `an empty input yields an empty list`() {
        assertTrue(SidebarSuggestions.merge(emptyList(), emptyList()).isEmpty())
    }
}
