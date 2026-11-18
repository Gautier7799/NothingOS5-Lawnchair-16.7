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

/**
 * Pure matching rule for the App Library search field, kept free of Android types so it can be
 * unit-tested on the JVM.
 *
 * A category card matches when the query is empty, or when it appears in the card title, or in the
 * title of any app the card contains. Matching is case-insensitive and trims the query, so a stray
 * space does not hide every card.
 */
object CaddyAppLibraryFilter {

    fun matches(query: String, title: String, appTitles: List<String>): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        if (title.contains(needle, ignoreCase = true)) return true
        return appTitles.any { it.contains(needle, ignoreCase = true) }
    }
}
