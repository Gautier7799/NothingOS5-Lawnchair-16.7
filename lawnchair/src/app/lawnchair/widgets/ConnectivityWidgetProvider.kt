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
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.widget.RemoteViews
import com.android.launcher3.R

/**
 * Bluetooth state and the active audio output.
 *
 * New to the Lawnchair widget library: it reports whether Bluetooth is on and which output device
 * the audio is routed to. Both facts are read without runtime permissions (the Bluetooth state
 * comes from [Settings.Global] and the route from [AudioManager]), so no permission prompt is
 * needed and the widget degrades to "Unavailable" on hardware without those features.
 */
class ConnectivityWidgetProvider : LawnchairWidgetProvider() {

    override fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_connectivity)
        NativeWidgetPreferences.applyCard(context, views, appWidgetId)
        NativeWidgetPreferences.applyHeader(
            context,
            views,
            appWidgetId,
            R.drawable.ic_widget_wifi,
            context.getString(R.string.widget_connectivity_label),
        )
        NativeWidgetPreferences.applyInset(context, views, appWidgetId)

        // The hero value is Wi-Fi when connected, otherwise the Bluetooth state.
        val wifi = wifiState(context)
        if (wifi.connected) {
            views.setTextViewText(R.id.widget_value, context.getString(R.string.widget_connectivity_wifi_on))
            views.setTextColor(R.id.widget_value, context.getColor(R.color.widget_success))
            views.setTextViewText(
                R.id.widget_inset_detail,
                wifi.network ?: context.getString(R.string.widget_connectivity_wifi_no_network),
            )
            return views
        }

        val bluetoothOn = bluetoothEnabled(context)
        if (bluetoothOn == null) {
            views.setTextViewText(R.id.widget_value, context.getString(R.string.widget_unavailable))
            views.setTextColor(R.id.widget_value, NativeWidgetPreferences.primaryColor(context, appWidgetId))
        } else {
            views.setTextViewText(
                R.id.widget_value,
                context.getString(if (bluetoothOn) R.string.widget_connectivity_bluetooth_on else R.string.widget_connectivity_bluetooth_off),
            )
            views.setTextColor(
                R.id.widget_value,
                if (bluetoothOn) context.getColor(R.color.widget_success) else NativeWidgetPreferences.primaryColor(context, appWidgetId),
            )
        }

        val device = activeOutputLabel(context)
        views.setTextViewText(
            R.id.widget_inset_detail,
            context.getString(R.string.widget_connectivity_audio_output, device),
        )
        return views
    }

    override fun onEnabled(context: Context) {
        NativeWidgetRefresh.register(context, javaClass, BLUETOOTH_URI) { refreshAll(context) }
    }

    override fun onDisabled(context: Context) {
        NativeWidgetRefresh.unregister(context, javaClass, BLUETOOTH_URI)
    }

    /** Wi-Fi connectivity snapshot: whether an active network is present and its name. */
    private data class WifiState(val connected: Boolean, val network: String?)

    private fun wifiState(context: Context): WifiState {
        val manager = context.getSystemService(android.net.ConnectivityManager::class.java)
            ?: return WifiState(false, null)
        val network = manager.activeNetwork ?: return WifiState(false, null)
        val caps = manager.getNetworkCapabilities(network) ?: return WifiState(false, null)
        if (!caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) return WifiState(false, null)
        // SSID is only exposed with location permission; fall back to a generic label.
        val name = runCatching {
            val wifi = context.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)
            @Suppress("DEPRECATION")
            wifi?.connectionInfo?.ssid?.takeUnless { it.isNullOrBlank() || it == "<unknown ssid>" }
        }.getOrNull()
        return WifiState(true, name)
    }

    /** `null` when the device has no Bluetooth radio. */
    private fun bluetoothEnabled(context: Context): Boolean? = runCatching {
        val supported = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_BLUETOOTH)
        if (!supported) return null
        Settings.Global.getInt(context.contentResolver, Settings.Global.BLUETOOTH_ON, 0) == 1
    }.getOrNull()

    private fun activeOutputLabel(context: Context): String {
        val audio = context.getSystemService(AudioManager::class.java)
            ?: return context.getString(R.string.widget_connectivity_output_device_speaker)
        val outputs = runCatching { audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }.getOrNull()
            ?: return context.getString(R.string.widget_connectivity_output_device_speaker)
        return when (WidgetFormat.classifyOutput(outputs.map { it.type })) {
            WidgetFormat.AudioOutput.BLUETOOTH -> context.getString(R.string.widget_connectivity_output_device_bluetooth)
            WidgetFormat.AudioOutput.HEADSET -> context.getString(R.string.widget_connectivity_output_device_headset)
            WidgetFormat.AudioOutput.USB -> context.getString(R.string.widget_connectivity_output_device_usb)
            WidgetFormat.AudioOutput.SPEAKER -> context.getString(R.string.widget_connectivity_output_device_speaker)
        }
    }

    companion object {
        private val BLUETOOTH_URI: Uri = Settings.Global.getUriFor(Settings.Global.BLUETOOTH_ON)
    }
}
