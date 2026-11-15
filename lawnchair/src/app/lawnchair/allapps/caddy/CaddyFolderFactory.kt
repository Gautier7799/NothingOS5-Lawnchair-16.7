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

package app.lawnchair.allapps.caddy

/**
 * Android-free model for turning a Caddy category card into a real Lawnchair folder.
 *
 * A Caddy card is a presentation grouping of apps in the drawer. When the user drags
 * the card onto the home screen, the launcher materialises a native folder whose
 * contents are the card's apps. This object holds only the pure, testable part of
 * that translation: which apps to include, in which order, and under what title.
 *
 * The actual folder creation (FolderInfo, FolderIcon, database write) stays on the
 * launcher side, because it needs CellLayout and ModelWriter access.
 */
object CaddyFolderFactory {

    /** Maximum length kept from a category title so the folder label stays readable. */
    const val MAX_TITLE_LENGTH = 40

    /**
     * Immutable, validated description of the folder a card should become.
     *
     * [appKeys] are stable `ComponentKey.toString()` values so the folder can be
     * rebuilt from the database without depending on AppInfo object identity.
     */
    data class FolderSpec(
        val title: String,
        val appKeys: List<String>,
    )

    /**
     * Builds the folder specification for a dropped card.
     *
     * Returns null when the card carries no usable app, so the caller leaves the drop
     * to the ordinary paths instead of creating an empty folder. Blank keys are
     * dropped, duplicates are removed keeping first-seen order, and the title is
     * trimmed and truncated.
     */
    fun buildSpec(title: CharSequence?, appKeys: List<String>?): FolderSpec? {
        if (appKeys == null) return null
        val keys = LinkedHashSet<String>()
        for (raw in appKeys) {
            val key = raw.trim()
            if (key.isNotEmpty()) keys.add(key)
        }
        if (keys.isEmpty()) return null
        return FolderSpec(
            title = normalizeTitle(title),
            appKeys = keys.toList(),
        )
    }

    /**
     * Folder label for a category title. Falls back to the first app label when the
     * category has no usable name, so the folder is never untitled.
     */
    fun normalizeTitle(title: CharSequence?, fallback: CharSequence? = null): String {
        val primary = title?.toString()?.trim().orEmpty()
        if (primary.isNotEmpty()) return primary.take(MAX_TITLE_LENGTH)
        val secondary = fallback?.toString()?.trim().orEmpty()
        return secondary.take(MAX_TITLE_LENGTH)
    }
}
