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

/** Read/write access to the raw persisted card list. */
interface CaddyHomeCardStore {
    fun read(): String?
    fun write(value: String)
}

/**
 * Owns the persisted set of Caddy home cards.
 *
 * The store only holds the extra card information (title, assigned component
 * keys, grid placement). App data itself is never duplicated here: the launcher
 * app model resolves each key back to a live [com.android.launcher3.model.data.AppInfo].
 */
class CaddyHomeCardRepository(private val store: CaddyHomeCardStore) {

    fun load(): List<CaddyHomeCardInfo> = CaddyHomeCardCodec.decode(store.read())

    fun save(cards: List<CaddyHomeCardInfo>) {
        store.write(CaddyHomeCardCodec.encode(cards))
    }

    fun add(card: CaddyHomeCardInfo): List<CaddyHomeCardInfo> {
        val cards = load().filterNot { it.id == card.id } + card
        save(cards)
        return cards
    }

    fun remove(id: Int): List<CaddyHomeCardInfo> {
        val cards = load().filterNot { it.id == id }
        save(cards)
        return cards
    }

    fun update(card: CaddyHomeCardInfo): List<CaddyHomeCardInfo> {
        val cards = load().map { if (it.id == card.id) card else it }
        save(cards)
        return cards
    }

    fun nextId(): Int = (load().maxOfOrNull { it.id } ?: 0) + 1
}
