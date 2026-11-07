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

/**
 * Pure, framework-free formatting helpers shared by the native Lawnchair widgets.
 *
 * Everything here is deterministic and side-effect free so it can be unit tested on the JVM
 * without an Android runtime.
 */
object WidgetFormat {

    /** How a battery reading should be presented. */
    enum class BatteryTone { NORMAL, LOW, CRITICAL, CHARGING }

    private const val LOW_THRESHOLD = 30
    private const val CRITICAL_THRESHOLD = 15

    /**
     * Formats a wall-clock time.
     *
     * @param hour 0..23 (24-hour clock value)
     * @param minute 0..59
     * @param second 0..59, only used when [showSeconds] is true
     * @param is24Hour whether to render a 24-hour or 12-hour representation
     * @param showSeconds whether to append seconds
     */
    fun formatTime(
        hour: Int,
        minute: Int,
        second: Int = 0,
        is24Hour: Boolean = true,
        showSeconds: Boolean = false,
    ): String {
        val safeHour = ((hour % 24) + 24) % 24
        val safeMinute = minute.coerceIn(0, 59)
        val safeSecond = second.coerceIn(0, 59)
        val suffix = if (showSeconds) ":${two(safeSecond)}" else ""
        if (is24Hour) {
            return "${two(safeHour)}:${two(safeMinute)}$suffix"
        }
        val displayHour = when (safeHour % 12) {
            0 -> 12
            else -> safeHour % 12
        }
        val period = if (safeHour < 12) "AM" else "PM"
        return "$displayHour:${two(safeMinute)}$suffix $period"
    }

    /** Formats a `day month` style short date, e.g. `3 Mar`. */
    fun formatShortDate(dayOfMonth: Int, month: Int, weekday: String? = null): String {
        val safeDay = dayOfMonth.coerceIn(1, 31)
        val monthName = MONTHS[((month - 1) % 12 + 12) % 12]
        return if (weekday.isNullOrBlank()) {
            "$safeDay $monthName"
        } else {
            "$weekday, $safeDay $monthName"
        }
    }

    /**
     * Formats a byte count using binary (1024-based) units with a single decimal place for
     * values below 10 in the chosen unit.
     */
    fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "\u2014"
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB", "PB")
        var value = bytes.toDouble()
        var unitIndex = -1
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex++
        }
        val rounded = if (value < 10) {
            String.format("%.1f", value)
        } else {
            value.toLong().toString()
        }
        return "$rounded ${units[unitIndex]}"
    }

    /** Fraction (0f..1f) of [total] that is in use. Returns 0f for nonsensical inputs. */
    fun usedFraction(used: Long, total: Long): Float {
        if (total <= 0L) return 0f
        return (used.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat()
    }

    /** Percentage label for a 0..100 battery level, clamping out-of-range input. */
    fun batteryLevelLabel(level: Int): String = "${level.coerceIn(0, 100)}%"

    /** Classifies a battery reading so the UI can pick an appropriate accent color. */
    fun batteryTone(level: Int, charging: Boolean): BatteryTone = when {
        charging -> BatteryTone.CHARGING
        level <= CRITICAL_THRESHOLD -> BatteryTone.CRITICAL
        level <= LOW_THRESHOLD -> BatteryTone.LOW
        else -> BatteryTone.NORMAL
    }

    private fun two(value: Int): String = if (value < 10) "0$value" else value.toString()

    private val MONTHS = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
}
