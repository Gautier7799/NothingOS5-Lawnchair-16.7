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

package app.lawnchair.util

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Covers the temperature conversion shown in the weather widget.
 *
 * The conversion is a linear map anchored on each unit's freezing and boiling
 * point, so the interesting cases are the anchors themselves, a value that is
 * already in the requested unit, and the round trip back to the source unit.
 */
class TemperatureTest {

    @Test
    fun `converting to the same unit returns the value untouched`() {
        assertThat(Temperature(21, Temperature.Unit.Celsius).inUnit(Temperature.Unit.Celsius))
            .isEqualTo(21)
    }

    @Test
    fun `freezing and boiling anchors map across the scales`() {
        val freezing = Temperature(0, Temperature.Unit.Celsius)
        val boiling = Temperature(100, Temperature.Unit.Celsius)

        assertThat(freezing.inUnit(Temperature.Unit.Fahrenheit)).isEqualTo(32)
        assertThat(boiling.inUnit(Temperature.Unit.Fahrenheit)).isEqualTo(212)
        assertThat(freezing.inUnit(Temperature.Unit.Kelvin)).isEqualTo(273)
    }

    @Test
    fun `fahrenheit converts back to celsius`() {
        assertThat(Temperature(212, Temperature.Unit.Fahrenheit).inUnit(Temperature.Unit.Celsius))
            .isEqualTo(100)
    }

    @Test
    fun `conversion rounds to the nearest degree`() {
        // 37 °C is 98.6 °F, which must not truncate to 98.
        assertThat(Temperature(37, Temperature.Unit.Celsius).inUnit(Temperature.Unit.Fahrenheit))
            .isEqualTo(99)
    }

    @Test
    fun `round trip returns the original value`() {
        val original = 21
        val converted = Temperature(original, Temperature.Unit.Celsius)
            .inUnit(Temperature.Unit.Rakine)

        assertThat(Temperature(converted, Temperature.Unit.Rakine).inUnit(Temperature.Unit.Celsius))
            .isEqualTo(original)
    }

    @Test
    fun `unit strings round trip through their stored name`() {
        Temperature.Unit.entries.forEach { unit ->
            val name = Temperature.unitToString(unit)

            assertThat(Temperature.unitFromString(name)).isEqualTo(unit)
        }
    }

    @Test
    fun `metric maps to celsius and imperial maps to fahrenheit`() {
        assertThat(Temperature.unitFromString("metric")).isEqualTo(Temperature.Unit.Celsius)
        assertThat(Temperature.unitFromString("imperial")).isEqualTo(Temperature.Unit.Fahrenheit)
    }

    @Test
    fun `unknown unit name is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            Temperature.unitFromString("rankine-with-an-i")
        }
    }
}
