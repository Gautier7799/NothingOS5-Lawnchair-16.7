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

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.widget.RemoteViews
import com.android.launcher3.R
import java.util.Calendar

/**
 * Today's date plus the next upcoming calendar event.
 *
 * Reading the calendar requires `READ_CALENDAR`. When the permission has not been granted the
 * widget still renders the date and shows a prompt that opens the system permission dialog, so the
 * widget is useful before it is granted and never shows stale placeholder data.
 */
class CalendarWidgetProvider : LawnchairWidgetProvider() {

    override fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_calendar)
        NativeWidgetPreferences.applyCard(context, views, appWidgetId)
        NativeWidgetPreferences.applyHeader(
            context,
            views,
            appWidgetId,
            R.drawable.ic_widget_calendar,
            context.getString(R.string.widget_calendar_label),
        )
        NativeWidgetPreferences.applyInset(context, views, appWidgetId)
        NativeWidgetPreferences.applyDetailVisibility(context, views, appWidgetId)
        views.setTextColor(R.id.widget_date_day, NativeWidgetPreferences.primaryColor(context, appWidgetId))

        if (!hasCalendarPermission(context)) {
            renderPermissionPrompt(context, views, appWidgetId)
            return views
        }

        val event = nextEvent(context)
        if (event == null) {
            views.setTextViewText(R.id.widget_event_title, context.getString(R.string.widget_calendar_no_events))
            views.setTextViewText(R.id.widget_inset_detail, context.getString(R.string.widget_calendar_none_hint))
        } else {
            views.setTextViewText(R.id.widget_event_title, event.title)
            views.setTextViewText(R.id.widget_inset_detail, event.whenLabel)
        }
        return views
    }

    private fun renderPermissionPrompt(context: Context, views: RemoteViews, appWidgetId: Int) {
        views.setTextViewText(R.id.widget_event_title, context.getString(R.string.widget_calendar_permission_title))
        views.setTextViewText(R.id.widget_inset_detail, context.getString(R.string.widget_calendar_permission_hint))
        // Tapping the panel opens the permission request in the widget's configuration activity.
        views.setOnClickPendingIntent(R.id.widget_inset, permissionPendingIntent(context, appWidgetId))
    }

    private fun permissionPendingIntent(context: Context, appWidgetId: Int) = android.app.PendingIntent.getActivity(
        context,
        appWidgetId,
        Intent(context, LawnchairWidgetConfigActivity::class.java).apply {
            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        },
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
    )

    private fun hasCalendarPermission(context: Context): Boolean = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Next event starting within the next 7 days, or null when there is none. */
    private fun nextEvent(context: Context): Event? {
        val now = System.currentTimeMillis()
        val horizon = now + 7L * 24 * 60 * 60 * 1000
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.ALL_DAY,
        )
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString())
            .appendPath(horizon.toString())
            .build()
        return runCatching {
            context.contentResolver.query(
                uri,
                projection,
                "${CalendarContract.Instances.BEGIN} >= ?",
                arrayOf(now.toString()),
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val title = cursor.getString(0) ?: return@use null
                val begin = cursor.getLong(1)
                val allDay = cursor.getInt(2) == 1
                val whenLabel = label(context, begin, allDay)
                Event(title.ifBlank { context.getString(R.string.widget_calendar_untitled) }, whenLabel)
            }
        }.getOrNull()
    }

    private fun label(context: Context, beginMillis: Long, allDay: Boolean): String {
        val now = Calendar.getInstance()
        val start = Calendar.getInstance().apply { timeInMillis = beginMillis }
        val offset = WidgetFormat.dayOffset(
            nowYear = now.get(Calendar.YEAR),
            nowDayOfYear = now.get(Calendar.DAY_OF_YEAR),
            targetYear = start.get(Calendar.YEAR),
            targetDayOfYear = start.get(Calendar.DAY_OF_YEAR),
        )
        if (allDay) {
            return when (offset) {
                0 -> context.getString(R.string.widget_calendar_today)

                1 -> context.getString(R.string.widget_calendar_tomorrow)

                else -> start.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, java.util.Locale.getDefault())
                    ?: ""
            }
        }
        val is24 = DateFormat.is24HourFormat(context)
        val weekday = start.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, java.util.Locale.getDefault()) ?: ""
        return WidgetFormat.formatEventWhen(
            dayOffset = offset,
            weekday = weekday,
            hour = start.get(Calendar.HOUR_OF_DAY),
            minute = start.get(Calendar.MINUTE),
            is24Hour = is24,
        )
    }

    private data class Event(val title: String, val whenLabel: String)
}
