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

    private val MONTHS = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
}
