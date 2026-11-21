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

package app.lawnchair.ui.preferences.destinations

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.AppItemPlaceholder
import app.lawnchair.ui.preferences.components.layout.PreferenceLazyColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceScaffold
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.ui.preferences.components.reorderable.PositionalList
import app.lawnchair.ui.preferences.components.reorderable.PositionalListState
import app.lawnchair.ui.preferences.components.reorderable.rememberPositionalListState
import app.lawnchair.util.appsState
import com.android.launcher3.R

/**
 * Picks and orders the apps shown in the home-screen sidebar.
 *
 * Reuses the drawer folder's positional list: enabled apps come first in the chosen order and
 * the rest follow alphabetically, so one gesture both moves an app in or out and reorders it.
 */
@Composable
fun SelectAppsForSidebar(modifier: Modifier = Modifier) {
    val apps by appsState()
    val sidebarAppsAdapter = preferenceManager2().sidebarApps.getAdapter()

    val activeIds = remember(sidebarAppsAdapter.state.value) {
        sidebarAppsAdapter.state.value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    val (positionalItems, activeCount) = remember(apps, activeIds) {
        PositionalListState.prepareCategorizedItems(
            allItems = apps,
            enabledIds = activeIds,
            idSelector = { it.key.toString() },
        )
    }

    val loading = apps.isEmpty()

    val state = rememberPositionalListState(
        items = positionalItems,
        activeCount = activeCount,
        onOrderChange = { newList, newCount ->
            val sorted = PositionalListState.sortInactiveItems(newList, newCount) { it.label }
            val enabledKeys = PositionalListState.getEnabledKeys(sorted, newCount)
            sidebarAppsAdapter.onChange(enabledKeys.joinToString(","))
        },
        labelSelector = { it.label },
    )

    PreferenceScaffold(
        label = if (loading) {
            stringResource(R.string.loading)
        } else {
            stringResource(R.string.x_with_y_count, stringResource(R.string.sidebar_apps_label), state.activeCount)
        },
        modifier = modifier,
        isExpandedScreen = LocalIsExpandedScreen.current,
    ) { contentPadding ->
        if (loading) {
            PreferenceLazyColumn(contentPadding, enabled = false, state = rememberLazyListState()) {
                preferenceGroupItems(count = 20, isFirstChild = true) {
                    AppItemPlaceholder { Spacer(Modifier.width(24.dp)) }
                }
            }
        } else {
            PositionalList(
                state = state,
                itemContent = { app, dragHandle, toggle ->
                    AppItem(app = app, onClick = {}, widget = dragHandle, endWidget = toggle)
                },
                contentPadding = contentPadding,
            )
        }
    }
}
