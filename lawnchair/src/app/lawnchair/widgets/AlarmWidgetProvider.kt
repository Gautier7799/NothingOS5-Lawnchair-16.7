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
import android.content.Context
import android.widget.RemoteViews
import com.android.launcher3.R
import java.util.Calendar
import java.util.Locale

/**
 * Next scheduled alarm.
 *
 * New to the Lawnchair widget library: it reads [AlarmManager.getNextAlarmClock], which is public
 * and needs no permission. When no alarm is set the widget says so, and it re-renders when the
 * alarm clock changes because the platform broadcasts `ACTION_NEXT_ALARM_CLOCK_CHANGED` to the
 * provider.
 */
class AlarmWidgetProvider : LawnchairWidgetProvider() {

    override fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_alarm)
        NativeWidgetPreferences.applyCard(context, views, appWidgetId)
        NativeWidgetPreferences.applyHeader(
            context,
            views,
            appWidgetId,
            R.drawable.ic_widget_alarm,
            context.getString(R.string.widget_alarm_label),
        )
        NativeWidgetPreferences.applyInset(context, views, appWidgetId)

        val alarm = readAlarm(context)
        if (alarm == null) {
            views.setTextViewText(R.id.widget_value, context.getString(R.string.widget_alarm_none))
            views.setTextViewText(R.id.widget_inset_detail, context.getString(R.string.widget_alarm_none_detail))
            views.setTextColor(R.id.widget_value, NativeWidgetPreferences.primaryColor(context, appWidgetId))
            return views
        }

        views.setTextViewText(R.id.widget_value, WidgetFormat.formatTime(alarm.hour, alarm.minute, is24Hour = is24Hour(context)))
        views.setTextViewText(
            R.id.widget_inset_detail,
            WidgetFormat.formatAlarmDay(alarm.dayOffset, alarm.weekday, null),
        )
        views.setTextColor(R.id.widget_value, NativeWidgetPreferences.primaryColor(context, appWidgetId))
        return views
    }

    private fun is24Hour(context: Context): Boolean = android.text.format.DateFormat.is24HourFormat(context)

    private fun readAlarm(context: Context): AlarmSnapshot? = runCatching {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return null
        val info = manager.nextAlarmClock ?: return null
        val trigger = Calendar.getInstance().apply { timeInMillis = info.triggerTime }
        val now = Calendar.getInstance()
        val dayOffset = WidgetFormat.dayOffset(
            nowYear = now.get(Calendar.YEAR),
            nowDayOfYear = now.get(Calendar.DAY_OF_YEAR),
            targetYear = trigger.get(Calendar.YEAR),
            targetDayOfYear = trigger.get(Calendar.DAY_OF_YEAR),
        )
        AlarmSnapshot(
            hour = trigger.get(Calendar.HOUR_OF_DAY),
            minute = trigger.get(Calendar.MINUTE),
            dayOffset = dayOffset,
            weekday = trigger.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()) ?: "",
        )
    }.getOrNull()

    private data class AlarmSnapshot(
        val hour: Int,
        val minute: Int,
        val dayOffset: Int,
        val weekday: String,
    )
}
