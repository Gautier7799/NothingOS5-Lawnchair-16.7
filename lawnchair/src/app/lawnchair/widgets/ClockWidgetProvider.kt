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
 * Live clock with the current date.
 *
 * Renders with [android.widget.TextClock] so the platform keeps the time current without waking
 * the provider on every tick. Two designs are available: the reference "hero" face (oversized hour
 * digits with smaller minutes) and a plain single-line time; a wide instance switches to a
 * single-row layout.
 */
class ClockWidgetProvider : LawnchairWidgetProvider() {

    override fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val hero = NativeWidgetPreferences.getHeroClock(context, appWidgetId)
        val wide = widgetWidth(context, appWidgetId) >= WIDE_WIDTH_DP
        val layout = when {
            hero -> R.layout.widget_clock_hero
            wide -> R.layout.widget_clock_wide
            else -> R.layout.widget_clock
        }
        val views = RemoteViews(context.packageName, layout)

        NativeWidgetPreferences.applyCard(context, views, appWidgetId)
        NativeWidgetPreferences.applyHeader(
            context,
            views,
            appWidgetId,
            R.drawable.ic_widget_clock,
            context.getString(R.string.widget_clock_label),
        )

        val showSeconds = NativeWidgetPreferences.getShowSeconds(context, appWidgetId)
        val primary = NativeWidgetPreferences.primaryColor(context, appWidgetId)
        if (hero) {
            // TextClock formats itself, so the seconds toggle maps to the minute field's format.
            views.setCharSequence(R.id.widget_clock_minute, "setFormat12Hour", if (showSeconds) "mm:ss" else "mm")
            views.setCharSequence(R.id.widget_clock_minute, "setFormat24Hour", if (showSeconds) "mm:ss" else "mm")
            views.setTextColor(R.id.widget_clock_hour, primary)
            views.setTextColor(R.id.widget_clock_minute, primary)
            views.setTextColor(R.id.widget_clock_period, NativeWidgetPreferences.variantColor(context, appWidgetId))
        } else {
            views.setCharSequence(R.id.widget_clock_time, "setFormat24Hour", if (showSeconds) "HH:mm:ss" else "HH:mm")
            views.setCharSequence(R.id.widget_clock_time, "setFormat12Hour", if (showSeconds) "h:mm:ss" else "h:mm")
            views.setTextColor(R.id.widget_clock_time, primary)
        }
        NativeWidgetPreferences.applyInset(context, views, appWidgetId)
        NativeWidgetPreferences.applyDetailVisibility(context, views, appWidgetId)
        return views
    }
}
