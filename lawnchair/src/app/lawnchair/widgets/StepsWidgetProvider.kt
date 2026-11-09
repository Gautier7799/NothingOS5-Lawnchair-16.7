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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View
import android.widget.RemoteViews
import com.android.launcher3.R

/**
 * Step count for today against a configurable goal.
 *
 * The reading comes from the `TYPE_STEP_COUNTER` sensor, which reports the steps since boot; the
 * widget subtracts the value captured at the start of the day. That baseline is stored per widget
 * so a reboot (which resets the sensor) does not produce a nonsensical count. The physical-activity
 * permission is required to read the sensor, so the widget shows a permission prompt when it is
 * missing instead of a wrong number.
 */
class StepsWidgetProvider : LawnchairWidgetProvider() {

    override fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_steps)
        NativeWidgetPreferences.applyCard(context, views, appWidgetId)
        NativeWidgetPreferences.applyHeader(
            context,
            views,
            appWidgetId,
            R.drawable.ic_widget_steps,
            context.getString(R.string.widget_steps_label),
        )
        NativeWidgetPreferences.applyInset(context, views, appWidgetId)

        if (!hasActivityPermission(context)) {
            views.setTextViewText(R.id.widget_value, context.getString(R.string.widget_steps_permission))
            views.setTextColor(R.id.widget_value, NativeWidgetPreferences.primaryColor(context, appWidgetId))
            views.setTextViewText(R.id.widget_inset_detail, context.getString(R.string.widget_steps_permission_hint))
            views.setProgressBar(R.id.widget_bar, 100, 0, false)
            views.setViewVisibility(R.id.widget_steps_unit, View.GONE)
            return views
        }

        val steps = StepReader.todaySteps(context, appWidgetId)
        val goal = NativeWidgetPreferences.getStepGoal(context, appWidgetId)
        if (steps == null) {
            views.setTextViewText(R.id.widget_value, context.getString(R.string.widget_unavailable))
            views.setTextColor(R.id.widget_value, NativeWidgetPreferences.primaryColor(context, appWidgetId))
            views.setTextViewText(R.id.widget_inset_detail, context.getString(R.string.widget_steps_no_sensor))
            views.setProgressBar(R.id.widget_bar, 100, 0, false)
            views.setViewVisibility(R.id.widget_steps_unit, View.GONE)
            return views
        }

        views.setViewVisibility(R.id.widget_steps_unit, View.VISIBLE)
        views.setTextViewText(R.id.widget_value, WidgetFormat.stepCountLabel(steps))
        views.setTextColor(R.id.widget_value, NativeWidgetPreferences.primaryColor(context, appWidgetId))
        views.setTextViewText(R.id.widget_inset_detail, WidgetFormat.stepGoalLabel(steps, goal))
        views.setProgressBar(R.id.widget_bar, 100, WidgetFormat.stepGoalPercent(steps, goal), false)
        return views
    }

    private fun hasActivityPermission(context: Context): Boolean = context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /**
     * Reads the step sensor and converts the since-boot value into a today-relative count.
     *
     * The `TYPE_STEP_COUNTER` sensor reports the total since boot, so today's steps are the
     * difference from the value captured at the start of the day. That baseline is persisted in
     * [NativeWidgetPreferences] because it must survive process death and a reboot (the sensor
     * resets to zero on boot).
     */
    object StepReader {

        /** Today's step count, or null when the device has no step sensor. */
        fun todaySteps(context: Context, appWidgetId: Int): Int? {
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return null
            val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
            val total = readOnce(manager, sensor) ?: return null

            val today = java.time.LocalDate.now().toEpochDay()
            val baselineDay = NativeWidgetPreferences.getStepBaselineDay(context, appWidgetId)
            var baseline = NativeWidgetPreferences.getStepBaseline(context, appWidgetId)
            if (baselineDay != today || baseline == null || total < baseline) {
                baseline = total
                NativeWidgetPreferences.setStepBaseline(context, appWidgetId, total)
                NativeWidgetPreferences.setStepBaselineDay(context, appWidgetId, today)
            }
            return (total - baseline).coerceAtLeast(0f).toInt()
        }

        /** Blocks briefly for the sensor's first event; it reports immediately on registration. */
        private fun readOnce(manager: SensorManager, sensor: Sensor): Float? {
            var value: Float? = null
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (value == null) value = event.values.firstOrNull()
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            if (!manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)) return null
            val deadline = System.currentTimeMillis() + 250
            while (value == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(5)
            }
            manager.unregisterListener(listener)
            return value
        }
    }
}
