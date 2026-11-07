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
}
