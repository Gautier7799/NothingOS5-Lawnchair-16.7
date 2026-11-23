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

/**
 * Filtering for the sidebar's search field.
 *
 * The standalone Samart sidebar grew a searchable All Apps drawer; this is the part of it that
 * makes sense inside the sidebar, where the app list is already the one the user chose. Ported
 * from that app (MIT), but the comparison is stricter: it matches on a normalised string so
 * the query finds "Éditeur" from "editeur" and ignores the spaces and punctuation users type
 * inconsistently.
 *
 * Kept free of Android types so the matching rules can be unit tested.
 */
object SidebarSearch {

    /**
     * Normalises a label or query for comparison.
     *
     * Diacritics are folded to their base letter, everything is lowercased, and punctuation and
     * whitespace are dropped. Dropping whitespace means "you tube" still finds "YouTube".
     */
    fun normalise(input: String): String = buildString(input.length) {
        for (ch in input) {
            val folded = when (ch) {
                'à', 'á', 'â', 'ã', 'ä', 'å', 'À', 'Á', 'Â', 'Ã', 'Ä', 'Å' -> 'a'
                'è', 'é', 'ê', 'ë', 'È', 'É', 'Ê', 'Ë' -> 'e'
                'ì', 'í', 'î', 'ï', 'Ì', 'Í', 'Î', 'Ï' -> 'i'
                'ò', 'ó', 'ô', 'õ', 'ö', 'Ò', 'Ó', 'Ô', 'Õ', 'Ö' -> 'o'
                'ù', 'ú', 'û', 'ü', 'Ù', 'Ú', 'Û', 'Ü' -> 'u'
                'ç', 'Ç' -> 'c'
                'ñ', 'Ñ' -> 'n'
                else -> ch
            }
            when {
                folded.isLetterOrDigit() -> append(folded.lowercaseChar())
                else -> Unit
            }
        }
    }

    /**
     * Whether [label] should be shown for [query].
     *
     * An empty query keeps everything, which is what makes this usable as a live filter rather
     * than a one-shot search. A query that normalises to nothing (only spaces, say) also keeps
     * everything, so typing a stray space does not empty the panel.
     */
    fun matches(label: String, query: String): Boolean {
        val needle = normalise(query)
        if (needle.isEmpty()) return true
        return normalise(label).contains(needle)
    }

    /**
     * Filters and ranks [items] for [query], best match first.
     *
     * Ranking is prefix first, then substring, then everything that merely matched. Within a
     * rank the input order is kept, so the user's own ordering of sidebar apps is respected
     * instead of being reshuffled by the search.
     *
     * [label] extracts the text to match, which keeps this independent of `AppInfo`.
     */
    fun <T> rank(items: List<T>, query: String, label: (T) -> String): List<T> {
        val needle = normalise(query)
        if (needle.isEmpty()) return items
        val prefix = mutableListOf<T>()
        val substring = mutableListOf<T>()
        for (item in items) {
            val haystack = normalise(label(item))
            when {
                haystack.startsWith(needle) -> prefix += item
                haystack.contains(needle) -> substring += item
            }
        }
        return prefix + substring
    }
}
