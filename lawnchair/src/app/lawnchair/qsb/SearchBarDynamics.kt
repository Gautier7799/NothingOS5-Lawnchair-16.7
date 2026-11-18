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

/**
 * The inputs that decide whether the home search bar is shown.
 *
 * Kept as a plain value so the rule can be exercised on the JVM without a launcher.
 */
data class SearchBarState(
    /** The user has not turned the dynamic behaviour off. */
    val dynamicEnabled: Boolean,
    /** The launcher is on the workspace, not in All Apps, Overview or another state. */
    val workspaceVisible: Boolean,
    /** The workspace is resting on the main (default) page. */
    val onDefaultPage: Boolean,
)

/**
 * Decides when the search bar is visible.
 *
 * The search bar is a large affordance: on the main page it is where the user expects it, but on
 * the side pages it sits on top of the icons and gets in the way. When [SearchBarState.dynamicEnabled]
 * is set the bar is therefore shown only on the main page, and hidden as soon as the workspace
 * moves to a side page or leaves the workspace. Turning the option off restores the bar everywhere
 * the launcher would normally show it.
 */
object SearchBarDynamics {

    fun shouldShow(state: SearchBarState): Boolean {
        if (!state.workspaceVisible) return true
        if (!state.dynamicEnabled) return true
        return state.onDefaultPage
    }
}
