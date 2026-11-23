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

package app.lawnchair.sidebar

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.lawnchair.allapps.caddy.CaddyMaterialYou
import com.android.launcher3.R

/**
 * The quick-control strip that sits above the sidebar's app column.
 *
 * Ported from the standalone "Samart sidebar" app (MIT). That app draws these tiles in a
 * `WindowManager` overlay from a foreground service; here they are ordinary views inside the
 * sidebar panel, so no overlay permission or background service is involved.
 *
 * Every toggle acts on the device directly through a framework API. Two of them write
 * `Settings.System`, which needs `WRITE_SETTINGS`; the tile opens the grant screen instead of
 * failing silently when the permission is missing.
 */
class QuickControlStrip(private val context: Context) {

    private val tiles = LinkedHashMap<QuickControl, LinearLayout>()
    private val cameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    /** Root view holding one tile per enabled control, or null when none are enabled. */
    var view: View? = null
        private set

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        context.resources.displayMetrics,
    ).toInt()

    /**
     * Builds the strip for [controls].
     *
     * Returns null when the list is empty so the caller can skip the container entirely rather
     * than leave a gap above the app column.
     */
    fun build(controls: List<QuickControl>): View? {
        tiles.clear()
        if (controls.isEmpty()) {
            view = null
            return null
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(TILE_GAP_DP) }
        }

        controls.forEach { control ->
            val tile = buildTile(control)
            tiles[control] = tile
            row.addView(
                tile,
                LinearLayout.LayoutParams(0, dp(TILE_HEIGHT_DP), 1f).apply {
                    marginStart = dp(TILE_GAP_DP / 2)
                    marginEnd = dp(TILE_GAP_DP / 2)
                },
            )
        }

        refresh()
        view = row
        return row
    }

    private fun buildTile(control: QuickControl): LinearLayout {
        val tile = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = labelFor(control)
            setOnClickListener { onTileClicked(control) }
        }

        val icon = ImageView(context).apply {
            setImageResource(iconFor(control))
            layoutParams = LinearLayout.LayoutParams(dp(ICON_SIZE_DP), dp(ICON_SIZE_DP))
        }
        val label = TextView(context).apply {
            text = labelFor(control)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LABEL_TEXT_SIZE_SP)
            gravity = Gravity.CENTER
            maxLines = 1
        }

        tile.addView(icon)
        tile.addView(label)
        return tile
    }

    /** Re-reads every toggle from the device and repaints the tiles. */
    fun refresh() {
        tiles.forEach { (control, tile) ->
            val active = QuickControlState.isActive(control, currentState(control))
            tile.background = GradientDrawable().apply {
                setColor(
                    if (active) {
                        CaddyMaterialYou.accent(context)
                    } else {
                        CaddyMaterialYou.surfaceContainer(context)
                    },
                )
                cornerRadius = dp(TILE_RADIUS_DP).toFloat()
                setStroke(dp(1), CaddyMaterialYou.outlineVariant(context))
            }
            val contentColor =
                if (active) {
                    CaddyMaterialYou.onSurface(context)
                } else {
                    CaddyMaterialYou.onSurfaceVariant(context)
                }
            (tile.getChildAt(0) as? ImageView)?.setColorFilter(contentColor)
            (tile.getChildAt(1) as? TextView)?.setTextColor(contentColor)
        }
    }

    /** Current on/off value for [control], in the encoding [QuickControlState] expects. */
    private fun currentState(control: QuickControl): Int = when (control) {
        QuickControl.TORCH -> if (isTorchOn()) 1 else 0

        QuickControl.AUTO_ROTATE -> settingInt(
            Settings.System.ACCELEROMETER_ROTATION,
            default = 0,
        )

        QuickControl.AUTO_BRIGHTNESS -> if (
            settingInt(Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        ) {
            1
        } else {
            0
        }

        QuickControl.DND -> if (
            notificationManager.currentInterruptionFilter !=
            NotificationManager.INTERRUPTION_FILTER_ALL
        ) {
            1
        } else {
            0
        }

        QuickControl.RINGER -> audioManager.ringerMode
    }

    private fun settingInt(key: String, default: Int): Int = try {
        Settings.System.getInt(context.contentResolver, key)
    } catch (_: Settings.SettingNotFoundException) {
        default
    }

    private fun isTorchOn(): Boolean = torchCameraId?.let { id ->
        try {
            cameraManager?.getCameraCharacteristics(id)
                ?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true && torchEnabled
        } catch (_: Exception) {
            false
        }
    } ?: false

    private var torchEnabled = false

    private val torchCameraId: String? by lazy {
        val manager = cameraManager ?: return@lazy null
        try {
            manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun onTileClicked(control: QuickControl) {
        when (control) {
            QuickControl.TORCH -> toggleTorch()
            QuickControl.AUTO_ROTATE -> toggleAutoRotate()
            QuickControl.AUTO_BRIGHTNESS -> toggleAutoBrightness()
            QuickControl.DND -> toggleDnd()
            QuickControl.RINGER -> toggleRinger()
        }
        // Toggling is asynchronous in the framework, so repaint on the next turn of the loop
        // rather than reading the old value straight away.
        Handler(Looper.getMainLooper()).postDelayed({ refresh() }, STATE_SETTLE_MS)
    }

    private fun toggleTorch() {
        val id = torchCameraId ?: return
        val manager = cameraManager ?: return
        try {
            manager.setTorchMode(id, !torchEnabled)
            torchEnabled = !torchEnabled
        } catch (_: Exception) {
            // The camera may be in use; leaving the tile unchanged is the honest outcome.
        }
    }

    private fun toggleAutoRotate() {
        if (!Settings.System.canWrite(context)) {
            requestWriteSettings()
            return
        }
        val next = if (
            settingInt(Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        ) {
            0
        } else {
            1
        }
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            next,
        )
    }

    private fun toggleAutoBrightness() {
        if (!Settings.System.canWrite(context)) {
            requestWriteSettings()
            return
        }
        val next = if (
            settingInt(Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        ) {
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        } else {
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        }
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            next,
        )
    }

    private fun toggleDnd() {
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val dndOn = notificationManager.currentInterruptionFilter !=
            NotificationManager.INTERRUPTION_FILTER_ALL
        val next = if (dndOn) {
            NotificationManager.INTERRUPTION_FILTER_ALL
        } else {
            NotificationManager.INTERRUPTION_FILTER_NONE
        }
        try {
            notificationManager.setInterruptionFilter(next)
        } catch (_: SecurityException) {
            // Access was revoked between the check and the call.
        }
    }

    private fun toggleRinger() {
        val next = QuickControlState.nextRingerMode(
            audioManager.ringerMode,
            notificationManager.isNotificationPolicyAccessGranted,
        )
        try {
            audioManager.ringerMode = next
        } catch (_: SecurityException) {
            // Without policy access only normal and vibrate are accepted.
        }
    }

    private fun requestWriteSettings() {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun iconFor(control: QuickControl): Int = when (control) {
        QuickControl.TORCH -> R.drawable.ic_quick_torch
        QuickControl.AUTO_ROTATE -> R.drawable.ic_quick_rotate
        QuickControl.AUTO_BRIGHTNESS -> R.drawable.ic_quick_brightness
        QuickControl.DND -> R.drawable.ic_quick_dnd
        QuickControl.RINGER -> R.drawable.ic_quick_ringer
    }

    private fun labelFor(control: QuickControl): String = when (control) {
        QuickControl.TORCH -> context.getString(R.string.sidebar_quick_torch)
        QuickControl.AUTO_ROTATE -> context.getString(R.string.sidebar_quick_rotate)
        QuickControl.AUTO_BRIGHTNESS -> context.getString(R.string.sidebar_quick_brightness)
        QuickControl.DND -> context.getString(R.string.sidebar_quick_dnd)
        QuickControl.RINGER -> context.getString(R.string.sidebar_quick_ringer)
    }

    private companion object {
        const val TILE_HEIGHT_DP = 56
        const val TILE_RADIUS_DP = 16
        const val TILE_GAP_DP = 6
        const val ICON_SIZE_DP = 22
        const val LABEL_TEXT_SIZE_SP = 10f
        const val STATE_SETTLE_MS = 150L
    }
}
