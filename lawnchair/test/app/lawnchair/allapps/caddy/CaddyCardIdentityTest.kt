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
 * Guards the Caddy card matching rules, the drawer's source of instability.
 *
 * Every Caddy card shares one `viewType` and one class, so `AdapterItem.isSameAs` compared only
 * those two and reported any two cards as the same item. `DiffUtil` cannot follow an item across a
 * reorder when items are indistinguishable, so it fell back to matching by position: a card that
 * moved (opening a category moves it to the front) handed its content to a neighbouring card's
 * view, and the next refresh swapped it back. The cards traded contents and rebound every refresh.
 */
class CaddyCardIdentityTest {

    @Test
    fun cardsWithDifferentTitlesAreDifferentIdentities() {
        assertThat(CaddyCardIdentity.of(false, false, "Media"))
            .isNotEqualTo(CaddyCardIdentity.of(false, false, "Games"))
    }

    @Test
    fun aCategoryKeepsItsIdentityWhenTheListIsRebuilt() {
        // The drawer rebuilds its whole item list on each refresh; identity must survive that.
        assertThat(CaddyCardIdentity.of(false, false, "Media"))
            .isEqualTo(CaddyCardIdentity.of(false, false, "Media"))
    }

    @Test
    fun titlesAreTrimmedSoStrayWhitespaceCannotSplitACard() {
        assertThat(CaddyCardIdentity.of(false, false, "  Media  "))
            .isEqualTo(CaddyCardIdentity.of(false, false, "Media"))
    }

    @Test
    fun aMissingTitleDoesNotCrash() {
        assertThat(CaddyCardIdentity.of(false, false, null)).isNotEmpty()
        assertThat(CaddyCardIdentity.of(false, false, null))
            .isNotEqualTo(CaddyCardIdentity.of(false, false, "Media"))
    }

    @Test
    fun utilityCardsAreDistinctFromCategoriesAndFromEachOther() {
        val addCategory = CaddyCardIdentity.of(false, true, "Add category")
        val search = CaddyCardIdentity.of(true, false, "Search")
        val media = CaddyCardIdentity.of(false, false, "Media")

        assertThat(addCategory).isNotEqualTo(search)
        assertThat(addCategory).isNotEqualTo(media)
        assertThat(search).isNotEqualTo(media)
    }

    @Test
    fun theAddCardIsNotMistakenForACategoryWithTheSameTitle() {
        // The "+ Add" card carries the title "Add category". Testing the title first would file
        // it as an ordinary category and let it match a user category of that name.
        assertThat(CaddyCardIdentity.of(false, true, "Add category"))
            .isNotEqualTo(CaddyCardIdentity.of(false, false, "Add category"))
    }

    @Test
    fun utilityCardsHaveStableIdentities() {
        assertThat(CaddyCardIdentity.of(true, false, null))
            .isEqualTo(CaddyCardIdentity.of(true, false, "anything"))
        assertThat(CaddyCardIdentity.of(false, true, null))
            .isEqualTo(CaddyCardIdentity.of(false, true, "anything"))
    }

    @Test
    fun appContentKeyIsStableForAnUnchangedApp() {
        assertThat(CaddyCardIdentity.appContentKey("a#0", 42, "App A"))
            .isEqualTo(CaddyCardIdentity.appContentKey("a#0", 42, "App A"))
    }

    @Test
    fun aSwappedIconPackChangesTheAppContentKey() {
        // Regression: the drawer must repaint when the icon pack changes. Every component key
        // stays the same across an icon pack switch, so only the bitmap identity can reveal it.
        val before = CaddyCardIdentity.appContentKey("com.a#0", bitmapIdentity = 11, title = "A")
        val after = CaddyCardIdentity.appContentKey("com.a#0", bitmapIdentity = 99, title = "A")
        assertThat(before).isNotEqualTo(after)
    }

    @Test
    fun aRenamedAppChangesTheAppContentKey() {
        assertThat(CaddyCardIdentity.appContentKey("com.a#0", 11, "Old name"))
            .isNotEqualTo(CaddyCardIdentity.appContentKey("com.a#0", 11, "New name"))
    }

    @Test
    fun appContentKeySeparatesFieldsSoViolatingContentCannotCollide() {
        // Naive concatenation would let "a|1" + "b" collide with "a" + "1|b".
        assertThat(CaddyCardIdentity.appContentKey("a|1", 2, "b"))
            .isNotEqualTo(CaddyCardIdentity.appContentKey("a", 1, "2|b"))
    }
}
