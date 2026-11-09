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

import java.util.Locale

/**
 * Pure, framework-free formatting helpers shared by the native Lawnchair widgets.
 *
 * Everything here is deterministic and side-effect free so it can be unit tested on the JVM
 * without an Android runtime.
 */
object WidgetFormat {

    /** How a battery reading should be presented. */
    enum class BatteryTone { NORMAL, LOW, CRITICAL, CHARGING }

    /** Do Not Disturb presentation state. */
    enum class DndState { ACCESS_REQUIRED, OFF, PRIORITY, ALARMS, ALL }

    /** The kind of device the audio is currently routed to. */
    enum class AudioOutput { BLUETOOTH, HEADSET, USB, SPEAKER }

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

    /** Formats a battery temperature in degrees Celsius with one decimal place. */
    fun formatTemperature(celsius: Float): String = String.format(Locale.US, "%.1f\u00b0C", celsius)

    /** Formats a battery voltage with two decimal places. */
    fun formatVoltage(volts: Float): String = String.format(Locale.US, "%.2fV", volts)

    /** A resolved weather location: coordinates plus the human label to display. */
    data class WeatherLocation(val latitude: Double, val longitude: Double, val label: String)

    /** Encodes a [WeatherLocation] for storage in [NativeWidgetPreferences]. */
    fun encodeWeatherLocation(latitude: Double, longitude: Double, label: String): String = "$latitude,$longitude,$label"

    /**
     * Parses the stored `latitude,longitude,label` form back into a [WeatherLocation].
     *
     * Returns null when the value is missing or malformed so callers can fall back to the
     * "tap to configure" state instead of crashing.
     */
    fun parseWeatherLocation(raw: String?): WeatherLocation? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(",", limit = 3)
        if (parts.size < 3) return null
        val latitude = parts[0].toDoubleOrNull() ?: return null
        val longitude = parts[1].toDoubleOrNull() ?: return null
        val label = parts[2].trim()
        if (label.isEmpty()) return null
        return WeatherLocation(latitude, longitude, label)
    }

    /** Rounded temperature label, e.g. `21\u00b0`. */
    fun formatTemperature(celsius: Int): String = "$celsius\u00b0"

    /** Today's high/low label, e.g. `H 24\u00b0 \u00b7 L 15\u00b0`. */
    fun formatHighLow(high: Int, low: Int): String = "H $high\u00b0 \u00b7 L $low\u00b0"

    /**
     * Rotation of the hour hand in degrees.
     *
     * [hour] is the 12-hour value (0..11 for `Calendar.HOUR`, 12 is treated as 0). The hand advances
     * smoothly with the minutes so it never sits exactly on the hour mark.
     */
    fun hourHandDegrees(hour: Int, minute: Int): Float {
        val h = ((hour % 12) + 12) % 12
        return h * 30f + minute.coerceIn(0, 59) * 0.5f
    }

    /** Rotation of the minute hand in degrees. */
    fun minuteHandDegrees(minute: Int): Float = minute.coerceIn(0, 59) * 6f

    /**
     * Human label for when an upcoming event starts, e.g. `Today 14:00`, `Tomorrow 09:30` or
     * `Fri 10:00`.
     */
    fun formatEventWhen(dayOffset: Int, weekday: String, hour: Int, minute: Int, is24Hour: Boolean): String {
        val day = when {
            dayOffset <= 0 -> "Now"
            dayOffset == 1 -> "Tomorrow"
            else -> weekday.ifBlank { "In $dayOffset days" }
        }
        if (dayOffset <= 0) return day
        return "$day \u00b7 ${formatTime(hour, minute, is24Hour = is24Hour)}"
    }

    /** A cached weather reading, encoded compactly so it survives process death. */
    data class WeatherSnapshot(val temperatureC: Int, val weatherCode: Int, val highC: Int, val lowC: Int)

    fun encodeWeatherSnapshot(snapshot: WeatherSnapshot): String = "${snapshot.temperatureC},${snapshot.weatherCode},${snapshot.highC},${snapshot.lowC}"

    fun parseWeatherSnapshot(raw: String?): WeatherSnapshot? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(",")
        if (parts.size != 4) return null
        val values = parts.map { it.trim().toIntOrNull() ?: return null }
        return WeatherSnapshot(values[0], values[1], values[2], values[3])
    }

    /**
     * Classifies the current audio output from the set of connected output device types.
     *
     * Bluetooth takes precedence over wired headsets, which take precedence over USB, and a plain
     * speaker is the fallback when nothing else is connected.
     */
    fun classifyOutput(deviceTypes: List<Int>): AudioOutput = when {
        deviceTypes.isEmpty() -> AudioOutput.SPEAKER

        // AudioDeviceInfo.TYPE_BLUETOOTH_A2DP (8) / TYPE_BLUETOOTH_SCO (7)
        deviceTypes.any { it == 8 || it == 7 } -> AudioOutput.BLUETOOTH

        // TYPE_WIRED_HEADSET (3) / TYPE_WIRED_HEADPHONES (4)
        deviceTypes.any { it == 3 || it == 4 } -> AudioOutput.HEADSET

        // TYPE_USB_DEVICE (11) / TYPE_USB_HEADSET (22) / TYPE_USB_ACCESSORY (12)
        deviceTypes.any { it == 11 || it == 22 || it == 12 } -> AudioOutput.USB

        else -> AudioOutput.SPEAKER
    }

    /**
     * Maps the notification-policy access flag and the current interruption filter to a
     * presentation state.
     *
     * @param accessGranted whether notification-policy access has been granted
     * @param interruptionFilter a `NotificationManager.INTERRUPTION_FILTER_*` value
     */
    fun dndState(accessGranted: Boolean, interruptionFilter: Int): DndState = when {
        !accessGranted -> DndState.ACCESS_REQUIRED

        // INTERRUPTION_FILTER_ALL == 1 means DND is off.
        interruptionFilter == 1 -> DndState.OFF

        // INTERRUPTION_FILTER_PRIORITY == 2
        interruptionFilter == 2 -> DndState.PRIORITY

        // INTERRUPTION_FILTER_ALARMS == 4
        interruptionFilter == 4 -> DndState.ALARMS

        // INTERRUPTION_FILTER_NONE == 3 (total silence)
        interruptionFilter == 3 -> DndState.ALL

        else -> DndState.OFF
    }

    /**
     * Number of calendar days between two day-of-year values, tolerating a year boundary.
     *
     * Returns 0 for today, 1 for tomorrow and so on.
     */
    fun dayOffset(nowYear: Int, nowDayOfYear: Int, targetYear: Int, targetDayOfYear: Int): Int = when {
        targetYear > nowYear -> (if (isLeapYear(nowYear)) 366 else 365) - nowDayOfYear + targetDayOfYear
        targetYear < nowYear -> -((if (isLeapYear(targetYear)) 366 else 365) - targetDayOfYear + nowDayOfYear)
        else -> targetDayOfYear - nowDayOfYear
    }

    /** Human label for an alarm that fires [dayOffset] days from now. */
    fun formatAlarmDay(dayOffset: Int, weekday: String, label: String?): String {
        val day = when (dayOffset) {
            0 -> "Today"
            1 -> "Tomorrow"
            else -> weekday.ifBlank { "In $dayOffset days" }
        }
        return if (label.isNullOrBlank()) day else "$day \u00b7 $label"
    }

    private fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

    /**
     * Label for a step count, grouped with thin spaces so five-digit values stay readable.
     * Negative input is treated as zero.
     */
    fun stepCountLabel(steps: Int): String {
        val value = steps.coerceAtLeast(0)
        val digits = value.toString()
        return digits.reversed().chunked(3).joinToString("\u2009").reversed()
    }

    /** Progress toward a step goal as a 0..100 percentage. A non-positive goal yields 0. */
    fun stepGoalPercent(steps: Int, goal: Int): Int {
        if (goal <= 0) return 0
        return (steps.coerceAtLeast(0) * 100 / goal).coerceIn(0, 100)
    }

    /** A step goal label, e.g. `7,320 / 10,000 steps`. */
    fun stepGoalLabel(steps: Int, goal: Int): String = "${stepCountLabel(steps)} / ${stepCountLabel(goal)} steps"

    /**
     * A battery percentage for an accessory, or an em dash when the level is unknown.
     *
     * Earbuds and other accessories frequently report no level at all, so the widget must have a
     * neutral representation instead of a misleading `0%`.
     */
    fun accessoryBatteryLabel(level: Int?): String = if (level == null) "\u2014" else "${level.coerceIn(0, 100)}%"

    /**
     * A now-playing line, degrading gracefully when the media session has partial metadata.
     *
     * Falls back to a placeholder when both fields are missing so the widget never renders an
     * empty line.
     */
    fun trackLabel(title: String?, artist: String?, fallback: String): String {
        val cleanTitle = title?.trim().orEmpty()
        val cleanArtist = artist?.trim().orEmpty()
        return when {
            cleanTitle.isNotEmpty() && cleanArtist.isNotEmpty() -> "$cleanTitle \u00b7 $cleanArtist"
            cleanTitle.isNotEmpty() -> cleanTitle
            cleanArtist.isNotEmpty() -> cleanArtist
            else -> fallback
        }
    }

    /** Formats a playback position/duration pair as `m:ss` (or `h:mm:ss` past an hour). */
    fun playbackPosition(positionMs: Long, durationMs: Long): String {
        val totalSeconds = (positionMs.coerceAtLeast(0) / 1000).toInt()
        val durationSeconds = (durationMs.coerceAtLeast(0) / 1000).toInt()
        return "${clock(totalSeconds)} / ${clock(durationSeconds)}"
    }

    private fun clock(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "$hours:${two(minutes)}:${two(seconds)}" else "$minutes:${two(seconds)}"
    }

    /**
     * A human label for how far away an upcoming observance is, e.g. `Today`, `Tomorrow` or
     * `in 12 days`. A non-positive [daysUntil] reads as `Today`.
     */
    fun formatObservanceWhen(daysUntil: Int): String = when {
        daysUntil <= 0 -> "Today"
        daysUntil == 1 -> "Tomorrow"
        else -> "in $daysUntil days"
    }

    private val MONTHS = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
}
