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
 * Stable identity for a drawer Caddy card, used by `DiffUtil` item matching.
 *
 * Every Caddy card shares one `viewType` and one class, so `AdapterItem.isSameAs` — which
 * compared only those two — reported every card as the same item. When items look identical,
 * `DiffUtil` cannot track one across a reorder and falls back to matching by position, so a card
 * that moved (opening a category moves it to the front) handed its content to a neighbouring
 * card's view and the next refresh swapped it back. Cards visibly traded contents.
 *
 * A category is identified by its title: it is what the card shows, it is what the grouping keys
 * on, and it survives reordering. The utility cards are separate kinds, so each gets its own token
 * and can never match a category. These functions take primitives so the matching rules are
 * testable without Android types.
 */
object CaddyCardIdentity {

    const val CATEGORY_PREFIX = "category:"

    /**
     * Identity of a category card. The title is trimmed so whitespace cannot split a card.
     *
     * Two categories with the same trimmed title are indistinguishable to `DiffUtil`, so the
     * editor refuses to create a duplicate (see [duplicateOf]).
     */
    fun category(title: CharSequence?): String = CATEGORY_PREFIX + title?.toString().orEmpty().trim()

    /**
     * Identity of any Caddy card.
     *
     * The utility cards are checked first: the "Add category" card carries a title too, so testing
     * the title first would file it as an ordinary category named "Add category".
     */
    fun of(isSearchActions: Boolean, isAddCategory: Boolean, title: CharSequence?): String = when {
        isSearchActions -> "search-actions"
        isAddCategory -> "add-category"
        else -> category(title)
    }

    /**
     * Returns the existing title that [candidate] collides with, or null when it is free.
     *
     * Category cards are identified by title alone, so two categories sharing a title get the
     * same `DiffUtil` identity and can trade contents on refresh — the same failure mode PR #82
     * fixed for reordering. The editor therefore refuses to save a duplicate. Comparison uses the
     * same normalisation as [category] so a difference in surrounding whitespace cannot slip a
     * duplicate past the check. Previously saved duplicates are tolerated for backwards
     * compatibility: an idle category keeps its card, it only blocks creating another.
     *
     * [existingTitles] are the titles already stored; [candidate] is the raw editor input.
     */
    fun duplicateOf(existingTitles: List<String>, candidate: CharSequence?): String? {
        val wanted = candidate?.toString().orEmpty().trim()
        if (wanted.isEmpty()) return null
        return existingTitles.firstOrNull { it.trim() == wanted }
    }

    /**
     * Identity of an app as the card draws it: component key, which icon bitmap it uses, and label.
     *
     * The component key alone cannot decide that a card is unchanged. Swapping the icon pack keeps
     * every component key but replaces every bitmap, so a card judged unchanged would keep drawing
     * the old icons until the launcher restarted. `IconCache` returns the same `BitmapInfo`
     * instance for an unchanged icon, so its identity separates a real icon change from a merely
     * rebuilt `AppInfo` (which has no `equals`).
     *
     * [bitmapIdentity] must change only when the drawn icon changes — the bitmap's identity hash
     * in production.
     */
    fun appContentKey(componentKey: String, bitmapIdentity: Int, title: CharSequence?): String {
        val label = title?.toString().orEmpty()
        // Length-prefix the key so a '|' inside it cannot shift the field boundaries and let two
        // different apps produce the same key. The label is last, so it needs no escaping.
        return "${componentKey.length}:$componentKey|$bitmapIdentity|$label"
    }
}
