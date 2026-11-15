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

package app.lawnchair.dossier

import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.Launcher
import com.android.launcher3.allapps.AllAppsStore

/**
 * Keeps the workspace Dossier cards in step with the launcher's lifecycle.
 *
 * Cards are created by the user's own gesture (dropping one app onto another) and
 * then owned by the launcher model, so this coordinator no longer seeds any cards.
 * It only re-renders the already-bound cards when the app list settles or when the
 * Dossier Grille settings change.
 */
class DossierCardCoordinator(private val launcher: Launcher) {

    private val enabled by lazy {
        PreferenceManager2.getInstance(launcher).dossierEnabled
    }
    private val appsListener = AllAppsStore.OnUpdateListener { syncFromApps() }

    private var listening = false
    private var workspaceReady = false

    /** Called once the launcher's views are ready. */
    fun start() {
        val store = launcher.appsView?.appsStore ?: return
        if (!listening) {
            store.addUpdateListener(appsListener)
            listening = true
        }
    }

    /** Called after the workspace has (re)bound its pages. */
    fun onWorkspaceBound() {
        // onCreate() can run before AppsView has been inflated. Retry listener attachment
        // after the workspace has finished binding so the first complete AllAppsStore update
        // cannot be missed during a cold launcher start.
        start()
        workspaceReady = true
        syncFromApps()
    }

    fun stop() {
        workspaceReady = false
        if (listening) {
            launcher.appsView?.appsStore?.removeUpdateListener(appsListener)
            listening = false
        }
    }

    private fun syncFromApps() {
        if (!workspaceReady) return
        if (!enabled.firstCached()) return
        refreshBoundCards()
    }

    /**
     * Existing Dossier rows are model-owned and are not re-seeded by controller.sync().
     * Refresh their resolved icons explicitly after AllAppsStore publishes a stable list.
     */
    private var refreshPosted = false

    private fun refreshBoundCards() {
        if (refreshPosted) return
        refreshPosted = true
        postToBoundCards {
            refreshPosted = false
            it.refreshApps()
        }
    }

    /**
     * Re-renders every bound card with the current Dossier Grille settings.
     *
     * The icon grid is an in-card layout, independent of the workspace cell span, so a
     * settings change never needs the full workspace rebind that reloadGrid() performs.
     * Called after the preference cache has been refreshed with the new value.
     */
    fun refreshCards() {
        postToBoundCards { it.refreshGrid() }
    }

    /** Runs [action] on every bound Dossier card on the next animation frame. */
    private fun postToBoundCards(action: (DossierCardView) -> Unit) {
        val workspace = launcher.workspace ?: return
        workspace.postOnAnimation {
            if (!workspaceReady) return@postOnAnimation
            val order = workspace.screenOrder
            for (index in 0 until order.size()) {
                val cellLayout = workspace.getScreenWithId(order.get(index)) ?: continue
                val children = cellLayout.shortcutsAndWidgets
                for (childIndex in 0 until children.childCount) {
                    (children.getChildAt(childIndex) as? DossierCardView)?.let(action)
                }
            }
        }
    }
}
