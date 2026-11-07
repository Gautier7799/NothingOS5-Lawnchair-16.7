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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.widget.RemoteViews
import com.android.launcher3.R

/**
 * Media volume and Do Not Disturb state.
 *
 * New to the Lawnchair widget library: it mirrors the media stream level and the current ringer /
 * DND state. DND is only readable with notification-policy access, so when the permission is
 * missing the widget shows a one-tap prompt that opens the system settings page instead of failing.
 */
class VolumeWidgetProvider : LawnchairWidgetProvider() {

    override fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val wide = widgetWidth(context, appWidgetId) >= WIDE_WIDTH_DP
        val layout = if (wide) R.layout.widget_volume_wide else R.layout.widget_volume
        val views = RemoteViews(context.packageName, layout)

        NativeWidgetPreferences.applyCard(context, views, appWidgetId)
        NativeWidgetPreferences.applyHeader(
            context,
            views,
            appWidgetId,
            R.drawable.ic_widget_volume,
            context.getString(R.string.widget_volume_label),
        )
        NativeWidgetPreferences.applyInset(context, views, appWidgetId)
        views.setTextColor(R.id.widget_value, NativeWidgetPreferences.primaryColor(context, appWidgetId))

        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0
        val current = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        val percent = if (max > 0) (current * 100 / max) else 0
        val muted = audio?.isStreamMute(AudioManager.STREAM_MUSIC) ?: false
        views.setTextViewText(R.id.widget_value, if (muted) context.getString(R.string.widget_volume_muted) else "$percent%")
        views.setProgressBar(R.id.widget_bar, 100, percent, false)

        val dnd = WidgetFormat.dndState(
            accessGranted = notificationPolicyAccessGranted(context),
            interruptionFilter = currentInterruptionFilter(context),
        )
        views.setTextViewText(
            R.id.widget_inset_detail,
            context.getString(
                when (dnd) {
                    WidgetFormat.DndState.ACCESS_REQUIRED -> R.string.widget_volume_dnd_access_required
                    WidgetFormat.DndState.OFF -> R.string.widget_volume_dnd_off
                    WidgetFormat.DndState.PRIORITY -> R.string.widget_volume_dnd_priority
                    WidgetFormat.DndState.ALARMS -> R.string.widget_volume_dnd_alarms
                    WidgetFormat.DndState.ALL -> R.string.widget_volume_dnd_all
                },
            ),
        )
        if (dnd == WidgetFormat.DndState.ACCESS_REQUIRED) {
            views.setOnClickPendingIntent(R.id.widget_inset_detail, dndSettingsIntent(context))
            views.setOnClickPendingIntent(R.id.widget_value, dndSettingsIntent(context))
        } else {
            views.setOnClickPendingIntent(R.id.widget_inset_detail, null)
            views.setOnClickPendingIntent(R.id.widget_value, null)
        }
        return views
    }

    override fun onEnabled(context: Context) {
        registerObserver(context)
    }

    override fun onDisabled(context: Context) {
        NativeWidgetRefresh.unregister(context, javaClass, VOLUME_URI)
    }

    private fun registerObserver(context: Context) {
        NativeWidgetRefresh.register(context, javaClass, VOLUME_URI) { refreshAll(context) }
    }

    private fun notificationPolicyAccessGranted(context: Context): Boolean = runCatching {
        context.getSystemService(android.app.NotificationManager::class.java)
            ?.isNotificationPolicyAccessGranted == true
    }.getOrDefault(false)

    private fun currentInterruptionFilter(context: Context): Int = runCatching {
        context.getSystemService(android.app.NotificationManager::class.java)
            ?.currentInterruptionFilter ?: android.app.NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }.getOrDefault(android.app.NotificationManager.INTERRUPTION_FILTER_UNKNOWN)

    private fun dndSettingsIntent(context: Context): PendingIntent? {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        private val VOLUME_URI: Uri = Settings.System.getUriFor("volume_music")
    }
}
