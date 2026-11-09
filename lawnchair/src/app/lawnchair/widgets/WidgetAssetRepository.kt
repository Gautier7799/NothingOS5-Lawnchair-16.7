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

package app.lawnchair.widgets

import android.content.Context
import app.lawnchair.widgets.WidgetCalendarData.Observance
import org.json.JSONObject

/**
 * Loads and caches the JSON assets shipped for the daily-content widgets.
 *
 * The reference app stores one file per month and kind; Lawnchair ships a single compact file per
 * kind, keyed by `month-day`, which removes the padding entries the reference carried (for example
 * a 31st entry in a 30-day month).
 */
object WidgetAssetRepository {

    /** A quote together with its attributed author. */
    data class Quote(val text: String, val author: String)

    private const val OBSERVANCES_FILE = "widgets/observances.json"
    private const val QUOTES_FILE = "widgets/quotes.json"
    private const val FACTS_FILE = "widgets/facts.json"

    @Volatile
    private var observances: List<Observance>? = null

    @Volatile
    private var quotes: Map<String, Quote>? = null

    @Volatile
    private var facts: Map<String, String>? = null

    /** All observances, sorted by month then day. Loaded once and cached. */
    fun observances(context: Context): List<Observance> = observances ?: synchronized(this) {
        observances ?: parseObservances(readAsset(context, OBSERVANCES_FILE)).also { observances = it }
    }

    /** The quote for a `month-day` key, or null when the asset has no entry. */
    fun quote(context: Context, month: Int, day: Int): Quote? = quotes(context)["$month-$day"]

    /** The fact for a `month-day` key, or null when the asset has no entry. */
    fun fact(context: Context, month: Int, day: Int): String? = facts(context)["$month-$day"]

    private fun quotes(context: Context): Map<String, Quote> = quotes ?: synchronized(this) {
        quotes ?: parseQuotes(readAsset(context, QUOTES_FILE)).also { quotes = it }
    }

    private fun facts(context: Context): Map<String, String> = facts ?: synchronized(this) {
        facts ?: parseFacts(readAsset(context, FACTS_FILE)).also { facts = it }
    }

    private fun readAsset(context: Context, name: String): String = context.assets.open(name).bufferedReader().use { it.readText() }

    internal fun parseObservances(json: String): List<Observance> {
        val array = JSONObject(json).getJSONArray("observances")
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val rule = item.optJSONObject("rule")
                val observance = WidgetCalendarData.observance(
                    month = item.getInt("month"),
                    day = if (item.has("day")) item.getInt("day") else null,
                    name = item.getString("name"),
                    ruleKind = rule?.optString("kind"),
                    ruleWeekday = rule?.optString("weekday"),
                ) ?: continue
                add(observance)
            }
        }
    }

    internal fun parseQuotes(json: String): Map<String, Quote> {
        val root = JSONObject(json).getJSONObject("quotes")
        return buildMap {
            root.keys().forEach { key ->
                val item = root.getJSONObject(key)
                put(key, Quote(item.getString("quote"), item.optString("author", "Unknown")))
            }
        }
    }

    internal fun parseFacts(json: String): Map<String, String> {
        val root = JSONObject(json).getJSONObject("facts")
        return buildMap {
            root.keys().forEach { key -> put(key, root.getString(key)) }
        }
    }
}
