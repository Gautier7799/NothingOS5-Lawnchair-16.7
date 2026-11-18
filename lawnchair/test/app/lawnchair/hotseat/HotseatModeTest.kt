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

package app.lawnchair.hotseat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HotseatModeTest {

    @Test
    fun values_containDisabledLawnchairAndGoogleSearch() {
        assertThat(HotseatMode.values())
            .containsExactly(DisabledHotseat, LawnchairHotseat, GoogleSearchHotseat)
    }

    @Test
    fun googleSearch_isOfferedAgain() {
        assertThat(HotseatMode.values().map { it.toString() }).contains("google_search")
    }

    @Test
    fun storedGoogleSearchPreference_resolvesToGoogleSearchDock() {
        assertThat(HotseatMode.fromString("google_search")).isSameInstanceAs(GoogleSearchHotseat)
    }

    @Test
    fun disabledAndLawnchair_stillRoundTrip() {
        assertThat(HotseatMode.fromString("disabled")).isSameInstanceAs(DisabledHotseat)
        assertThat(HotseatMode.fromString("lawnchair")).isSameInstanceAs(LawnchairHotseat)
        assertThat(HotseatMode.fromString("anything_else")).isSameInstanceAs(LawnchairHotseat)
    }

    @Test
    fun toStringValues_areStable() {
        assertThat(LawnchairHotseat.toString()).isEqualTo("lawnchair")
        assertThat(DisabledHotseat.toString()).isEqualTo("disabled")
        assertThat(GoogleSearchHotseat.toString()).isEqualTo("google_search")
    }

    @Test
    fun dockSearchBar_isOffWhenTheUserTurnsItOff() {
        assertThat(DockSearchBar.isEnabled(LawnchairHotseat, false)).isFalse()
    }

    @Test
    fun dockSearchBar_isOffForModesWithoutASearchBar() {
        assertThat(DockSearchBar.isEnabled(DisabledHotseat, true)).isFalse()
        assertThat(DockSearchBar.isEnabled(DisabledHotseat, false)).isFalse()
    }

    @Test
    fun dockSearchBar_isOnOnlyForModesWithASearchBarAndTheToggleEnabled() {
        assertThat(DockSearchBar.isEnabled(LawnchairHotseat, true)).isTrue()
        assertThat(DockSearchBar.isEnabled(GoogleSearchHotseat, true)).isTrue()
        assertThat(DockSearchBar.isEnabled(GoogleSearchHotseat, false)).isFalse()
    }
}
