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

import com.android.launcher3.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Guards the style/inset-panel pairing of the widget family. If a style ever stops producing its
 * own panel drawable the widgets would silently collapse to a single look.
 */
class NativeWidgetPreferencesTest {

    @Test
    fun insetBackgroundForStyle_eachStyleHasItsOwnPanel() {
        val filled = insetBackgroundForStyle(NativeWidgetPreferences.STYLE_FILLED)
        val transparent = insetBackgroundForStyle(NativeWidgetPreferences.STYLE_TRANSPARENT)
        val tonal = insetBackgroundForStyle(NativeWidgetPreferences.STYLE_TONAL)

        assertThat(filled).isEqualTo(R.drawable.widget_inset_background)
        assertThat(transparent).isEqualTo(R.drawable.widget_inset_background_transparent)
        assertThat(tonal).isEqualTo(R.drawable.widget_inset_background_tonal)
        assertThat(setOf(filled, transparent, tonal)).hasSize(3)
    }

    @Test
    fun insetBackgroundForStyle_unknownStyleFallsBackToFilled() {
        assertThat(insetBackgroundForStyle(Int.MIN_VALUE)).isEqualTo(R.drawable.widget_inset_background)
        assertThat(insetBackgroundForStyle(99)).isEqualTo(R.drawable.widget_inset_background)
    }

    @Test
    fun weatherLocation_roundTrips() {
        val encoded = WidgetFormat.encodeWeatherLocation(51.5072, -0.1276, "London")
        val parsed = WidgetFormat.parseWeatherLocation(encoded)
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.latitude).isWithin(0.0001).of(51.5072)
        assertThat(parsed.longitude).isWithin(0.0001).of(-0.1276)
        assertThat(parsed.label).isEqualTo("London")
    }

    @Test
    fun weatherLocation_rejectsMalformedInput() {
        assertThat(WidgetFormat.parseWeatherLocation(null)).isNull()
        assertThat(WidgetFormat.parseWeatherLocation("")).isNull()
        assertThat(WidgetFormat.parseWeatherLocation("51.5")).isNull()
        assertThat(WidgetFormat.parseWeatherLocation("not,numbers,London")).isNull()
        assertThat(WidgetFormat.parseWeatherLocation("51.5,-0.12,")).isNull()
    }

    @Test
    fun weatherSnapshot_roundTripsAndRejectsGarbage() {
        val snapshot = WidgetFormat.WeatherSnapshot(21, 3, 24, 15)
        val parsed = WidgetFormat.parseWeatherSnapshot(WidgetFormat.encodeWeatherSnapshot(snapshot))
        assertThat(parsed).isEqualTo(snapshot)

        assertThat(WidgetFormat.parseWeatherSnapshot(null)).isNull()
        assertThat(WidgetFormat.parseWeatherSnapshot("21,3,24")).isNull()
        assertThat(WidgetFormat.parseWeatherSnapshot("a,b,c,d")).isNull()
    }

    @Test
    fun hourHandDegrees_advancesSmoothlyWithMinutes() {
        assertThat(WidgetFormat.hourHandDegrees(0, 0)).isWithin(0.001f).of(0f)
        assertThat(WidgetFormat.hourHandDegrees(3, 0)).isWithin(0.001f).of(90f)
        assertThat(WidgetFormat.hourHandDegrees(6, 30)).isWithin(0.001f).of(195f)
        // Calendar.HOUR reports 0..11; 12 must fold back to 0 rather than overshoot.
        assertThat(WidgetFormat.hourHandDegrees(12, 0)).isWithin(0.001f).of(0f)
    }

    @Test
    fun minuteHandDegrees_coversFullCircle() {
        assertThat(WidgetFormat.minuteHandDegrees(0)).isWithin(0.001f).of(0f)
        assertThat(WidgetFormat.minuteHandDegrees(15)).isWithin(0.001f).of(90f)
        assertThat(WidgetFormat.minuteHandDegrees(59)).isWithin(0.001f).of(354f)
    }

    @Test
    fun formatEventWhen_labelsRelativeDays() {
        assertThat(WidgetFormat.formatEventWhen(0, "Fri", 14, 0, is24Hour = true)).isEqualTo("Now")
        assertThat(WidgetFormat.formatEventWhen(1, "Fri", 9, 30, is24Hour = true)).isEqualTo("Tomorrow \u00b7 09:30")
        assertThat(WidgetFormat.formatEventWhen(3, "Mon", 10, 5, is24Hour = true)).isEqualTo("Mon \u00b7 10:05")
        assertThat(WidgetFormat.formatEventWhen(2, "", 10, 5, is24Hour = true)).isEqualTo("In 2 days \u00b7 10:05")
    }

    @Test
    fun clockDigits_padsHourOnlyIn24HourMode() {
        assertThat(WidgetFormat.clockDigits(9, 5, is24Hour = true)).isEqualTo("09" to "05")
        assertThat(WidgetFormat.clockDigits(9, 5, is24Hour = false)).isEqualTo("9" to "05")
        assertThat(WidgetFormat.clockDigits(0, 0, is24Hour = false)).isEqualTo("12" to "00")
        assertThat(WidgetFormat.clockDigits(12, 30, is24Hour = false)).isEqualTo("12" to "30")
        assertThat(WidgetFormat.clockDigits(13, 30, is24Hour = false)).isEqualTo("1" to "30")
        assertThat(WidgetFormat.clockDigits(23, 59, is24Hour = true)).isEqualTo("23" to "59")
    }

    @Test
    fun clockPeriod_onlyForTwelveHourMode() {
        assertThat(WidgetFormat.clockPeriod(9, is24Hour = true)).isEmpty()
        assertThat(WidgetFormat.clockPeriod(9, is24Hour = false)).isEqualTo("AM")
        assertThat(WidgetFormat.clockPeriod(12, is24Hour = false)).isEqualTo("PM")
        assertThat(WidgetFormat.clockPeriod(0, is24Hour = false)).isEqualTo("AM")
        assertThat(WidgetFormat.clockPeriod(13, is24Hour = false)).isEqualTo("PM")
    }
}
