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

/** Covers the WMO code to visual-category mapping used by the weather widget. */
class WeatherConditionsTest {

    @Test
    fun kindOf_mapsRepresentativeCodes() {
        assertThat(WeatherConditions.kindOf(0)).isEqualTo(WeatherKind.CLEAR)
        assertThat(WeatherConditions.kindOf(1)).isEqualTo(WeatherKind.PARTLY_CLOUDY)
        assertThat(WeatherConditions.kindOf(2)).isEqualTo(WeatherKind.PARTLY_CLOUDY)
        assertThat(WeatherConditions.kindOf(3)).isEqualTo(WeatherKind.CLOUDY)
        assertThat(WeatherConditions.kindOf(45)).isEqualTo(WeatherKind.FOG)
        assertThat(WeatherConditions.kindOf(53)).isEqualTo(WeatherKind.DRIZZLE)
        assertThat(WeatherConditions.kindOf(63)).isEqualTo(WeatherKind.RAIN)
        assertThat(WeatherConditions.kindOf(80)).isEqualTo(WeatherKind.RAIN)
        assertThat(WeatherConditions.kindOf(73)).isEqualTo(WeatherKind.SNOW)
        assertThat(WeatherConditions.kindOf(86)).isEqualTo(WeatherKind.SNOW)
        assertThat(WeatherConditions.kindOf(95)).isEqualTo(WeatherKind.THUNDER)
    }

    @Test
    fun kindOf_unknownCodeFallsBackToUnknown() {
        assertThat(WeatherConditions.kindOf(-1)).isEqualTo(WeatherKind.UNKNOWN)
        assertThat(WeatherConditions.kindOf(7)).isEqualTo(WeatherKind.UNKNOWN)
    }
}
