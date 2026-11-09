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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WidgetFormatTest {

    @Test
    fun formatTime_24Hour_padsToTwoDigits() {
        assertThat(WidgetFormat.formatTime(9, 5, is24Hour = true)).isEqualTo("09:05")
        assertThat(WidgetFormat.formatTime(23, 59, is24Hour = true)).isEqualTo("23:59")
        assertThat(WidgetFormat.formatTime(0, 0, is24Hour = true)).isEqualTo("00:00")
    }

    @Test
    fun formatTime_12Hour_usesTwelveHourClockAndPeriod() {
        assertThat(WidgetFormat.formatTime(0, 0, is24Hour = false)).isEqualTo("12:00 AM")
        assertThat(WidgetFormat.formatTime(12, 0, is24Hour = false)).isEqualTo("12:00 PM")
        assertThat(WidgetFormat.formatTime(13, 7, is24Hour = false)).isEqualTo("1:07 PM")
        assertThat(WidgetFormat.formatTime(23, 59, is24Hour = false)).isEqualTo("11:59 PM")
    }

    @Test
    fun formatTime_showSeconds_appendsSeconds() {
        assertThat(WidgetFormat.formatTime(7, 8, second = 9, is24Hour = true, showSeconds = true))
            .isEqualTo("07:08:09")
    }

    @Test
    fun formatTime_clampsOutOfRangeMinutesAndSeconds() {
        assertThat(WidgetFormat.formatTime(10, 99, second = 75, is24Hour = true, showSeconds = true))
            .isEqualTo("10:59:59")
    }

    @Test
    fun formatShortDate_withoutWeekday() {
        assertThat(WidgetFormat.formatShortDate(3, 3)).isEqualTo("3 Mar")
        assertThat(WidgetFormat.formatShortDate(31, 12)).isEqualTo("31 Dec")
    }

    @Test
    fun formatShortDate_withWeekday() {
        assertThat(WidgetFormat.formatShortDate(1, 1, weekday = "Mon")).isEqualTo("Mon, 1 Jan")
    }

    @Test
    fun formatBytes_scalesAcrossUnits() {
        assertThat(WidgetFormat.formatBytes(0)).isEqualTo("0 B")
        assertThat(WidgetFormat.formatBytes(512)).isEqualTo("512 B")
        assertThat(WidgetFormat.formatBytes(1024)).isEqualTo("1.0 KB")
        assertThat(WidgetFormat.formatBytes(1536)).isEqualTo("1.5 KB")
        assertThat(WidgetFormat.formatBytes(1024L * 1024L * 10)).isEqualTo("10 MB")
        assertThat(WidgetFormat.formatBytes(1024L * 1024L * 1024L * 2)).isEqualTo("2.0 GB")
    }

    @Test
    fun formatBytes_negative_isPlaceholder() {
        assertThat(WidgetFormat.formatBytes(-1)).isEqualTo("\u2014")
    }

    @Test
    fun usedFraction_handlesEdges() {
        assertThat(WidgetFormat.usedFraction(50, 100)).isEqualTo(0.5f)
        assertThat(WidgetFormat.usedFraction(0, 0)).isEqualTo(0f)
        assertThat(WidgetFormat.usedFraction(200, 100)).isEqualTo(1f)
    }

    @Test
    fun batteryLevelLabel_clampsToValidRange() {
        assertThat(WidgetFormat.batteryLevelLabel(72)).isEqualTo("72%")
        assertThat(WidgetFormat.batteryLevelLabel(150)).isEqualTo("100%")
        assertThat(WidgetFormat.batteryLevelLabel(-5)).isEqualTo("0%")
    }

    @Test
    fun batteryTone_classifiesLevels() {
        assertThat(WidgetFormat.batteryTone(80, charging = false)).isEqualTo(WidgetFormat.BatteryTone.NORMAL)
        assertThat(WidgetFormat.batteryTone(20, charging = false)).isEqualTo(WidgetFormat.BatteryTone.LOW)
        assertThat(WidgetFormat.batteryTone(10, charging = false)).isEqualTo(WidgetFormat.BatteryTone.CRITICAL)
        assertThat(WidgetFormat.batteryTone(10, charging = true)).isEqualTo(WidgetFormat.BatteryTone.CHARGING)
        assertThat(WidgetFormat.batteryTone(90, charging = true)).isEqualTo(WidgetFormat.BatteryTone.CHARGING)
    }

    @Test
    fun formatTemperature_oneDecimalWithUnit() {
        assertThat(WidgetFormat.formatTemperature(31.0f)).isEqualTo("31.0\u00b0C")
        assertThat(WidgetFormat.formatTemperature(27.5f)).isEqualTo("27.5\u00b0C")
    }

    @Test
    fun formatVoltage_twoDecimalsWithUnit() {
        assertThat(WidgetFormat.formatVoltage(4.2f)).isEqualTo("4.20V")
        assertThat(WidgetFormat.formatVoltage(3.851f)).isEqualTo("3.85V")
    }

    @Test
    fun classifyOutput_prefersBluetoothThenHeadsetThenUsb() {
        assertThat(WidgetFormat.classifyOutput(emptyList())).isEqualTo(WidgetFormat.AudioOutput.SPEAKER)
        assertThat(WidgetFormat.classifyOutput(listOf(2))).isEqualTo(WidgetFormat.AudioOutput.SPEAKER)
        assertThat(WidgetFormat.classifyOutput(listOf(8))).isEqualTo(WidgetFormat.AudioOutput.BLUETOOTH)
        assertThat(WidgetFormat.classifyOutput(listOf(3))).isEqualTo(WidgetFormat.AudioOutput.HEADSET)
        assertThat(WidgetFormat.classifyOutput(listOf(11))).isEqualTo(WidgetFormat.AudioOutput.USB)
        // Bluetooth wins even when a headset is also connected.
        assertThat(WidgetFormat.classifyOutput(listOf(3, 8))).isEqualTo(WidgetFormat.AudioOutput.BLUETOOTH)
    }

    @Test
    fun dndState_mapsAccessAndFilter() {
        assertThat(WidgetFormat.dndState(accessGranted = false, interruptionFilter = 1))
            .isEqualTo(WidgetFormat.DndState.ACCESS_REQUIRED)
        assertThat(WidgetFormat.dndState(accessGranted = true, interruptionFilter = 1))
            .isEqualTo(WidgetFormat.DndState.OFF)
        assertThat(WidgetFormat.dndState(accessGranted = true, interruptionFilter = 2))
            .isEqualTo(WidgetFormat.DndState.PRIORITY)
        assertThat(WidgetFormat.dndState(accessGranted = true, interruptionFilter = 4))
            .isEqualTo(WidgetFormat.DndState.ALARMS)
        assertThat(WidgetFormat.dndState(accessGranted = true, interruptionFilter = 3))
            .isEqualTo(WidgetFormat.DndState.ALL)
        assertThat(WidgetFormat.dndState(accessGranted = true, interruptionFilter = 0))
            .isEqualTo(WidgetFormat.DndState.OFF)
    }

    @Test
    fun dayOffset_handlesSameYearAndYearBoundary() {
        assertThat(WidgetFormat.dayOffset(2026, 100, 2026, 100)).isEqualTo(0)
        assertThat(WidgetFormat.dayOffset(2026, 100, 2026, 101)).isEqualTo(1)
        // Dec 31 (day 365 in a non-leap year) -> Jan 1 next year is tomorrow.
        assertThat(WidgetFormat.dayOffset(2026, 365, 2027, 1)).isEqualTo(1)
        // Leap year: Dec 31 is day 366.
        assertThat(WidgetFormat.dayOffset(2028, 366, 2029, 1)).isEqualTo(1)
    }

    @Test
    fun formatAlarmDay_labelsRelativeDaysAndLabel() {
        assertThat(WidgetFormat.formatAlarmDay(0, "Mon", null)).isEqualTo("Today")
        assertThat(WidgetFormat.formatAlarmDay(1, "Tue", null)).isEqualTo("Tomorrow")
        assertThat(WidgetFormat.formatAlarmDay(3, "Wed", null)).isEqualTo("Wed")
        assertThat(WidgetFormat.formatAlarmDay(3, "Wed", "Work")).isEqualTo("Wed \u00b7 Work")
        assertThat(WidgetFormat.formatAlarmDay(0, "Mon", "Wake")).isEqualTo("Today \u00b7 Wake")
    }

    @Test
    fun stepCountLabel_groupsThousandsWithThinSpaces() {
        assertThat(WidgetFormat.stepCountLabel(0)).isEqualTo("0")
        assertThat(WidgetFormat.stepCountLabel(999)).isEqualTo("999")
        assertThat(WidgetFormat.stepCountLabel(1000)).isEqualTo("1\u2009000")
        assertThat(WidgetFormat.stepCountLabel(12345)).isEqualTo("12\u2009345")
    }

    @Test
    fun stepCountLabel_clampsNegativeToZero() {
        assertThat(WidgetFormat.stepCountLabel(-5)).isEqualTo("0")
    }

    @Test
    fun stepGoalPercent_isClampedAndGuardsZeroGoal() {
        assertThat(WidgetFormat.stepGoalPercent(5000, 10000)).isEqualTo(50)
        assertThat(WidgetFormat.stepGoalPercent(20000, 10000)).isEqualTo(100)
        assertThat(WidgetFormat.stepGoalPercent(100, 0)).isEqualTo(0)
        assertThat(WidgetFormat.stepGoalPercent(-10, 10000)).isEqualTo(0)
    }

    @Test
    fun accessoryBatteryLabel_usesEmDashWhenUnknown() {
        assertThat(WidgetFormat.accessoryBatteryLabel(null)).isEqualTo("\u2014")
        assertThat(WidgetFormat.accessoryBatteryLabel(80)).isEqualTo("80%")
        assertThat(WidgetFormat.accessoryBatteryLabel(150)).isEqualTo("100%")
        assertThat(WidgetFormat.accessoryBatteryLabel(-1)).isEqualTo("0%")
    }

    @Test
    fun trackLabel_joinsTitleAndArtistAndFallsBack() {
        assertThat(WidgetFormat.trackLabel("Song", "Artist", "Nothing")).isEqualTo("Song \u00b7 Artist")
        assertThat(WidgetFormat.trackLabel("Song", null, "Nothing")).isEqualTo("Song")
        assertThat(WidgetFormat.trackLabel("  ", "Artist", "Nothing")).isEqualTo("Artist")
        assertThat(WidgetFormat.trackLabel(null, null, "Nothing")).isEqualTo("Nothing")
    }

    @Test
    fun playbackPosition_formatsMinutesAndHours() {
        assertThat(WidgetFormat.playbackPosition(65000, 215000)).isEqualTo("1:05 / 3:35")
        assertThat(WidgetFormat.playbackPosition(3661000, 7200000)).isEqualTo("1:01:01 / 2:00:00")
        assertThat(WidgetFormat.playbackPosition(-1, -1)).isEqualTo("0:00 / 0:00")
    }

    @Test
    fun formatObservanceWhen_labelsRelativeDays() {
        assertThat(WidgetFormat.formatObservanceWhen(0)).isEqualTo("Today")
        assertThat(WidgetFormat.formatObservanceWhen(1)).isEqualTo("Tomorrow")
        assertThat(WidgetFormat.formatObservanceWhen(12)).isEqualTo("in 12 days")
    }
}
