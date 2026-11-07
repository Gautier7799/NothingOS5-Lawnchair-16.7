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
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.widget.RemoteViews
import com.android.launcher3.R

/**
 * Battery level and charging state.
 *
 * The reading is taken from the sticky `ACTION_BATTERY_CHANGED` broadcast, which is also what the
 * platform sends on every meaningful change, so the widget stays current without polling.
 */
class BatteryWidgetProvider : LawnchairWidgetProvider() {

    override fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_battery)
        NativeWidgetPreferences.applyCard(context, views, appWidgetId)

        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
        val full = status == BatteryManager.BATTERY_STATUS_FULL
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else 0

        views.setTextViewText(R.id.widget_battery_level, WidgetFormat.batteryLevelLabel(percent))
        views.setTextViewText(
            R.id.widget_battery_status,
            context.getString(
                when {
                    full -> R.string.widget_battery_status_full
                    charging -> R.string.widget_battery_status_charging
                    else -> R.string.widget_battery_status_discharging
                },
            ),
        )
        views.setProgressBar(R.id.widget_battery_bar, 100, percent, false)
        views.setTextColor(R.id.widget_battery_level, toneColor(context, percent, charging || full))
        return views
    }

    private fun toneColor(context: Context, level: Int, charging: Boolean): Int = when (
        WidgetFormat.batteryTone(level, charging)
    ) {
        WidgetFormat.BatteryTone.CHARGING -> context.getColor(R.color.widget_accent)

        WidgetFormat.BatteryTone.LOW,
        WidgetFormat.BatteryTone.CRITICAL,
        -> context.getColor(R.color.widget_warning)

        WidgetFormat.BatteryTone.NORMAL -> context.getColor(R.color.widget_on_surface)
    }
}
