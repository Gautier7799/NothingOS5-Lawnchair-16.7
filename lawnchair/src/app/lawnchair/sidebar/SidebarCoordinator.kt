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

import android.app.AppOpsManager
import android.os.Process
import android.view.WindowManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.allapps.AllAppsStore
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
    private var listeningToApps = false
    private var workspaceReady = false

    /** Re-resolves the sidebar apps when the model publishes a new app list. */
    private val appsListener = AllAppsStore.OnUpdateListener { onAppsChanged() }

    fun start() {
        launcher.stateManager.addStateListener(this)
        attachAppsListener()
        reload()
        observePreferences()
    }

    fun stop() {
        launcher.stateManager.removeStateListener(this)
        prefsJob?.cancel()
        prefsJob = null
        detachAppsListener()
        workspaceReady = false
        SidebarHandle.detach(launcher)
    }

    /**
     * Subscribes to model updates so an app that is installed, uninstalled or renamed while the
     * launcher is running shows up without a restart.
     *
     * `onCreate` can run before `AppsView` is inflated, in which case there is no store yet;
     * [onWorkspaceBound] retries once the workspace has bound.
     */
    private fun attachAppsListener() {
        if (listeningToApps) return
        val store = launcher.appsView?.appsStore ?: return
        store.addUpdateListener(appsListener)
        listeningToApps = true
    }

    private fun detachAppsListener() {
        if (!listeningToApps) return
        launcher.appsView?.appsStore?.removeUpdateListener(appsListener)
        listeningToApps = false
    }

    /** Called after the workspace has bound, when the app store is guaranteed to exist. */
    fun onWorkspaceBound() {
        attachAppsListener()
        workspaceReady = true
        reload()
    }

    private fun onAppsChanged() {
        if (!workspaceReady) return
        reload()
    }

    /**
     * Re-binds the tab whenever a preference that changes what the panel shows or where it sits
     * changes.
     *
     * Only `sidebarEnabled` and `sidebarApps` were observed before, so changing the edge, the
     * vertical position or the column count updated the preference store but left the tab and an
     * already open panel in their old geometry — the setting appeared to do nothing until the
     * launcher was restarted.
     */
    private fun observePreferences() {
        val prefs = PreferenceManager2.getInstance(launcher)
        val owner = launcher as LifecycleOwner
        prefsJob?.cancel()
        prefsJob = owner.lifecycleScope.launch {
            launch { prefs.sidebarEnabled.get().collect { reload() } }
            launch { prefs.sidebarApps.get().collect { reload() } }
            launch { prefs.sidebarOnRightEdge.get().collect { reload() } }
            launch { prefs.sidebarHandlePosition.get().collect { reload() } }
            launch { prefs.sidebarColumns.get().collect { reload() } }
            launch { prefs.sidebarOpacity.get().collect { reload() } }
            launch { prefs.sidebarHaptics.get().collect { reload() } }
            launch { prefs.sidebarSuggestions.get().collect { reload() } }
            launch { prefs.sidebarGestureBindings.get().collect { reload() } }
            launch { prefs.sidebarGesturesEnabled.get().collect { reload() } }
            launch { prefs.sidebarSwipeSensitivity.get().collect { reload() } }
            launch { prefs.sidebarTheme.get().collect { reload() } }
            launch { prefs.sidebarShowLabels.get().collect { reload() } }
            launch { prefs.sidebarShowOutline.get().collect { reload() } }
            launch { prefs.sidebarAutoHideFullscreen.get().collect { reload() } }
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
        // When the master switch is off the handler is not passed at all, so the handle falls
        // back to "pull to open" and a stray drag cannot run the user's bindings.
        val gesturesOn = prefs.sidebarGesturesEnabled.firstCached(prefs)
        val bindings = SidebarGestureBindings.resolve(prefs.sidebarGestureBindings.firstCached(prefs))
        SidebarHandle.attach(
            launcher,
            onOpen = { SidebarView.show(launcher, apps) },
            onGesture = if (gesturesOn) {
                { gesture -> performGesture(gesture, bindings) }
            } else {
                null
            },
            onGestureHasDoubleTap = {
                SidebarGestureBindings.actionFor(bindings, SidebarGesture.DOUBLE_TAP) !=
                    SidebarGestureAction.NONE
            },
        )
        // Keep an already open panel in step with the new list or geometry instead of leaving
        // stale icons on screen until it is closed and reopened.
        SidebarView.refresh(launcher, apps)
        syncVisibility(launcher.stateManager.state)
    }

    /**
     * Runs the action bound to [gesture].
     *
     * Every action goes through the launcher's own state manager or a public platform intent, so
     * none of them needs a permission. An unbound direction does nothing rather than falling back
     * to opening the panel, which is what makes "swipe down does nothing" a real option.
     */
    private fun performGesture(
        gesture: SidebarGesture,
        bindings: Map<SidebarGesture, SidebarGestureAction>,
    ) {
        when (SidebarGestureBindings.actionFor(bindings, gesture)) {
            SidebarGestureAction.NONE -> Unit

            SidebarGestureAction.OPEN_PANEL -> SidebarView.show(launcher, apps)

            SidebarGestureAction.OPEN_ALL_APPS -> launcher.stateManager.goToState(LauncherState.ALL_APPS)

            SidebarGestureAction.OPEN_SEARCH -> {
                // The search field only exists once all-apps is laid out, so focus it after the
                // state transition rather than trying to focus a view that is not there yet.
                launcher.stateManager.goToState(LauncherState.ALL_APPS)
                launcher.appsView?.searchUiManager?.editText?.requestFocus()
            }

            SidebarGestureAction.OPEN_ASSISTANT -> QuickControlAction.assistant(launcher)

            SidebarGestureAction.OPEN_NOTIFICATIONS -> QuickControlAction.notifications(launcher)

            SidebarGestureAction.OPEN_QUICK_SETTINGS -> QuickControlAction.quickSettings(launcher)

            SidebarGestureAction.OPEN_POWER_MENU -> QuickControlAction.powerMenu(launcher)
        }
    }

    override fun onStateTransitionComplete(finalState: LauncherState) {
        syncVisibility(finalState)
    }

    private fun syncVisibility(state: LauncherState) {
        val normal = state == LauncherState.NORMAL
        // The tab lives in the launcher window, so it is already hidden whenever another app is
        // in front. The switch still matters for the launcher's own immersive states: showing a
        // tab over fullscreen video or a game is the case the user asked to suppress.
        val hiddenByFullscreen = PreferenceManager2.getInstance(launcher)
            .sidebarAutoHideFullscreen.firstCached() && isSystemImmersive()
        SidebarHandle.setVisible(launcher, normal && !hiddenByFullscreen)
    }

    /** Whether the launcher window is currently running fullscreen. */
    private fun isSystemImmersive(): Boolean {
        val flags = launcher.window?.attributes?.flags ?: return false
        return (flags and WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0
    }

    /**
     * Maps the saved component keys to live [AppInfo]s, then appends usage-ranked suggestions.
     *
     * A saved app can be uninstalled or moved to another profile, so keys that no longer
     * resolve are dropped rather than shown as blanks.
     *
     * When nothing has been chosen yet and suggestions are off, the sidebar falls back to every
     * installed app. That makes the panel useful the moment it is enabled: an empty column with
     * no explanation looks like the feature is broken rather than unconfigured.
     */
    private fun resolveApps(saved: String): List<AppInfo> {
        val store = launcher.appsView?.appsStore ?: return emptyList()
        val suggestionsEnabled = PreferenceManager2.getInstance(launcher)
            .sidebarSuggestions.firstCached()
        if (saved.isBlank() && !suggestionsEnabled) {
            return store.apps
                .filter { it.componentName != null }
                .sortedBy { it.title.toString().lowercase() }
        }

        val pinned = saved.split(',')
            .mapNotNull { key -> ComponentKey.fromString(key.trim())?.let { store.getApp(it) } }
        if (!suggestionsEnabled) return pinned

        val pinnedPackages = pinned.mapNotNull { it.componentName?.packageName }.toSet()
        val suggestions = suggestionPackages(exclude = pinnedPackages).mapNotNull { pkg ->
            store.apps.firstOrNull { it.componentName?.packageName == pkg }
        }
        return pinned + suggestions
    }

    /**
     * Package names of the most used apps, most used first.
     *
     * Reads the same usage data the launcher's prediction engine uses, but only when the user
     * has granted usage access: without it the platform returns an empty result rather than an
     * error, so the permission has to be checked up front. Returns an empty list when the
     * feature is off or the permission is missing, which degrades to the pinned apps alone.
     */
    private fun suggestionPackages(exclude: Set<String>): List<String> {
        if (!hasUsageAccess()) return emptyList()
        return SidebarUsageRanker.query(
            context = launcher,
            exclude = exclude,
            limit = SidebarSuggestions.DEFAULT_SUGGESTION_COUNT,
        )
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = launcher.getSystemService(AppOpsManager::class.java) ?: return false
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            launcher.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }
}
