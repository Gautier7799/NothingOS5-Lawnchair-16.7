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

import androidx.compose.runtime.Immutable

/**
 * A Dossier card placed on the workspace.
 *
 * A Dossier is an independent, resizable card that occupies its own grid area.
 * Its content is the set of apps it groups, identified by the same stable
 * component key the drawer uses, so the launcher app model stays the single
 * source of truth for app data. The grid placement (screen, cell, span) is what
 * the card owns and persists.
 */
@Immutable
data class DossierCardInfo(
    val id: Int,
    val title: String,
    val appKeys: List<String>,
    val screenId: Int = NO_SCREEN,
    val cellX: Int = 0,
    val cellY: Int = 0,
    val spanX: Int = DEFAULT_SPAN,
    val spanY: Int = DEFAULT_SPAN,
) {
    /** Number of apps assigned to the card. */
    val appCount: Int get() = appKeys.size

    /** True once the card has been placed on a concrete workspace screen. */
    val isPlaced: Boolean get() = screenId >= 0

    /** Returns a copy whose spans are clamped to the supported range. */
    fun withClampedSpan(minSpan: Int = MIN_SPAN, maxSpan: Int = MAX_SPAN): DossierCardInfo = copy(
        spanX = spanX.coerceIn(minSpan, maxSpan),
        spanY = spanY.coerceIn(minSpan, maxSpan),
    )

    companion object {
        const val NO_SCREEN = -1
        const val DEFAULT_SPAN = 2
        const val MIN_SPAN = 1
        const val MAX_SPAN = 8
    }
}
