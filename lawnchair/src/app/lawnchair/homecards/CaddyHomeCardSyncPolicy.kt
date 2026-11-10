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

/** Pure selection rules for keeping Caddy Home content independent from Drawer categories. */
object CaddyHomeCardSyncPolicy {

    /** Use drawer categories only to seed a new Home; saved Home cards are authoritative later. */
    fun select(
        savedCards: List<CaddyHomeCardInfo>,
        drawerSeed: List<CaddyHomeCardInfo>,
    ): List<CaddyHomeCardInfo> = savedCards.ifEmpty { drawerSeed }

    /** Hidden Home cards stay in storage but are not attached to the Workspace. */
    fun visible(
        cards: List<CaddyHomeCardInfo>,
        hiddenIds: Set<Int>,
    ): List<CaddyHomeCardInfo> = cards.filterNot { it.id in hiddenIds }
}
