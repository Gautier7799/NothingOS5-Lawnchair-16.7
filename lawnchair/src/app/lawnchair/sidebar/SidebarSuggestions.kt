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
 * Merges the user's pinned favourites with usage-ranked suggestions.
 *
 * The sidebar is a shortlist, so it shows the pinned apps first, in the order the user chose,
 * and then fills the remaining slots with the apps they actually reach for. Pinned apps are
 * never duplicated by a suggestion: a favourite that is also heavily used must not appear twice.
 *
 * Kept free of Android types so the ordering rules can be unit tested on the JVM.
 */
object SidebarSuggestions {

    /** How many usage-ranked suggestions are appended after the pinned apps. */
    const val DEFAULT_SUGGESTION_COUNT = 4

    /**
     * Builds the visible list.
     *
     * @param pinned component keys chosen by the user, in display order.
     * @param ranked component keys ordered by usage, most used first.
     * @param suggestionCount how many suggestions to append; zero shows only the pinned apps.
     * @param enabled whether suggestions are wanted at all.
     */
    fun merge(
        pinned: List<String>,
        ranked: List<String>,
        suggestionCount: Int = DEFAULT_SUGGESTION_COUNT,
        enabled: Boolean = true,
    ): List<String> {
        val result = ArrayList<String>(pinned.size + suggestionCount)
        val seen = HashSet<String>(pinned.size + suggestionCount)
        pinned.forEach { key ->
            if (key.isNotEmpty() && seen.add(key)) result.add(key)
        }
        if (!enabled || suggestionCount <= 0) return result
        var added = 0
        for (key in ranked) {
            if (added >= suggestionCount) break
            if (key.isEmpty() || !seen.add(key)) continue
            result.add(key)
            added++
        }
        return result
    }

    /**
     * Splits the merged list back into the pinned head and the suggested tail.
     *
     * The view needs the boundary to draw the "Suggestions" divider in the right place, and it
     * must be derived rather than stored so it cannot drift from the merged order.
     */
    fun pinnedCount(pinned: List<String>, merged: List<String>): Int {
        val pinnedSet = pinned.filter { it.isNotEmpty() }.toSet()
        var count = 0
        for (key in merged) {
            if (key !in pinnedSet) break
            count++
        }
        return count
    }
}
