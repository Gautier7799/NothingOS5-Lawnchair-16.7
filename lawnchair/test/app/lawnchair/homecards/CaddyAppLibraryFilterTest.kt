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

class CaddyAppLibraryFilterTest {

    @Test
    fun emptyQueryMatchesEveryCard() {
        assertThat(CaddyAppLibraryFilter.matches("", "Games", listOf("Chess"))).isTrue()
        assertThat(CaddyAppLibraryFilter.matches("   ", "Games", listOf("Chess"))).isTrue()
    }

    @Test
    fun matchesCategoryTitleCaseInsensitively() {
        assertThat(CaddyAppLibraryFilter.matches("gam", "Games", listOf("Chess"))).isTrue()
        assertThat(CaddyAppLibraryFilter.matches("GAMES", "Games", emptyList())).isTrue()
    }

    @Test
    fun matchesAnyAppTitle() {
        assertThat(CaddyAppLibraryFilter.matches("ches", "Games", listOf("Chess", "Checkers"))).isTrue()
        assertThat(CaddyAppLibraryFilter.matches("check", "Games", listOf("Chess", "Checkers"))).isTrue()
    }

    @Test
    fun noMatchWhenNeitherTitleNorAppsContainQuery() {
        assertThat(CaddyAppLibraryFilter.matches("photo", "Games", listOf("Chess"))).isFalse()
    }

    @Test
    fun queryIsTrimmedSoStraySpacesStillMatch() {
        assertThat(CaddyAppLibraryFilter.matches("  chess  ", "Games", listOf("Chess"))).isTrue()
    }
}
