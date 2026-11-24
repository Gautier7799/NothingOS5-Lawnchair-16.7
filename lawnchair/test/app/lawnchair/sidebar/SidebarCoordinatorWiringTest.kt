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

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the two wiring faults that made the sidebar look broken on a device even though the
 * panel geometry and search logic passed their tests.
 *
 * 1. [SidebarCoordinator] resolved its app list once and never subscribed to `AllAppsStore`. An
 *    app installed, removed or renamed while the launcher ran never showed up, and a cold start
 *    that ran before `AppsView` existed resolved to an empty list and stayed empty.
 * 2. Only `sidebarEnabled` and `sidebarApps` were observed. Changing the edge, the vertical
 *    position, the column count or the opacity wrote the preference but left the tab and an open
 *    panel in the old geometry, so the setting appeared to do nothing until a restart.
 *
 * Neither fault throws, so no exception and no log line pointed at them. They are pinned here
 * against the source.
 */
class SidebarCoordinatorWiringTest {

    private fun sourceFile(relativePath: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "lawnchair/")) {
                val candidate = File(dir, prefix + relativePath)
                if (candidate.isFile) return candidate
            }
            dir = dir.parentFile
        }
        throw AssertionError("could not locate $relativePath from ${File(".").absolutePath}")
    }

    private fun coordinatorSource(): String =
        sourceFile("src/app/lawnchair/sidebar/SidebarCoordinator.kt").readText()

    @Test
    fun coordinatorSubscribesToAppUpdates() {
        val text = coordinatorSource()
        assertTrue(
            "the coordinator must register an AllAppsStore update listener, otherwise " +
                "installed and removed apps never reach the panel",
            text.contains("addUpdateListener"),
        )
        assertTrue(
            "the coordinator must unregister its listener in stop()",
            text.contains("removeUpdateListener"),
        )
    }

    @Test
    fun everyLayoutPreferenceIsObserved() {
        val text = coordinatorSource()
        val observed = listOf(
            "sidebarEnabled",
            "sidebarApps",
            "sidebarOnRightEdge",
            "sidebarHandlePosition",
            "sidebarColumns",
            "sidebarOpacity",
            "sidebarHaptics",
            "sidebarSuggestions",
        )
        observed.forEach { pref ->
            assertTrue(
                "$pref must be collected, or changing it has no visible effect",
                text.contains("prefs.$pref.get().collect"),
            )
        }
    }

    @Test
    fun workspaceBoundHookRetriesTheListener() {
        val text = coordinatorSource()
        assertTrue(
            "onWorkspaceBound must exist so a cold start can retry once AppsView is inflated",
            text.contains("fun onWorkspaceBound()"),
        )
    }

    @Test
    fun launcherUsesTheWorkspaceBoundHook() {
        val launcher = sourceFile("src/app/lawnchair/LawnchairLauncher.kt").readText()
        assertTrue(
            "finishBindingItems must call sidebar.onWorkspaceBound() rather than reload(), " +
                "so the listener retry happens",
            launcher.contains("sidebar.onWorkspaceBound()"),
        )
    }

    @Test
    fun anOpenPanelIsRefreshedOnReload() {
        val text = coordinatorSource()
        assertTrue(
            "reload() must refresh an open panel, or a settings change leaves stale icons " +
                "on screen until the panel is closed and reopened",
            text.contains("SidebarView.refresh(launcher, apps)"),
        )
    }

    @Test
    fun appearanceDoesNotBlankAnOpenPanel() {
        val view = sourceFile("src/app/lawnchair/sidebar/SidebarView.kt").readText()
        assertTrue(
            "applyAppearance must not reset alpha and translation on an open panel, " +
                "which made it invisible as soon as a layout preference changed",
            view.contains("if (isOpen) {"),
        )
    }

    @Test
    fun suggestionsRequireUsageAccessBeforeQuerying() {
        val text = coordinatorSource()
        assertTrue(
            "the coordinator must check usage access before ranking, because the platform " +
                "returns an empty result rather than an error when it is missing",
            text.contains("hasUsageAccess()"),
        )
    }
}
