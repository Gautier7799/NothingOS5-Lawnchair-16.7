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

import android.content.Context

/** Read/write access to the raw persisted card list. */
interface DossierCardStore {
    fun read(): String?
    fun write(value: String)
}

/** Persists the raw card list in a private preference file. */
class SharedPreferencesDossierCardStore(context: Context) : DossierCardStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(KEY, null)

    override fun write(value: String) {
        prefs.edit().putString(KEY, value).apply()
    }

    private companion object {
        const val PREFS = "dossier_workspace_cards"
        const val KEY = "cards"
    }
}

/**
 * Owns the persisted set of Dossier cards.
 *
 * The store holds only the extra card information (title, assigned component
 * keys, grid placement). App data itself is never duplicated here: the launcher
 * app model resolves each key back to a live [com.android.launcher3.model.data.AppInfo].
 */
class DossierCardRepository(private val store: DossierCardStore) {

    fun load(): List<DossierCardInfo> = DossierCardCodec.decode(store.read())

    fun save(cards: List<DossierCardInfo>) {
        store.write(DossierCardCodec.encode(cards))
    }

    fun add(card: DossierCardInfo): List<DossierCardInfo> {
        val cards = load().filterNot { it.id == card.id } + card
        save(cards)
        return cards
    }

    fun remove(id: Int): List<DossierCardInfo> {
        val cards = load().filterNot { it.id == id }
        save(cards)
        return cards
    }

    fun update(card: DossierCardInfo): List<DossierCardInfo> {
        val cards = load().map { if (it.id == card.id) card else it }
        save(cards)
        return cards
    }

    fun nextId(): Int = (load().maxOfOrNull { it.id } ?: 0) + 1
}
