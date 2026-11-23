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
 * Arithmetic for the sidebar's brightness slider.
 *
 * Ported from the standalone Samart sidebar app (MIT), which drew a quick-settings style pill
 * slider in an overlay. The Android-facing part differs — the sidebar writes
 * `Settings.System.SCREEN_BRIGHTNESS` directly — but the number handling is the same shape and
 * is the part that is easy to get wrong, so it lives here without Android types.
 *
 * `SCREEN_BRIGHTNESS` is documented as 0..255 on most devices, but the range is a device
 * property rather than a constant. A device can report a maximum of zero or a maximum below
 * the minimum, and both would otherwise produce a divide-by-zero or an inverted slider.
 */
object BrightnessState {

    /** Brightness assumed when the system cannot report one. Matches the platform default. */
    const val DEFAULT_LEVEL = 127

    /** Widest level the framework is documented to use. */
    const val MAX_LEVEL = 255

    /** Smallest meaningful level; zero would turn the panel itself invisible. */
    const val MIN_LEVEL = 1

    /** The lowest level the slider is allowed to set. */
    fun clampLevel(level: Int): Int = level.coerceIn(MIN_LEVEL, MAX_LEVEL)

    /**
     * The slider position for a system brightness [level].
     *
     * A level outside the documented range is clamped rather than trusted, because some OEM
     * builds store larger values and the slider would then start off the end of its track.
     */
    fun toProgress(level: Int): Int = clampLevel(level)

    /** The system brightness to write for a slider [progress] position. */
    fun fromProgress(progress: Int): Int = clampLevel(progress)

    /**
     * Fill fraction of the slider between [min] and [max], in 0..1.
     *
     * Returns 0 when the reported range is degenerate (a maximum at or below the minimum), so
     * a device that refuses to report its range renders an empty track instead of crashing.
     */
    fun fraction(level: Int, min: Int, max: Int): Float {
        if (max <= min) return 0f
        return (level.coerceIn(min, max) - min).toFloat() / (max - min).toFloat()
    }

    /**
     * A short label for a brightness [level], as a percentage of [MAX_LEVEL].
     *
     * Rounded to nearest rather than truncated: 127 of 255 is 49.8%, and showing "49%" for
     * what is visually half brightness reads as an off-by-one bug to the user.
     */
    fun label(level: Int): String {
        val percent = (clampLevel(level) * 100 + MAX_LEVEL / 2) / MAX_LEVEL
        return "$percent%"
    }
}
