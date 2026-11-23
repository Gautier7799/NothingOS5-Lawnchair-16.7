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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Search is the one part of the sidebar where a wrong rule is invisible in a screenshot and
 * obvious to a user, so the normalisation and the ranking are pinned here.
 */
class SidebarSearchTest {

    @Test
    fun normalise_foldsCaseAndDiacritics() {
        assertEquals("editeur", SidebarSearch.normalise("Éditeur"))
        assertEquals("editeur", SidebarSearch.normalise("editeur"))
        assertEquals("cafe", SidebarSearch.normalise("Café"))
    }

    @Test
    fun normalise_dropsWhitespaceAndPunctuation() {
        // A user typing "you tube" or "you.tube" expects the same result as "youtube".
        assertEquals("youtube", SidebarSearch.normalise("You Tube"))
        assertEquals("youtube", SidebarSearch.normalise("you.tube"))
        assertEquals("youtube", SidebarSearch.normalise("  You-Tube  "))
    }

    @Test
    fun normalise_keepsDigits() {
        assertEquals("2048", SidebarSearch.normalise("2048"))
    }

    @Test
    fun matches_emptyQueryKeepsEverything() {
        assertTrue(SidebarSearch.matches("Anything", ""))
    }

    @Test
    fun matches_queryOfOnlyPunctuationKeepsEverything() {
        // Typing a stray space or dash must not empty the panel.
        assertTrue(SidebarSearch.matches("Maps", "   "))
        assertTrue(SidebarSearch.matches("Maps", "-"))
    }

    @Test
    fun matches_isCaseAndDiacriticInsensitive() {
        assertTrue(SidebarSearch.matches("Éditeur", "edit"))
        assertTrue(SidebarSearch.matches("YouTube", "you tube"))
        assertTrue(SidebarSearch.matches("YouTube", "TUBE"))
    }

    @Test
    fun matches_rejectsUnrelatedLabel() {
        assertFalse(SidebarSearch.matches("Maps", "mail"))
    }

    @Test
    fun rank_putsPrefixMatchesBeforeSubstringMatches() {
        val apps = listOf("YouTube", "Music Tube", "Maps")
        // "tu" is a prefix of "Tube...", a substring of "Music Tube".
        val ranked = SidebarSearch.rank(apps, "tube") { it }
        assertEquals(listOf("YouTube", "Music Tube"), ranked)
    }

    @Test
    fun rank_keepsInputOrderWithinARank() {
        val apps = listOf("Calendar", "Camera", "Candy")
        assertEquals(apps, SidebarSearch.rank(apps, "ca") { it })
    }

    @Test
    fun rank_emptyQueryReturnsInputUnchanged() {
        val apps = listOf("Zed", "Alpha")
        // The user's own sidebar ordering must not be reshuffled when not searching.
        assertEquals(apps, SidebarSearch.rank(apps, "") { it })
    }

    @Test
    fun rank_dropsNonMatchingItems() {
        val apps = listOf("Maps", "Mail", "Maps Pro")
        assertEquals(listOf("Maps", "Maps Pro"), SidebarSearch.rank(apps, "maps") { it })
    }

    @Test
    fun rank_worksWithAnyItemType() {
        data class App(val label: String, val id: Int)
        val apps = listOf(App("Notes", 1), App("Note", 2), App("Notebook Pro", 3))
        val ranked = SidebarSearch.rank(apps, "note") { it.label }
        // Both "Notes" and "Note" start with the query, so the input order survives; a label
        // that only contains it lands after them.
        assertEquals(listOf(1, 2, 3), ranked.map { it.id })
    }

    @Test
    fun rank_prefixBeatsSubstringAcrossTypes() {
        data class App(val label: String, val id: Int)
        val apps = listOf(App("My Notes", 1), App("Note", 2))
        val ranked = SidebarSearch.rank(apps, "note") { it.label }
        // "Note" is a prefix match and must come first even though it was listed second.
        assertEquals(listOf(2, 1), ranked.map { it.id })
    }
}
