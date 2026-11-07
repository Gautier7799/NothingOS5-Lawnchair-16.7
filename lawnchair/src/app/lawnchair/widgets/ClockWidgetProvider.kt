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
 * The layout uses [android.widget.TextClock], so the platform keeps the time up to date without
 * the provider being woken on every tick.
 */
class ClockWidgetProvider : LawnchairWidgetProvider() {

    override fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_clock)
        NativeWidgetPreferences.applyCard(context, views, appWidgetId)
        val showSeconds = NativeWidgetPreferences.getShowSeconds(context, appWidgetId)
        views.setCharSequence(
            R.id.widget_clock_time,
            "setFormat24Hour",
            if (showSeconds) "HH:mm:ss" else "HH:mm",
        )
        views.setCharSequence(
            R.id.widget_clock_time,
            "setFormat12Hour",
            if (showSeconds) "h:mm:ss" else "h:mm",
        )
        return views
    }
}
