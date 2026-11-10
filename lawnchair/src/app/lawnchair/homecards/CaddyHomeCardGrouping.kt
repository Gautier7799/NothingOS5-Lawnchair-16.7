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
 * Pure logic for merging Caddy home cards, shared by the two supported modes.
 *
 * [MODE_STACK] is the iOS-style widget stack: both cards keep their own apps and
 * live in one place, and the user cycles between them. [MODE_COMBINE] folds the
 * cards into a single card whose app list is the union of both.
 *
 * Kept free of Android dependencies so it can be exercised on the JVM. The
 * controller applies the returned list; this object only decides the layout.
 */
object CaddyHomeCardGrouping {

    const val MODE_STACK = "stack"
    const val MODE_COMBINE = "combine"

    /**
     * Merges the card [draggedId] onto the card [targetId].
     *
     * STACK puts both cards in one group at the target's cell and keeps their apps separate.
     * COMBINE removes the dragged card and gives the target the union of both app lists.
     * Returns the original list unchanged when either id is missing or they are the same card.
     */
    fun merge(
        cards: List<CaddyHomeCardInfo>,
        draggedId: Int,
        targetId: Int,
        mode: String,
    ): List<CaddyHomeCardInfo> {
        if (draggedId == targetId) return cards
        val dragged = cards.firstOrNull { it.id == draggedId } ?: return cards
        val target = cards.firstOrNull { it.id == targetId } ?: return cards
        return when (mode) {
            MODE_STACK -> stack(cards, dragged, target)
            MODE_COMBINE -> combine(cards, dragged, target)
            else -> cards
        }
    }

    private fun stack(
        cards: List<CaddyHomeCardInfo>,
        dragged: CaddyHomeCardInfo,
        target: CaddyHomeCardInfo,
    ): List<CaddyHomeCardInfo> {
        val groupId = target.group.ifEmpty { "g${target.id}" }
        val members = cards.filter { it.group == groupId || it.id == target.id || it.id == dragged.id }
        val nextIndex = members.maxOfOrNull { it.groupIndex }?.plus(1) ?: 0
        return cards.map { card ->
            when (card.id) {
                target.id -> card.copy(
                    group = groupId,
                    groupIndex = if (card.group == groupId) card.groupIndex else 0,
                )

                dragged.id -> card.copy(
                    screenId = target.screenId,
                    cellX = target.cellX,
                    cellY = target.cellY,
                    group = groupId,
                    groupIndex = nextIndex,
                )

                else -> card
            }
        }
    }

    private fun combine(
        cards: List<CaddyHomeCardInfo>,
        dragged: CaddyHomeCardInfo,
        target: CaddyHomeCardInfo,
    ): List<CaddyHomeCardInfo> {
        val mergedKeys = (target.appKeys + dragged.appKeys).distinct()
        val mergedTarget = target.copy(appKeys = mergedKeys, group = "", groupIndex = 0)
        return cards.filterNot { it.id == dragged.id }.map { if (it.id == target.id) mergedTarget else it }
    }

    /** Removes the card [id] from its group. A group of one left behind is dissolved. */
    fun removeFromGroup(cards: List<CaddyHomeCardInfo>, id: Int): List<CaddyHomeCardInfo> {
        val card = cards.firstOrNull { it.id == id } ?: return cards
        if (card.group.isEmpty()) return cards
        val remaining = cards.filterNot { it.id == id }
        val stillInGroup = remaining.filter { it.group == card.group }
        if (stillInGroup.size <= 1) {
            return remaining.map { if (it.group == card.group) it.copy(group = "", groupIndex = 0) else it }
        }
        return reindex(remaining, card.group)
    }

    /** Advances the visible member of the stack [groupId] by one, wrapping around. */
    fun cycle(cards: List<CaddyHomeCardInfo>, groupId: String): List<CaddyHomeCardInfo> {
        val members = cards.filter { it.group == groupId }.sortedBy { it.groupIndex }
        if (members.size < 2) return cards
        val rotated = members.drop(1) + members.first()
        val newIndex = rotated.withIndex().associate { (index, card) -> card.id to index }
        return cards.map { card ->
            newIndex[card.id]?.let { card.copy(groupIndex = it) } ?: card
        }
    }

    /** The member of [groupId] currently shown: the one with the lowest group index. */
    fun visible(cards: List<CaddyHomeCardInfo>, groupId: String): CaddyHomeCardInfo? = cards.filter { it.group == groupId }.minByOrNull { it.groupIndex }

    private fun reindex(cards: List<CaddyHomeCardInfo>, groupId: String): List<CaddyHomeCardInfo> {
        val ordered = cards.filter { it.group == groupId }.sortedBy { it.groupIndex }.map { it.id }
        val newIndex = ordered.withIndex().associate { (index, id) -> id to index }
        return cards.map { card ->
            newIndex[card.id]?.let { card.copy(groupIndex = it) } ?: card
        }
    }
}
