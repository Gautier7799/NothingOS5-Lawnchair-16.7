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

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Base class for the native Lawnchair widget library.
 *
 * Each widget is a plain [AppWidgetProvider] that renders a [RemoteViews] layout. Text and
 * progress views are backed by Lawnchair's `system_*` color resources, which
 * [app.lawnchair.AccentColorExtractor] rewrites from the wallpaper, so the widgets follow the
 * user's dynamic color without any per-widget theming code.
 */
abstract class LawnchairWidgetProvider : AppWidgetProvider() {

    final override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            val views = runCatching { buildRemoteViews(context, appWidgetId) }.getOrNull() ?: return@forEach
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    final override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { NativeWidgetPreferences.delete(context, it) }
    }

    final override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            refreshAll(context)
        }
    }

    /** Renders the widget for [appWidgetId]. Must never return null. */
    abstract fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews

    /**
     * Rebuilds every placed instance of this widget. Used for content that the platform does not
     * refresh on its own (for example a reconfiguration or an explicit refresh broadcast).
     */
    protected fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(android.content.ComponentName(context, javaClass))
        if (ids.isEmpty()) return
        onUpdate(context, manager, ids)
    }

    companion object {
        /** Explicit refresh request handled by [onReceive]. */
        const val ACTION_REFRESH = "app.lawnchair.widgets.action.REFRESH"
    }
}
