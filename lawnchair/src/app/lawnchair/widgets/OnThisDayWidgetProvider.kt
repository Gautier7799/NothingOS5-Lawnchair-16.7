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
import android.widget.RemoteViews
import com.android.launcher3.R
import java.time.LocalDate

/**
 * "On this day" — the observance registered for today, or the next one when today has none.
 *
 * The data lives in `assets/widgets/observances.json` and is matched by
 * [WidgetCalendarData.Observance], which also resolves the variable-date rules (for example the
 * last Saturday of March) that the reference data set pinned to a single, often wrong, day.
 */
class OnThisDayWidgetProvider : LawnchairWidgetProvider() {

    override fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_on_this_day)
        NativeWidgetPreferences.applyCard(context, views, appWidgetId)
        NativeWidgetPreferences.applyHeader(
            context,
            views,
            appWidgetId,
            R.drawable.ic_widget_calendar,
            context.getString(R.string.widget_on_this_day_label),
        )
        NativeWidgetPreferences.applyInset(context, views, appWidgetId)
        NativeWidgetPreferences.applyDetailVisibility(context, views, appWidgetId)

        val today = LocalDate.now()
        val observances = WidgetAssetRepository.observances(context)
        val match = WidgetCalendarData.observanceFor(observances, today)

        views.setTextViewText(R.id.widget_on_this_day_date, WidgetFormat.formatShortDate(today.dayOfMonth, today.monthValue, today.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())))

        if (match != null) {
            views.setTextViewText(R.id.widget_value, match.name)
            views.setTextColor(R.id.widget_value, NativeWidgetPreferences.primaryColor(context, appWidgetId))
            views.setTextViewText(R.id.widget_inset_detail, context.getString(R.string.widget_on_this_day_today))
            return views
        }

        val upcoming = WidgetCalendarData.nextObservance(observances, today)
        if (upcoming == null) {
            views.setTextViewText(R.id.widget_value, context.getString(R.string.widget_on_this_day_empty))
            views.setTextColor(R.id.widget_value, NativeWidgetPreferences.primaryColor(context, appWidgetId))
            views.setTextViewText(R.id.widget_inset_detail, "")
            return views
        }

        views.setTextViewText(R.id.widget_value, upcoming.observance.name)
        views.setTextColor(R.id.widget_value, NativeWidgetPreferences.variantColor(context, appWidgetId))
        views.setTextViewText(R.id.widget_inset_detail, WidgetFormat.formatObservanceWhen(upcoming.daysUntil))
        return views
    }
}
