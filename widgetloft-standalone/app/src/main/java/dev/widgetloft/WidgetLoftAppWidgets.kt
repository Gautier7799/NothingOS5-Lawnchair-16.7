package dev.widgetloft

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle

/**
 * Hosts real Android widgets inside WidgetLoft cards.
 *
 * A launcher-style host needs three things: a stable [AppWidgetHost] id, the system "bind" grant
 * (`ACTION_APPWIDGET_BIND`), and a host view created from the granted id. [pickWidget] drives the
 * system picker; [bindWidget] performs the bind grant; [createView] materialises the widget. The
 * activity owns the request-code round trip and calls [startListening]/[stopListening] with its
 * own lifecycle so widget updates only arrive while the board is visible.
 */
class WidgetLoftAppWidgets(private val context: Context) {

    private val host = AppWidgetHost(context, HOST_ID)
    private val manager = AppWidgetManager.getInstance(context)

    fun startListening() = host.startListening()

    fun stopListening() = host.stopListening()

    /** Opens the system widget picker for [appWidgetId] (from [allocateId]). */
    fun pickWidget(activity: Activity, appWidgetId: Int) {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_CUSTOM_EXTRAS, ArrayList<Bundle>())
        }
        activity.startActivityForResult(intent, REQUEST_PICK)
    }

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(appWidgetId: Int) {
        runCatching { host.deleteAppWidgetId(appWidgetId) }
    }

    fun infoFor(appWidgetId: Int): AppWidgetProviderInfo? =
        runCatching { manager.getAppWidgetInfo(appWidgetId) }.getOrNull()

    /**
     * Grants this host permission to bind [appWidgetId] to [provider]. On most devices this shows a
     * confirmation dialog; the result arrives in `onActivityResult` with [REQUEST_BIND].
     */
    fun bindWidget(activity: Activity, appWidgetId: Int, provider: ComponentName) {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
        }
        activity.startActivityForResult(intent, REQUEST_BIND)
    }

    fun isBound(appWidgetId: Int): Boolean =
        runCatching { manager.getAppWidgetInfo(appWidgetId) != null }.getOrDefault(false)

    /** Creates the host view for a bound widget, or null when the widget cannot be materialised. */
    fun createView(appWidgetId: Int): AppWidgetHostView? =
        runCatching { host.createView(context, appWidgetId, infoFor(appWidgetId)) }.getOrNull()

    /** Extracts the chosen provider from the system picker's result, or null when none was chosen. */
    fun providerFromPickResult(data: Intent?): ComponentName? {
        if (data == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data.getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, ComponentName::class.java)
        } else {
            @Suppress("DEPRECATION")
            data.getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER)
        }
    }

    companion object {
        const val HOST_ID = 0x57_4C_46_54 // "WLFT"
        const val REQUEST_PICK = 4101
        const val REQUEST_BIND = 4102
    }
}
