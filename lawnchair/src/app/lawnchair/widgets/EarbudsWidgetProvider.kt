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

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.android.launcher3.R

/**
 * Earbuds and accessory battery levels.
 *
 * Reports the per-component level (left bud, right bud, charging case) of the connected Bluetooth
 * accessory. The platform exposes these through the hidden `BluetoothDevice.getBatteryLevel` and
 * `getBatteryLevelLeft/Right/Case` methods, which are reached by reflection: the widget therefore
 * degrades to a clear "no earbuds" state instead of failing when the vendor does not implement
 * them or when Bluetooth permission has not been granted.
 */
class EarbudsWidgetProvider : LawnchairWidgetProvider() {

    override fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_earbuds)
        NativeWidgetPreferences.applyCard(context, views, appWidgetId)
        NativeWidgetPreferences.applyHeader(
            context,
            views,
            appWidgetId,
            R.drawable.ic_widget_earbuds,
            context.getString(R.string.widget_earbuds_label),
        )
        NativeWidgetPreferences.applyInset(context, views, appWidgetId)

        val accessory = AccessoryBattery.read(context)
        if (accessory == null) {
            views.setTextViewText(R.id.widget_value, context.getString(R.string.widget_earbuds_none))
            views.setTextColor(R.id.widget_value, NativeWidgetPreferences.primaryColor(context, appWidgetId))
            views.setTextViewText(R.id.widget_inset_detail, context.getString(R.string.widget_earbuds_hint))
            views.setViewVisibility(R.id.widget_earbuds_levels, android.view.View.GONE)
            return views
        }

        views.setTextViewText(R.id.widget_value, accessory.name ?: context.getString(R.string.widget_earbuds_connected))
        views.setTextColor(R.id.widget_value, context.getColor(R.color.widget_success))
        views.setViewVisibility(R.id.widget_earbuds_levels, android.view.View.VISIBLE)
        views.setTextViewText(R.id.widget_earbuds_left, WidgetFormat.accessoryBatteryLabel(accessory.left))
        views.setTextViewText(R.id.widget_earbuds_right, WidgetFormat.accessoryBatteryLabel(accessory.right))
        views.setTextViewText(R.id.widget_earbuds_case, WidgetFormat.accessoryBatteryLabel(accessory.case))
        views.setTextViewText(
            R.id.widget_inset_detail,
            context.getString(R.string.widget_earbuds_detail, WidgetFormat.accessoryBatteryLabel(accessory.overall)),
        )
        return views
    }

    override fun onEnabled(context: Context) {
        NativeWidgetRefresh.register(context, javaClass, BLUETOOTH_URI) { refreshAll(context) }
        refreshAll(context)
    }

    override fun onDisabled(context: Context) {
        NativeWidgetRefresh.unregister(context, javaClass, BLUETOOTH_URI)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            BluetoothDevice.ACTION_BATTERY_LEVEL_CHANGED,
            BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED,
            -> refreshAll(context)
        }
    }

    /** A resolved accessory reading; any level may be null when the device does not report it. */
    data class AccessoryReading(
        val name: String?,
        val overall: Int?,
        val left: Int?,
        val right: Int?,
        val case: Int?,
    )

    /**
     * Reads the first connected, battery-reporting accessory.
     *
     * Prefers a classic-audio bonded device because that is where earbud vendors publish the
     * per-component levels; returns null when Bluetooth is unavailable, permission is missing, or
     * no device reports a level.
     */
    object AccessoryBattery {

        @SuppressLint("MissingPermission")
        fun read(context: Context): AccessoryReading? {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter ?: return null
            if (!adapter.isEnabled) return null
            val devices = runCatching { adapter.bondedDevices }.getOrNull() ?: return null
            devices.forEach { device ->
                val overall = level(device, "getBatteryLevel")
                val left = level(device, "getBatteryLevelLeft")
                val right = level(device, "getBatteryLevelRight")
                val case = level(device, "getBatteryLevelCase")
                if (listOfNotNull(overall, left, right, case).isNotEmpty()) {
                    return AccessoryReading(
                        name = runCatching { device.alias }.getOrNull() ?: runCatching { device.name }.getOrNull(),
                        overall = overall,
                        left = left,
                        right = right,
                        case = case,
                    )
                }
            }
            return null
        }

        /** Invokes a hidden `getBatteryLevel*` accessor, returning null when unavailable. */
        private fun level(device: BluetoothDevice, method: String): Int? = runCatching {
            val value = device.javaClass.getMethod(method).invoke(device) as? Int ?: return null
            if (value in 0..100) value else null
        }.getOrNull()
    }

    private companion object {
        val BLUETOOTH_URI: android.net.Uri = android.provider.Settings.Global.getUriFor(android.provider.Settings.Global.BLUETOOTH_ON)
    }
}
