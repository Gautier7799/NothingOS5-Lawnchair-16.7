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

/**
 * Today at a glance: weekday, day of month and month.
 *
 * Rendered with [android.widget.TextClock] views so the widget rolls over at midnight by itself.
 */
class DateWidgetProvider : LawnchairWidgetProvider() {

    override fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_date)
        NativeWidgetPreferences.applyCard(context, views, appWidgetId)
        NativeWidgetPreferences.applyHeader(
            context,
            views,
            appWidgetId,
            R.drawable.ic_widget_calendar,
            context.getString(R.string.widget_date_label),
        )
        views.setTextColor(R.id.widget_date_day, NativeWidgetPreferences.primaryColor(context, appWidgetId))
        NativeWidgetPreferences.applyInset(context, views, appWidgetId)
        NativeWidgetPreferences.applyDetailVisibility(context, views, appWidgetId)
        views.setTextColor(R.id.widget_date_month, NativeWidgetPreferences.variantColor(context, appWidgetId))
        return views
    }
}
