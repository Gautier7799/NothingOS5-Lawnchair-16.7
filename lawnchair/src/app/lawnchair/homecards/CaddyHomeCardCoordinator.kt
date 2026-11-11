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

import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.Launcher
import com.android.launcher3.allapps.AllAppsStore

/**
 * Keeps the workspace Caddy cards in step with the launcher's lifecycle.
 *
 * The card content depends on the loaded app list, and the card views live
 * inside workspace pages that are recreated whenever the workspace rebinds.
 * This coordinator listens to the app store and to workspace binds, and it
 * rebuilds the cards on both events.
 */
class CaddyHomeCardCoordinator(private val launcher: Launcher) {

    private val controller by lazy { CaddyHomeCards.controller(launcher) }

    private val appsListener = AllAppsStore.OnUpdateListener { syncFromApps() }

    private var listening = false

    /** Called once the launcher's views are ready. */
    fun start() {
        registerAppsListener()
        syncFromApps()
    }

    /** Called after the workspace has (re)bound its pages. */
    fun onWorkspaceBound() {
        // AppsView may not exist yet during Activity.onCreate(). Retry listener
        // registration only after the workspace has finished binding.
        registerAppsListener()
        // Rebound pages are new views, so drop the stale references and rebuild.
        controller.detachAll()
        syncFromApps()
    }

    private fun registerAppsListener() {
        val store = launcher.appsView?.appsStore ?: return
        if (!listening) {
            store.addUpdateListener(appsListener)
            listening = true
        }
    }

    fun stop() {
        if (listening) {
            launcher.appsView?.appsStore?.removeUpdateListener(appsListener)
            listening = false
        }
        controller.detachAll()
    }

    private fun syncFromApps() {
        val prefs = PreferenceManager2.getInstance(launcher)
        if (!prefs.caddyHomeEnabled.firstCached()) {
            controller.detachAll()
            return
        }
        controller.mergeMode = prefs.caddyMergeMode.firstCached()
        controller.showTitles = prefs.caddyHomeShowTitles.firstCached()
        controller.columns = prefs.caddyHomeColumns.firstCached().coerceIn(1, 5)
        // onCreate() can run before Workspace has created/bound its pages. Never
        // sync or persist an empty placement result at that point: doing so would
        // overwrite the user's saved card coordinates with an empty list.
        val workspace = launcher.workspace ?: return
        if (workspace.screenOrder.size() == 0) return

        val apps = launcher.appsView?.appsStore?.apps?.toList() ?: return
        if (apps.isEmpty()) return
        controller.sync(apps)
    }
}
