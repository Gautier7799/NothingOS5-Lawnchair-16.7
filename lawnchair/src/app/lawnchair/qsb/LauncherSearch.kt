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

import androidx.lifecycle.lifecycleScope
import app.lawnchair.animateToAllApps
import app.lawnchair.launcher
import com.android.launcher3.Launcher
import kotlinx.coroutines.launch

/**
 * Opens the launcher's own search experience.
 *
 * `Activity.startSearch()` — which the QSB default view and the floating search pill used to call —
 * is a no-op on modern Android: the platform search dialog was removed, so the tap did nothing.
 * This instead moves to All Apps and focuses its search field, which is the launcher's real search
 * UI. If the edit text is not available yet the launcher still animates to All Apps, so the tap
 * always produces a visible result.
 */
object LauncherSearch {

    fun open(launcher: Launcher) {
        launcher.lifecycleScope.launch {
            val searchUiManager = runCatching { launcher.appsView.searchUiManager }.getOrNull()
            searchUiManager?.setDirectFocus(true)
            searchUiManager?.editText?.showKeyboard()
            launcher.animateToAllApps()
        }
    }
}
