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

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.util.ComponentKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Owns the home-screen sidebar's lifecycle.
 *
 * The sidebar is only meaningful on the workspace, so the tab is shown while the launcher is
 * in [LauncherState.NORMAL] and hidden as soon as any other state (all apps, overview,
 * widgets) takes over. Hiding rather than removing keeps the app list and its icons alive, so
 * returning to the workspace is instant.
 */
class SidebarCoordinator(private val launcher: Launcher) : StateManager.StateListener<LauncherState> {

    private var apps: List<AppInfo> = emptyList()
    private var prefsJob: Job? = null

    fun start() {
        launcher.stateManager.addStateListener(this)
        reload()
        observePreferences()
    }

    fun stop() {
        launcher.stateManager.removeStateListener(this)
        prefsJob?.cancel()
        prefsJob = null
        SidebarHandle.detach(launcher)
    }

    /** Re-binds the tab whenever either sidebar preference changes. */
    private fun observePreferences() {
        val prefs = PreferenceManager2.getInstance(launcher)
        val owner = launcher as LifecycleOwner
        prefsJob?.cancel()
        prefsJob = owner.lifecycleScope.launch {
            launch { prefs.sidebarEnabled.get().collect { reload() } }
            launch { prefs.sidebarApps.get().collect { reload() } }
        }
    }

    /** Re-reads the preference and the app list, then re-binds the tab. */
    fun reload() {
        val prefs = PreferenceManager2.getInstance(launcher)
        if (!prefs.sidebarEnabled.firstCached()) {
            SidebarHandle.detach(launcher)
            return
        }
        apps = resolveApps(prefs.sidebarApps.firstCached())
        SidebarHandle.attach(launcher) { SidebarView.show(launcher, apps) }
        syncVisibility(launcher.stateManager.state)
    }

    override fun onStateTransitionComplete(finalState: LauncherState) {
        syncVisibility(finalState)
    }

    private fun syncVisibility(state: LauncherState) {
        SidebarHandle.setVisible(launcher, state == LauncherState.NORMAL)
    }

    /**
     * Maps the saved component keys to live [AppInfo]s.
     *
     * A saved app can be uninstalled or moved to another profile, so keys that no longer
     * resolve are dropped rather than shown as blanks.
     *
     * When nothing has been chosen yet the sidebar falls back to every installed app. That
     * makes the panel useful the moment it is enabled: an empty column with no explanation
     * looks like the feature is broken rather than unconfigured.
     */
    private fun resolveApps(saved: String): List<AppInfo> {
        val store = launcher.appsView?.appsStore ?: return emptyList()
        if (saved.isBlank()) {
            return store.apps
                .filter { it.componentName != null }
                .sortedBy { it.title.toString().lowercase() }
        }
        return saved.split(',').mapNotNull { key ->
            ComponentKey.fromString(key.trim())?.let { store.getApp(it) }
        }
    }
}
