package dev.widgetloft

import android.content.Context

/**
 * Persists the board to [Context.getSharedPreferences] as a single encoded string.
 *
 * The board is small and rewritten on every change, so one string value keeps the store trivial and
 * avoids a database dependency. [WidgetLoftCodec] owns the actual format.
 */
class WidgetLoftStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): List<WidgetLoftCard> = WidgetLoftCodec.decode(prefs.getString(KEY_BOARD, null))

    fun save(cards: List<WidgetLoftCard>) {
        prefs.edit().putString(KEY_BOARD, WidgetLoftCodec.encode(cards)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_BOARD).apply()
    }

    private companion object {
        const val FILE = "widgetloft_board"
        const val KEY_BOARD = "board"
    }
}
