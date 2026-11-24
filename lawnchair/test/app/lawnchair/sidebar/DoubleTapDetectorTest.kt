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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleTapDetectorTest {

    @Test
    fun `first tap never completes a double tap`() {
        assertFalse(DoubleTapDetector().onTap(1_000L))
    }

    @Test
    fun `two taps inside the window are a double tap`() {
        val detector = DoubleTapDetector()
        assertFalse(detector.onTap(1_000L))
        assertTrue(detector.onTap(1_150L))
    }

    @Test
    fun `a tap exactly at the window edge still counts`() {
        val detector = DoubleTapDetector(windowMillis = 300L)
        detector.onTap(0L)
        assertTrue(detector.onTap(300L))
    }

    @Test
    fun `a slow second tap starts a new pair`() {
        val detector = DoubleTapDetector(windowMillis = 300L)
        detector.onTap(0L)
        assertFalse(detector.onTap(301L))
        assertTrue(detector.onTap(400L))
    }

    @Test
    fun `a completed double tap resets so a third tap does not fire again`() {
        val detector = DoubleTapDetector()
        detector.onTap(0L)
        assertTrue(detector.onTap(100L))
        assertFalse(detector.onTap(150L))
    }

    @Test
    fun `a clock that goes backwards is treated as a new pair`() {
        val detector = DoubleTapDetector()
        detector.onTap(5_000L)
        assertFalse(detector.onTap(4_000L))
    }

    @Test
    fun `reset drops the pending tap`() {
        val detector = DoubleTapDetector()
        detector.onTap(0L)
        detector.reset()
        assertFalse(detector.onTap(50L))
    }

    @Test
    fun `with no binding a tap fires immediately and is never deferred`() {
        assertEquals(
            TapOutcome.FIRE_SINGLE_TAP_NOW,
            TapOutcome.forTap(doubleTapBound = false, tapCompletedDouble = false),
        )
    }

    @Test
    fun `with a binding the first tap is deferred`() {
        assertEquals(
            TapOutcome.DEFER_SINGLE_TAP,
            TapOutcome.forTap(doubleTapBound = true, tapCompletedDouble = false),
        )
    }

    @Test
    fun `a completed double tap fires the binding instead of the single tap`() {
        assertEquals(
            TapOutcome.FIRE_DOUBLE_TAP,
            TapOutcome.forTap(doubleTapBound = true, tapCompletedDouble = true),
        )
    }

    @Test
    fun `an unbound double tap cannot fire, even if the detector completed`() {
        // The view cannot reach the detector without a handler, but pinning the rule stops a
        // future refactor from firing a binding the user never set.
        assertEquals(
            TapOutcome.FIRE_SINGLE_TAP_NOW,
            TapOutcome.forTap(doubleTapBound = false, tapCompletedDouble = true),
        )
    }

    @Test
    fun `two taps with a binding produce exactly one double tap and one deferral`() {
        val detector = DoubleTapDetector()
        val outcomes = listOf(1_000L, 1_120L).map { t ->
            TapOutcome.forTap(doubleTapBound = true, tapCompletedDouble = detector.onTap(t))
        }
        assertEquals(
            listOf(TapOutcome.DEFER_SINGLE_TAP, TapOutcome.FIRE_DOUBLE_TAP),
            outcomes,
        )
    }
}
