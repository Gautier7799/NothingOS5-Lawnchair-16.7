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

/** Entry point that wires a launcher to its Caddy home card controller. */
object CaddyHomeCards {
    fun controller(launcher: Launcher): CaddyHomeCardController = CaddyHomeCardController(
        launcher = launcher,
        repository = CaddyHomeCardRepository(SharedPreferencesCaddyHomeCardStore(launcher)),
    )
}
