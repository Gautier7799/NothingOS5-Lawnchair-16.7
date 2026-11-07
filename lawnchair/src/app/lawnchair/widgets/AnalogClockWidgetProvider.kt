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

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.android.launcher3.R
import java.util.Calendar

/**
 * Analog clock face with an original vector dial and rotating hands.
 *
 * The platform clamps `updatePeriodMillis` to at least 30 minutes, which is far too coarse for a
 * minute hand, so while at least one instance is placed the provider schedules a one-minute alarm
 * on itself and redraws from the wall clock. The hands are [RemoteViews] rotations, so no custom
 * view or background service is needed.
 */
class AnalogClockWidgetProvider : LawnchairWidgetProvider() {

    override fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_clock_analog)
        NativeWidgetPreferences.applyCard(context, views, appWidgetId)

        val now = Calendar.getInstance()
        val minute = now.get(Calendar.MINUTE)
        val hour = now.get(Calendar.HOUR)
        views.setFloat(R.id.widget_analog_hour, "setRotation", WidgetFormat.hourHandDegrees(hour, minute))
        views.setFloat(R.id.widget_analog_minute, "setRotation", WidgetFormat.minuteHandDegrees(minute))
        views.setTextColor(R.id.widget_clock_date, NativeWidgetPreferences.variantColor(context, appWidgetId))
        return views
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleTick(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelTick(context)
    }

    private fun scheduleTick(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        // Inexact, so the system can batch it with other wakeups instead of waking the device.
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + TICK_MILLIS,
            TICK_MILLIS,
            tickIntent(context),
        )
    }

    private fun cancelTick(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(tickIntent(context))
    }

    private fun tickIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, AnalogClockWidgetProvider::class.java).setAction(ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val TICK_MILLIS = 60_000L
    }
}
