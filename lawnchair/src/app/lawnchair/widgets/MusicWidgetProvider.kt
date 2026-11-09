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
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.widget.RemoteViews
import com.android.launcher3.R

/**
 * Now-playing media controls.
 *
 * Reads the active [MediaController] from [MediaSessionManager] and renders the current track with
 * transport buttons. Notification-listener access is what grants visibility into other apps'
 * sessions; without it the widget shows a "nothing playing" state rather than requesting anything.
 * The widget re-reads on every media button it handles and on the platform's periodic update.
 */
class MusicWidgetProvider : LawnchairWidgetProvider() {

    override fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_music)
        NativeWidgetPreferences.applyCard(context, views, appWidgetId)
        NativeWidgetPreferences.applyHeader(
            context,
            views,
            appWidgetId,
            R.drawable.ic_widget_music,
            context.getString(R.string.widget_music_label),
        )
        NativeWidgetPreferences.applyInset(context, views, appWidgetId)

        val snapshot = MediaSnapshot.read(context)
        if (snapshot == null) {
            views.setTextViewText(R.id.widget_value, context.getString(R.string.widget_music_nothing))
            views.setTextColor(R.id.widget_value, NativeWidgetPreferences.primaryColor(context, appWidgetId))
            views.setTextViewText(R.id.widget_inset_detail, context.getString(R.string.widget_music_hint))
            views.setViewVisibility(R.id.widget_music_controls, android.view.View.GONE)
            return views
        }

        views.setTextViewText(
            R.id.widget_value,
            WidgetFormat.trackLabel(snapshot.title, snapshot.artist, context.getString(R.string.widget_music_unknown_track)),
        )
        views.setTextColor(R.id.widget_value, NativeWidgetPreferences.primaryColor(context, appWidgetId))
        views.setTextViewText(R.id.widget_inset_detail, snapshot.album ?: context.getString(R.string.widget_music_playing))
        views.setViewVisibility(R.id.widget_music_controls, android.view.View.VISIBLE)
        views.setImageViewResource(
            R.id.widget_music_play,
            if (snapshot.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )
        views.setOnClickPendingIntent(R.id.widget_music_prev, transport(context, MediaAction.PREVIOUS))
        views.setOnClickPendingIntent(R.id.widget_music_play, transport(context, MediaAction.PLAY_PAUSE))
        views.setOnClickPendingIntent(R.id.widget_music_next, transport(context, MediaAction.NEXT))
        return views
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_MEDIA) {
            val action = intent.getStringExtra(EXTRA_MEDIA_ACTION)?.let { runCatching { MediaAction.valueOf(it) }.getOrNull() }
            if (action != null) dispatch(context, action)
            refreshAll(context)
        }
    }

    private fun transport(context: Context, action: MediaAction): PendingIntent {
        val intent = Intent(context, javaClass).setAction(ACTION_MEDIA).putExtra(EXTRA_MEDIA_ACTION, action.name)
        return PendingIntent.getBroadcast(
            context,
            action.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun dispatch(context: Context, action: MediaAction) {
        val controller = MediaSnapshot.controller(context) ?: return
        val transport = controller.transportControls
        runCatching {
            when (action) {
                MediaAction.PLAY_PAUSE -> if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) transport.pause() else transport.play()
                MediaAction.NEXT -> transport.skipToNext()
                MediaAction.PREVIOUS -> transport.skipToPrevious()
            }
        }
    }

    /** The transport buttons the widget exposes. */
    enum class MediaAction { PREVIOUS, PLAY_PAUSE, NEXT }

    /** A flattened view of the active media session. */
    data class MediaSnapshot(
        val title: String?,
        val artist: String?,
        val album: String?,
        val playing: Boolean,
    ) {
        companion object {
            /** The most recently active media controller, or null when none is exposed. */
            fun controller(context: Context): MediaController? {
                val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return null
                val component = ComponentName(context, MusicWidgetProvider::class.java)
                val sessions = runCatching { manager.getActiveSessions(component) }.getOrNull() ?: return null
                return sessions.firstOrNull { it.playbackState != null } ?: sessions.firstOrNull()
            }

            fun read(context: Context): MediaSnapshot? {
                val controller = controller(context) ?: return null
                val metadata = controller.metadata ?: return null
                val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                if (title.isNullOrBlank() && artist.isNullOrBlank()) return null
                return MediaSnapshot(
                    title = title,
                    artist = artist,
                    album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
                    playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING,
                )
            }
        }
    }

    companion object {
        /** Internal action carrying a [MediaAction] from a transport button. */
        const val ACTION_MEDIA = "app.lawnchair.widgets.action.MEDIA"
        const val EXTRA_MEDIA_ACTION = "media_action"
    }
}
