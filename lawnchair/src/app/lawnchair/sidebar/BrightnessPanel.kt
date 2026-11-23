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

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import app.lawnchair.allapps.caddy.CaddyMaterialYou
import com.android.launcher3.R

/**
 * Horizontal brightness slider shown at the top of the sidebar.
 *
 * Ported from the standalone Samart sidebar app (MIT), which drew a quick-settings style pill
 * in an overlay window. The overlay is not needed here because the panel is already on screen,
 * so this is an ordinary child view.
 *
 * Writing `SCREEN_BRIGHTNESS` needs `WRITE_SETTINGS`, which is a special access the user grants
 * in system settings. Without it the slider still renders and still reflects the current
 * brightness, so the panel never looks broken; the tap that tries to change it opens the grant
 * screen instead.
 */
class BrightnessPanel(private val context: Context) {

    /**
     * Builds the slider, or returns null when the device reports no usable range.
     *
     * A null result tells the caller not to reserve a row for it, which keeps the panel height
     * arithmetic honest on a device that cannot report its brightness.
     */
    fun build(): LinearLayout? {
        val current = currentLevel()
        val maximum = maximumLevel()
        if (maximum <= BrightnessState.MIN_LEVEL) return null

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }

        val title = TextView(context).apply {
            text = context.getString(R.string.sidebar_brightness_label)
            setTextColor(CaddyMaterialYou.onSurfaceVariant(context))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
        }
        row.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { rightMargin = dp(8) },
        )

        val value = TextView(context).apply {
            text = BrightnessState.label(current)
            setTextColor(CaddyMaterialYou.onSurfaceVariant(context))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
        }

        val bar = SeekBar(context).apply {
            max = BrightnessState.MAX_LEVEL
            progress = BrightnessState.toProgress(current)
            progressTintList = android.content.res.ColorStateList.valueOf(
                CaddyMaterialYou.accent(context),
            )
            thumbTintList = android.content.res.ColorStateList.valueOf(
                CaddyMaterialYou.accent(context),
            )
            background = GradientDrawable().apply {
                setColor(CaddyMaterialYou.surface(context))
                cornerRadius = dp(SidebarMetrics.TILE_RADIUS_DP).toFloat()
            }
        }
        row.addView(bar, LinearLayout.LayoutParams(0, dp(28), 1f))
        row.addView(
            value,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { leftMargin = dp(8) },
        )

        // Only commit on release: writing Settings on every intermediate step of a drag would
        // hammer the settings provider and make the slider feel sticky.
        bar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    value.text = BrightnessState.label(progress)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val level = BrightnessState.fromProgress(seekBar?.progress ?: return)
                    if (!writeLevel(level)) requestWriteSettings()
                }
            },
        )
        return row
    }

    private fun currentLevel(): Int = try {
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
        )
    } catch (_: Settings.SettingNotFoundException) {
        BrightnessState.DEFAULT_LEVEL
    }

    /**
     * Highest level the device accepts, read from its own setting.
     *
     * The framework documents 0..255 but does not guarantee it, and reading the device value
     * keeps the slider's ends matching what the hardware actually does.
     */
    private fun maximumLevel(): Int = try {
        Settings.System.getInt(context.contentResolver, "screen_brightness_max")
    } catch (_: Exception) {
        BrightnessState.MAX_LEVEL
    }

    /**
     * Writes [level] to the system, returning false when the write is not permitted.
     *
     * The mode is forced to manual first: writing a level while adaptive brightness is on has
     * no visible effect, which would look like the slider is broken.
     */
    private fun writeLevel(level: Int): Boolean {
        if (!Settings.System.canWrite(context)) return false
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                level,
            )
        } catch (_: Exception) {
            false
        }
    }

    private fun requestWriteSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
