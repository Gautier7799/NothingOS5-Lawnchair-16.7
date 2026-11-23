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

/**
 * The audio streams the sidebar offers a volume slider for.
 *
 * Ported from the standalone "Samart sidebar" app (MIT), which draws four vertical sliders in
 * an overlay panel. Here the streams are plain data so the arithmetic can be tested on the JVM;
 * the stream ids match `AudioManager.STREAM_*` without importing Android.
 */
enum class VolumeStream(val streamId: Int) {
    MEDIA(3),
    RING(2),
    NOTIFICATION(5),
    ALARM(4),
}

/**
 * Slider arithmetic for the sidebar's volume rows.
 *
 * `SeekBar` counts in whole steps from zero to the stream maximum, while the icon and the
 * percentage label need a 0..1 fraction. Keeping the conversion here means the clamping and
 * the "is this row silent" rule can be tested without an `AudioManager`.
 */
object VolumeState {

    /**
     * Clamps a requested step into `0..max`.
     *
     * `max` is zero on a device that has no such stream (a tablet without a ringer, for
     * instance). Returning zero there keeps the slider at the start instead of throwing.
     */
    fun clampProgress(progress: Int, max: Int): Int {
        if (max <= 0) return 0
        return progress.coerceIn(0, max)
    }

    /**
     * Fraction of the stream's range that [progress] represents, in `0..1`.
     *
     * A stream whose maximum is not positive has no range to fill, so the result is zero.
     */
    fun fraction(progress: Int, max: Int): Float {
        if (max <= 0) return 0f
        return clampProgress(progress, max).toFloat() / max
    }

    /** Percentage to show next to the slider, rounded down so 100% means actually full. */
    fun percent(progress: Int, max: Int): Int = (fraction(progress, max) * 100).toInt()

    /** Whether the stream is muted, which is what dims its icon. */
    fun isSilent(progress: Int, max: Int): Boolean = clampProgress(progress, max) == 0

    /**
     * Seekbar maximum for a stream.
     *
     * `SeekBar` treats a maximum of zero as "no progress possible" and still lays out, but
     * reporting one step keeps the thumb visible instead of pinned flat against the track.
     */
    fun seekBarMax(max: Int): Int = if (max <= 0) 1 else max

    /** Streams in display order, top to bottom. */
    val ordered: List<VolumeStream> get() = VolumeStream.values().toList()
}
