package dev.widgetloft

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * WidgetLoft's single screen: an independent card board.
 *
 * The activity owns the process-level pieces — persisted [WidgetLoftStore], [WidgetLoftTheme] and
 * the [WidgetLoftAppWidgets] host — and forwards the widget pick/bind results back to the board.
 * The board owns all card interaction and state.
 */
class MainActivity : Activity() {

    private lateinit var store: WidgetLoftStore
    private lateinit var theme: WidgetLoftTheme
    private lateinit var widgets: WidgetLoftAppWidgets
    private lateinit var board: WidgetLoftBoardView

    /** Card that should receive the widget currently being picked, or null when unset. */
    private var pendingWidgetCardId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = WidgetLoftStore(this)
        theme = WidgetLoftTheme(this)
        widgets = WidgetLoftAppWidgets(this)
        board = WidgetLoftBoardView(
            activity = this,
            theme = theme,
            store = store,
            widgets = widgets,
            onRequestWidget = ::beginWidgetFlow,
        )
        setContentView(board)
    }

    override fun onStart() {
        super.onStart()
        widgets.startListening()
    }

    override fun onStop() {
        widgets.stopListening()
        super.onStop()
    }

    /** Starts the system widget pick for [cardId]; the card is remembered until the bind completes. */
    private fun beginWidgetFlow(cardId: String) {
        pendingWidgetCardId = cardId
        val appWidgetId = widgets.allocateId()
        board.rememberAllocatedWidget(appWidgetId)
        widgets.pickWidget(this, appWidgetId)
    }

    @Deprecated("WidgetLoft's host still relies on the classic result callback for bind grants.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            WidgetLoftAppWidgets.REQUEST_PICK -> {
                val appWidgetId = board.allocatedWidgetId()
                if (resultCode != RESULT_OK || appWidgetId == null) {
                    appWidgetId?.let(widgets::deleteId)
                    board.clearAllocatedWidget()
                    return
                }
                val provider = widgets.providerFromPickResult(data)
                if (provider == null) {
                    widgets.deleteId(appWidgetId)
                    board.clearAllocatedWidget()
                    return
                }
                widgets.bindWidget(this, appWidgetId, provider)
            }

            WidgetLoftAppWidgets.REQUEST_BIND -> {
                val appWidgetId = board.allocatedWidgetId()
                val cardId = pendingWidgetCardId
                if (resultCode == RESULT_OK && appWidgetId != null && cardId != null &&
                    widgets.isBound(appWidgetId)
                ) {
                    board.attachHostedWidget(cardId, appWidgetId)
                } else {
                    appWidgetId?.let(widgets::deleteId)
                }
                board.clearAllocatedWidget()
                pendingWidgetCardId = null
            }
        }
    }
}
