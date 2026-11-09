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

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.android.launcher3.R
import java.time.LocalDate

/**
 * A quote or fact of the day, with a tap-to-advance gesture.
 *
 * Both kinds share this provider because they are the same interaction over different data. The
 * concrete [QuoteWidgetProvider] and [FactWidgetProvider] subclasses pin the kind so the widget
 * picker can list them separately, while the kind also remains overridable per widget in the config
 * screen via [NativeWidgetPreferences]. Tapping the card advances to the following day's entry.
 */
abstract class DailyContentWidgetProvider : LawnchairWidgetProvider() {

    /** Which data set the widget shows. */
    enum class Kind { QUOTE, FACT }

    /** The kind this provider shows by default, before any per-widget override. */
    protected abstract val defaultKind: Kind

    override fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_daily_content)
        NativeWidgetPreferences.applyCard(context, views, appWidgetId)
        NativeWidgetPreferences.applyInset(context, views, appWidgetId)
        NativeWidgetPreferences.applyDetailVisibility(context, views, appWidgetId)

        val kind = NativeWidgetPreferences.getDailyKind(context, appWidgetId, defaultKind)
        NativeWidgetPreferences.applyHeader(
            context,
            views,
            appWidgetId,
            if (kind == Kind.QUOTE) R.drawable.ic_widget_quote else R.drawable.ic_widget_fact,
            context.getString(if (kind == Kind.QUOTE) R.string.widget_quote_label else R.string.widget_fact_label),
        )

        val date = LocalDate.now().plusDays(NativeWidgetPreferences.getDailyOffset(context, appWidgetId).toLong())
        val month = date.monthValue
        val day = date.dayOfMonth
        val text: String
        val attribution: String?
        when (kind) {
            Kind.QUOTE -> {
                val quote = WidgetAssetRepository.quote(context, month, day)
                text = quote?.text ?: context.getString(R.string.widget_daily_content_empty)
                attribution = quote?.author
            }

            Kind.FACT -> {
                text = WidgetAssetRepository.fact(context, month, day) ?: context.getString(R.string.widget_daily_content_empty)
                attribution = null
            }
        }

        views.setTextViewText(R.id.widget_value, text)
        views.setTextColor(R.id.widget_value, NativeWidgetPreferences.primaryColor(context, appWidgetId))
        views.setTextViewText(
            R.id.widget_inset_detail,
            attribution ?: context.getString(R.string.widget_daily_content_day_of, WidgetFormat.formatShortDate(day, month)),
        )
        views.setViewVisibility(R.id.widget_daily_content_date, View.VISIBLE)
        views.setTextViewText(R.id.widget_daily_content_date, WidgetFormat.formatShortDate(day, month))
        views.setOnClickPendingIntent(R.id.widget_root, advance(context, appWidgetId))
        return views
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_NEXT) return
        val appWidgetId = intent.getIntExtra(EXTRA_APP_WIDGET_ID, -1)
        if (appWidgetId < 0) return
        NativeWidgetPreferences.setDailyOffset(context, appWidgetId, NativeWidgetPreferences.getDailyOffset(context, appWidgetId) + 1)
        val manager = AppWidgetManager.getInstance(context) ?: return
        runCatching { manager.updateAppWidget(appWidgetId, buildRemoteViews(context, appWidgetId)) }
    }

    private fun advance(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, javaClass).setAction(ACTION_NEXT).putExtra(EXTRA_APP_WIDGET_ID, appWidgetId)
        return PendingIntent.getBroadcast(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_NEXT = "app.lawnchair.widgets.action.DAILY_NEXT"
        const val EXTRA_APP_WIDGET_ID = "app_widget_id"
    }
}

/** Quote-of-the-day widget. */
class QuoteWidgetProvider : DailyContentWidgetProvider() {
    override val defaultKind = Kind.QUOTE
}

/** Fact-of-the-day widget. */
class FactWidgetProvider : DailyContentWidgetProvider() {
    override val defaultKind = Kind.FACT
}
