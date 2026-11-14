package app.lawnchair.allapps.caddy

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Single source of truth for the persisted Caddy custom-category format.
 *
 * Stored in the `caddy_custom_categories` SharedPreferences file under the `categories` key as a
 * JSON array of `{ "title": ..., "apps": [componentKey, ...] }`. Both the drawer's create flow
 * and its loader go through this codec so a card built one way is always read back the same way.
 *
 * Uses `kotlinx.serialization`'s JSON tree API rather than `org.json`: the tree API is a real,
 * pure-JVM implementation, so the format can be exercised by unit tests instead of relying on the
 * stubbed `org.json` that ships in the Android unit-test runtime.
 */
object CaddyCategoryStore {

    const val PREFS_NAME = "caddy_custom_categories"
    const val KEY_CATEGORIES = "categories"

    private val json = Json { ignoreUnknownKeys = true }

    /** Serializes [cards] to the persisted JSON array. */
    fun encode(cards: List<CaddyCardSpec>): String {
        val array = buildJsonArray {
            for (card in cards) {
                add(
                    buildJsonObject {
                        put("title", JsonPrimitive(card.title))
                        put(
                            "apps",
                            buildJsonArray {
                                card.appKeys.forEach { add(JsonPrimitive(it)) }
                            },
                        )
                    },
                )
            }
        }
        return array.toString()
    }

    /**
     * Parses the persisted JSON. Malformed entries are skipped rather than throwing, so one bad
     * card cannot break the whole drawer.
     *
     * This runs on the main thread every time the drawer opens, so it must never throw: every
     * element access uses a safe cast instead of `jsonPrimitive` (which throws on non-primitives).
     */
    fun decode(text: String?): List<CaddyCardSpec> {
        if (text.isNullOrBlank()) return emptyList()
        val array = runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull()
            ?: return emptyList()
        val result = ArrayList<CaddyCardSpec>(array.size)
        for (element in array) {
            val card = element as? JsonObject ?: continue
            val title = (card["title"] as? JsonPrimitive)?.contentOrNull
            val apps = (card["apps"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?: continue
            CaddyCardAssembly.assemble(title, apps)?.let(result::add)
        }
        return result
    }
}
