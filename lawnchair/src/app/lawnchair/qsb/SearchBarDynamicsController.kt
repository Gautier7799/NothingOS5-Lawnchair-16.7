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

package app.lawnchair.qsb

import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState

/**
 * Applies [SearchBarDynamics] to the home search bar.
 *
 * The launcher calls [refresh] whenever the relevant state changes: a workspace page transition,
 * a launcher state change, or a resume. [refresh] reads the current page and preference, decides
 * with [SearchBarDynamics.shouldShow], and fades the hotseat search bar (or its floating pill)
 * in or out. Nothing else about the search bar is touched, so its normal state animations keep
 * working.
 */
class SearchBarDynamicsController(private val launcher: Launcher) {

    private val prefs = PreferenceManager2.getInstance(launcher)

    /** The last applied visibility, so repeated refreshes do not restart the fade. */
    private var shown: Boolean? = null

    fun refresh() {
        val hotseat = launcher.hotseat ?: return
        val workspace = launcher.workspace
        val onDefaultPage = workspace != null &&
            workspace.currentPage == workspace.defaultPage
        val state = SearchBarState(
            dynamicEnabled = prefs.searchBarDynamic.firstCached(),
            workspaceVisible = launcher.isInState(LauncherState.NORMAL),
            onDefaultPage = onDefaultPage,
        )
        val shouldShow = SearchBarDynamics.shouldShow(state)
        if (shouldShow == shown) return
        shown = shouldShow
        hotseat.setSearchBarDynamicAlpha(if (shouldShow) 1f else 0f)
    }
}
