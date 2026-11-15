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
 * Guards the drag marker used to recognise a Caddy card on drop.
 *
 * The marker must never collide with a persisted `Favorites` item type, otherwise a
 * real home-screen row could be routed into the Caddy folder path. The persisted
 * types are duplicated here as literals on purpose: these tests must not load
 * `LauncherSettings` (its static initialiser touches the Android framework), and a
 * silent change to a persisted type should fail loudly rather than be masked.
 */
class CaddyFolderDragContractTest {

    /** Values of `LauncherSettings.Favorites.ITEM_TYPE_*` at the time of writing. */
    private val persistedItemTypes = listOf(
        0, // ITEM_TYPE_APPLICATION
        1, // ITEM_TYPE_SHORTCUT
        2, // ITEM_TYPE_FOLDER
        3, // ITEM_TYPE_DOSSIER
        4, // ITEM_TYPE_APPWIDGET
        5, // ITEM_TYPE_CUSTOM_APPWIDGET
        6, // ITEM_TYPE_DEEP_SHORTCUT
        7, // ITEM_TYPE_TASK
        8, // ITEM_TYPE_QSB
        9, // ITEM_TYPE_SEARCH_ACTION
        10, // ITEM_TYPE_APP_PAIR
        11, // ITEM_TYPE_PRIVATE_SPACE_INSTALL_APP_BUTTON
        -1, // ITEM_TYPE_NON_ACTIONABLE
    )

    @Test
    fun dragMarker_doesNotCollideWithAnyPersistedItemType() {
        assertThat(persistedItemTypes).doesNotContain(CaddyFolderDragContract.DRAG_ITEM_TYPE)
    }

    @Test
    fun dragSpan_matchesNativeFolderSize() {
        assertThat(CaddyFolderDragContract.SPAN_X).isEqualTo(1)
        assertThat(CaddyFolderDragContract.SPAN_Y).isEqualTo(1)
    }
}
