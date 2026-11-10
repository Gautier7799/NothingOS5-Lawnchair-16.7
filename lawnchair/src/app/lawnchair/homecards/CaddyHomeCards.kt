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

import android.content.Context
import com.android.launcher3.Launcher

/** Persists the raw card list in a private preference file. */
class SharedPreferencesCaddyHomeCardStore(context: Context) : CaddyHomeCardStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(KEY, null)

    override fun write(value: String) {
        prefs.edit().putString(KEY, value).apply()
    }

    private companion object {
        const val PREFS = "caddy_home_cards"
        const val KEY = "cards"
    }
}

/** Records which cards the user removed from the home screen, so a later sync will not re-add them. */
interface CaddyHomeCardHiddenStore {
    fun load(): Set<Int>
    fun hide(id: Int)
    fun unhide(id: Int)
}

/** Keeps the removed-card ids in the same private preference file as the cards themselves. */
class SharedPreferencesCaddyHomeCardHiddenStore(context: Context) : CaddyHomeCardHiddenStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): Set<Int> = prefs.getStringSet(KEY, emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet()

    override fun hide(id: Int) {
        val next = load() + id
        prefs.edit().putStringSet(KEY, next.map(Int::toString).toSet()).apply()
    }

    override fun unhide(id: Int) {
        val next = load() - id
        prefs.edit().putStringSet(KEY, next.map(Int::toString).toSet()).apply()
    }

    private companion object {
        const val PREFS = "caddy_home_cards"
        const val KEY = "hidden_cards"
    }
}

/** Entry point that wires a launcher to its Caddy home card controller. */
object CaddyHomeCards {
    fun controller(launcher: Launcher): CaddyHomeCardController = CaddyHomeCardController(
        launcher = launcher,
        repository = CaddyHomeCardRepository(SharedPreferencesCaddyHomeCardStore(launcher)),
        hiddenStore = SharedPreferencesCaddyHomeCardHiddenStore(launcher),
    )
}
