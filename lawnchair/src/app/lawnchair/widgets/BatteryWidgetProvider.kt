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
 * Battery level, charging state and thermal/electrical detail.
 *
 * The reading comes from the sticky `ACTION_BATTERY_CHANGED` broadcast, which the platform also
 * sends on every meaningful change, so the widget stays current without polling.
 */
class BatteryWidgetProvider : LawnchairWidgetProvider() {

    override fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val wide = widgetWidth(context, appWidgetId) >= WIDE_WIDTH_DP
        val layout = if (wide) R.layout.widget_battery_wide else R.layout.widget_battery
        val views = RemoteViews(context.packageName, layout)

        NativeWidgetPreferences.applyCard(context, views, appWidgetId)
        NativeWidgetPreferences.applyHeader(
            context,
            views,
            appWidgetId,
            R.drawable.ic_widget_battery,
            context.getString(R.string.widget_battery_label),
        )
        NativeWidgetPreferences.applyInset(context, views, appWidgetId)
        NativeWidgetPreferences.applyDetailVisibility(context, views, appWidgetId)

        val reading = BatteryReading.from(context)
        views.setTextViewText(R.id.widget_value, WidgetFormat.batteryLevelLabel(reading.percent))
        views.setTextColor(R.id.widget_value, toneColor(context, appWidgetId, reading))

        // The device silhouette fills with the level. ImageView levels run 0..10000, so the
        // percentage is scaled; low/critical readings swap to the warning fill.
        val fill = when (WidgetFormat.batteryTone(reading.percent, reading.charging || reading.full)) {
            WidgetFormat.BatteryTone.LOW, WidgetFormat.BatteryTone.CRITICAL -> R.drawable.widget_battery_fill_warning
            else -> R.drawable.widget_battery_fill
        }
        views.setImageViewResource(R.id.widget_battery_fill, fill)
        views.setInt(R.id.widget_battery_fill, "setImageLevel", reading.percent.coerceIn(0, 100) * 100)

        views.setTextViewText(R.id.widget_inset_detail, buildDetail(context, reading))
        return views
    }

    private fun buildDetail(context: Context, reading: BatteryReading): CharSequence {
        val status = context.getString(
            when {
                reading.full -> R.string.widget_battery_status_full
                reading.charging -> R.string.widget_battery_status_charging
                else -> R.string.widget_battery_status_discharging
            },
        )
        val extras = mutableListOf<String>()
        reading.temperatureC?.let {
            extras += context.getString(R.string.widget_battery_temperature, WidgetFormat.formatTemperature(it))
        }
        reading.voltageV?.let {
            extras += context.getString(R.string.widget_battery_voltage, WidgetFormat.formatVoltage(it))
        }
        return if (extras.isEmpty()) status else (listOf(status) + extras).joinToString(" \u00b7 ")
    }

    private fun toneColor(context: Context, appWidgetId: Int, reading: BatteryReading): Int = when (
        WidgetFormat.batteryTone(reading.percent, reading.charging || reading.full)
    ) {
        WidgetFormat.BatteryTone.CHARGING -> context.getColor(R.color.widget_success)

        WidgetFormat.BatteryTone.LOW,
        WidgetFormat.BatteryTone.CRITICAL,
        -> context.getColor(R.color.widget_warning)

        WidgetFormat.BatteryTone.NORMAL -> NativeWidgetPreferences.primaryColor(context, appWidgetId)
    }

    /** Immutable snapshot of the battery state, tolerant of missing extras. */
    data class BatteryReading(
        val percent: Int,
        val charging: Boolean,
        val full: Boolean,
        val temperatureC: Float?,
        val voltageV: Float?,
    ) {
        companion object {
            fun from(context: Context): BatteryReading {
                val intent: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
                    ?: BatteryManager.BATTERY_STATUS_UNKNOWN
                val percent = if (level >= 0 && scale > 0) level * 100 / scale else 0
                val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
                val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE) ?: Int.MIN_VALUE
                return BatteryReading(
                    percent = percent,
                    charging = status == BatteryManager.BATTERY_STATUS_CHARGING,
                    full = status == BatteryManager.BATTERY_STATUS_FULL,
                    temperatureC = if (tempTenths == Int.MIN_VALUE) null else tempTenths / 10f,
                    voltageV = if (voltageMv == Int.MIN_VALUE) null else voltageMv / 1000f,
                )
            }
        }
    }
}
