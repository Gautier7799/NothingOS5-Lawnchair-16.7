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
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.AudioManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import app.lawnchair.allapps.caddy.CaddyMaterialYou

/**
 * The volume rows the sidebar can slide over its app column.
 *
 * Ported from the standalone "Samart sidebar" app (MIT), where four 220 dp overlay panels hold
 * rotated `SeekBar`s. Here the rows live inside the sidebar panel, so they need no overlay
 * window: the panel is already on screen and already has the launcher's colour context.
 *
 * Each row writes straight through `AudioManager.setStreamVolume`, which needs no permission
 * for the four media-style streams. A device that has no such stream reports a maximum of
 * zero, and [VolumeState] keeps that from dividing by it.
 */
class VolumePanel(private val context: Context) {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val rows = mutableListOf<Row>()

    /** Root view holding one row per stream. */
    var view: View? = null
        private set

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        context.resources.displayMetrics,
    ).toInt()

    /** Builds the rows for [streams], or null when the device has none of them. */
    fun build(streams: List<VolumeStream>): View? {
        rows.clear()
        val usable = streams.filter { audioManager.getStreamMaxVolume(it.streamId) > 0 }
        if (usable.isEmpty()) {
            view = null
            return null
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(ROW_GAP_DP), 0, dp(ROW_GAP_DP))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        usable.forEach { stream ->
            val row = Row(stream)
            rows += row
            root.addView(
                row.root,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
        }

        refresh()
        view = root
        return root
    }

    /** Re-reads every stream from the audio service and repaints the rows. */
    fun refresh() {
        rows.forEach { it.refresh() }
    }

    /**
     * One stream: an icon, a vertical slider and a percentage.
     *
     * The slider is a `SeekBar` rotated a quarter turn, which is how the Samart panel gets a
     * vertical control without a custom view.
     */
    private inner class Row(val stream: VolumeStream) {

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }

        private val icon = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, ICON_TEXT_SIZE_SP)
            gravity = Gravity.CENTER
            setTextColor(CaddyMaterialYou.onSurfaceVariant(context))
            layoutParams = LinearLayout.LayoutParams(dp(ICON_BOX_DP), dp(ICON_BOX_DP))
        }

        private val seekBar = SeekBar(context).apply {
            rotation = -90f
            splitTrack = false
            thumb = null
            layoutParams = FrameLayout.LayoutParams(
                dp(SLIDER_LENGTH_DP),
                dp(SLIDER_THICKNESS_DP),
                Gravity.CENTER,
            )
            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        bar: SeekBar,
                        progress: Int,
                        fromUser: Boolean,
                    ) {
                        if (fromUser) {
                            audioManager.setStreamVolume(stream.streamId, progress, 0)
                        }
                        label.text = percentText(progress)
                    }

                    override fun onStartTrackingTouch(bar: SeekBar) = Unit

                    override fun onStopTrackingTouch(bar: SeekBar) = Unit
                },
            )
        }

        private val label = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, PERCENT_TEXT_SIZE_SP)
            gravity = Gravity.CENTER
            setTextColor(CaddyMaterialYou.onSurfaceVariant(context))
        }

        private val sliderFrame = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                dp(SLIDER_THICKNESS_DP),
                dp(SLIDER_LENGTH_DP),
            ).apply { topMargin = dp(ROW_GAP_DP) }
        }

        init {
            icon.text = iconText()
            sliderFrame.addView(seekBar)
            root.addView(icon)
            root.addView(sliderFrame)
            root.addView(label)
        }

        fun refresh() {
            val max = audioManager.getStreamMaxVolume(stream.streamId)
            val current = audioManager.getStreamVolume(stream.streamId)
            seekBar.max = VolumeState.seekBarMax(max)
            seekBar.progress = VolumeState.clampProgress(current, max)
            applyTrackColors(VolumeState.isSilent(current, max))
            label.text = percentText(current)
        }

        private fun percentText(progress: Int): String {
            val max = audioManager.getStreamMaxVolume(stream.streamId)
            return "${VolumeState.percent(progress, max)}%"
        }

        private fun applyTrackColors(silent: Boolean) {
            val active = if (silent) {
                CaddyMaterialYou.outlineVariant(context)
            } else {
                CaddyMaterialYou.accent(context)
            }
            val track = CaddyMaterialYou.surfaceContainer(context)
            val radius = dp(SLIDER_THICKNESS_DP) / 2f
            val background = GradientDrawable().apply {
                setColor(track)
                cornerRadius = radius
            }
            val fill = GradientDrawable().apply {
                setColor(active)
                cornerRadius = radius
            }
            seekBar.progressDrawable = LayerDrawable(
                arrayOf(
                    background,
                    ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL),
                ),
            ).apply {
                setId(0, android.R.id.background)
                setId(1, android.R.id.progress)
            }
            icon.setTextColor(if (silent) CaddyMaterialYou.outlineVariant(context) else active)
        }

        private fun iconText(): String = when (stream) {
            VolumeStream.MEDIA -> "♪"
            VolumeStream.RING -> "☎"
            VolumeStream.NOTIFICATION -> "✉"
            VolumeStream.ALARM -> "⏰"
        }
    }

    private companion object {
        const val SLIDER_LENGTH_DP = 120
        const val SLIDER_THICKNESS_DP = 32
        const val ICON_BOX_DP = 32
        const val ROW_GAP_DP = 6
        const val ICON_TEXT_SIZE_SP = 16f
        const val PERCENT_TEXT_SIZE_SP = 10f
    }
}
