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

import androidx.compose.runtime.Immutable

/**
 * Presentation model for a Caddy card placed on the workspace.
 *
 * The card mirrors the Caddy Smart Drawer category card: a title and the
 * component keys of the apps it groups. App identity is stored as the same
 * stable component key the drawer uses ([com.android.launcher3.util.ComponentKey]),
 * so the launcher app model remains the single source of truth for app data.
 */
@Immutable
data class CaddyHomeCardInfo(
    val id: Int,
    val title: String,
    val appKeys: List<String>,
    val screenId: Int = -1,
    val cellX: Int = 0,
    val cellY: Int = 0,
    val spanX: Int = 2,
    val spanY: Int = 2,
) {
    /** Number of apps assigned to the card. */
    val appCount: Int get() = appKeys.size
}
