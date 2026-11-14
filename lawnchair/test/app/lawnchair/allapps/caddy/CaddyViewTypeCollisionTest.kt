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

import app.lawnchair.search.LawnchairSearchAdapterProvider
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Guards the drawer's view-type space against collisions.
 *
 * The Caddy cards and the search results share one adapter and are dispatched by `viewType`.
 * When a Caddy bit equals a search-result bit, the search list routes that result into the
 * Caddy bind path, which reads the (absent) `caddyTitle` and passed a null title into
 * `CaddyCardRenderer.renderCategoryCard`. That crashed the launcher with a
 * `NullPointerException`. `VIEW_TYPE_CADDY_CATEGORY = 1 << 11` used to equal
 * `SEARCH_RESULT_ICON_ROW` and `VIEW_TYPE_CADDY_SPACER = 1 << 12` used to equal
 * `SEARCH_RESULT_SMALL_ICON_ROW`.
 */
class CaddyViewTypeCollisionTest {

    private val caddyViewTypes = mapOf(
        "VIEW_TYPE_CADDY_CATEGORY" to BaseAllAppsAdapter.VIEW_TYPE_CADDY_CATEGORY,
        "VIEW_TYPE_CADDY_SPACER" to BaseAllAppsAdapter.VIEW_TYPE_CADDY_SPACER,
    )

    @Test
    fun caddyViewTypes_doNotCollideWithSearchResultViewTypes() {
        val searchViewTypes = LawnchairSearchAdapterProvider.viewTypeMap.values.toSet()
        for ((name, caddyType) in caddyViewTypes) {
            assertThat(searchViewTypes).doesNotContain(caddyType)
        }
    }

    @Test
    fun caddyViewTypes_areDistinctFromEachOtherAndFromFolderAndIcon() {
        val otherViewTypes = listOf(
            BaseAllAppsAdapter.VIEW_TYPE_ICON,
            BaseAllAppsAdapter.VIEW_TYPE_FOLDER,
        )
        val all = caddyViewTypes.values + otherViewTypes
        assertThat(all).containsNoDuplicates()
    }

    @Test
    fun caddyViewTypes_areStillIconViewTypes() {
        // The grid span/row logic keys off the icon mask, so the new bits must stay in it.
        for ((name, caddyType) in caddyViewTypes) {
            assertThat(BaseAllAppsAdapter.isIconViewType(caddyType)).isTrue()
        }
    }
}
