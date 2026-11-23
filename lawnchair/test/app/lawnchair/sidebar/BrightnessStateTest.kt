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

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Brightness is written straight to `Settings.System`, so a wrong number is immediately visible
 * on the device. These pin the clamping and the degenerate-range handling.
 */
class BrightnessStateTest {

    @Test
    fun clampLevel_keepsInRangeValue() {
        assertEquals(128, BrightnessState.clampLevel(128))
    }

    @Test
    fun clampLevel_rejectsZero() {
        // Zero would make the screen unreadable rather than merely dim.
        assertEquals(BrightnessState.MIN_LEVEL, BrightnessState.clampLevel(0))
    }

    @Test
    fun clampLevel_capsAboveMaximum() {
        assertEquals(BrightnessState.MAX_LEVEL, BrightnessState.clampLevel(999))
    }

    @Test
    fun clampLevel_handlesNegative() {
        assertEquals(BrightnessState.MIN_LEVEL, BrightnessState.clampLevel(-40))
    }

    @Test
    fun toProgress_and_fromProgress_roundTrip() {
        for (level in listOf(1, 64, 127, 200, 255)) {
            assertEquals(level, BrightnessState.fromProgress(BrightnessState.toProgress(level)))
        }
    }

    @Test
    fun fraction_isZeroAtMinimum() {
        assertEquals(0f, BrightnessState.fraction(1, 1, 255), 0.0001f)
    }

    @Test
    fun fraction_isOneAtMaximum() {
        assertEquals(1f, BrightnessState.fraction(255, 1, 255), 0.0001f)
    }

    @Test
    fun fraction_isHalfwayAtMidpoint() {
        assertEquals(0.5f, BrightnessState.fraction(128, 1, 255), 0.001f)
    }

    @Test
    fun fraction_doesNotDivideByZeroOnDegenerateRange() {
        // A device reporting max == min must render an empty track, not throw.
        assertEquals(0f, BrightnessState.fraction(100, 100, 100), 0f)
        assertEquals(0f, BrightnessState.fraction(100, 200, 100), 0f)
    }

    @Test
    fun fraction_clampsOutOfRangeLevel() {
        assertEquals(1f, BrightnessState.fraction(9999, 1, 255), 0f)
        assertEquals(0f, BrightnessState.fraction(-5, 1, 255), 0f)
    }

    @Test
    fun label_isPercentageOfMax() {
        assertEquals("100%", BrightnessState.label(255))
        // 127 of 255 is 49.8%, which rounds to 50 rather than truncating to 49.
        assertEquals("50%", BrightnessState.label(127))
        assertEquals("0%", BrightnessState.label(0))
    }

    @Test
    fun label_halfLevelRoundsUp() {
        assertEquals("50%", BrightnessState.label(128))
        assertEquals("40%", BrightnessState.label(102))
    }

    @Test
    fun label_capsAtFullForOversizedLevel() {
        assertEquals("100%", BrightnessState.label(400))
    }
}
