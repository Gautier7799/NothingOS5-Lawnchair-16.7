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
}
