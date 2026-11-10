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

import android.view.View
import com.android.launcher3.BubbleTextView
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.popup.PopupContainerWithArrow
import com.android.launcher3.util.ShortcutUtil

/**
 * Long-press handling for an app icon inside a Caddy home card.
 *
 * The card itself is a plain workspace view without an [com.android.launcher3.model.data.ItemInfo]
 * tag, so the launcher's default workspace long-press handler would refuse it. Instead this opens
 * the same native App Info pop-up the launcher uses everywhere else, keeping "App info",
 * "Uninstall", "Personalise" and "Open in store" available on every icon. It is a no-op for
 * non-app entries.
 */
object CaddyCardIconLongClick {

    @JvmField
    val LISTENER = View.OnLongClickListener { view -> onLongClick(view) }

    private fun onLongClick(view: View): Boolean {
        val info = view.tag
        if (info !is AppInfo || !ShortcutUtil.supportsShortcuts(info)) return false
        val icon = view as? BubbleTextView ?: return false
        return PopupContainerWithArrow.showForIcon(icon) != null
    }
}
