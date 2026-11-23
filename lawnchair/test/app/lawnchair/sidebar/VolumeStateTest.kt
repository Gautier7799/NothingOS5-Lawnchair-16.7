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

/**
 * The volume rows clamp into the stream's own range, which differs per device and per stream.
 * These tests pin the clamping and the silent rule across the edge cases that actually occur:
 * a stream with no range, a value past the maximum, and the two ends of the range.
 */
class VolumeStateTest {

    @Test
    fun clampProgress_keepsValuesInsideRange() {
        assertEquals(0, VolumeState.clampProgress(0, 15))
        assertEquals(7, VolumeState.clampProgress(7, 15))
        assertEquals(15, VolumeState.clampProgress(15, 15))
    }

    @Test
    fun clampProgress_clampsBothEnds() {
        assertEquals(0, VolumeState.clampProgress(-4, 15))
        assertEquals(15, VolumeState.clampProgress(99, 15))
    }

    @Test
    fun clampProgress_streamWithoutRangeIsZero() {
        // A device without a ringer reports a maximum of 0; the slider must not divide by it.
        assertEquals(0, VolumeState.clampProgress(5, 0))
        assertEquals(0, VolumeState.clampProgress(5, -1))
    }

    @Test
    fun fraction_spansZeroToOne() {
        assertEquals(0f, VolumeState.fraction(0, 10), 0.001f)
        assertEquals(0.5f, VolumeState.fraction(5, 10), 0.001f)
        assertEquals(1f, VolumeState.fraction(10, 10), 0.001f)
    }

    @Test
    fun fraction_noRangeIsZero() {
        assertEquals(0f, VolumeState.fraction(3, 0), 0.001f)
    }

    @Test
    fun fraction_overshootIsClampedToOne() {
        assertEquals(1f, VolumeState.fraction(50, 10), 0.001f)
    }

    @Test
    fun percent_roundsDownSoFullMeansFull() {
        assertEquals(0, VolumeState.percent(0, 10))
        assertEquals(50, VolumeState.percent(5, 10))
        assertEquals(100, VolumeState.percent(10, 10))
        // 14/15 is 93.3%, not 94; rounding down avoids showing 100% while one step is missing.
        assertEquals(93, VolumeState.percent(14, 15))
    }

    @Test
    fun isSilent_onlyAtZero() {
        assertTrue(VolumeState.isSilent(0, 15))
        assertFalse(VolumeState.isSilent(1, 15))
        // A stream with no range has nothing to be silent about, but it also has no volume.
        assertTrue(VolumeState.isSilent(0, 0))
    }

    @Test
    fun seekBarMax_neverZero() {
        // SeekBar with max 0 still lays out but the thumb cannot move; one step avoids that.
        assertEquals(1, VolumeState.seekBarMax(0))
        assertEquals(1, VolumeState.seekBarMax(-3))
        assertEquals(15, VolumeState.seekBarMax(15))
    }

    @Test
    fun streamIdsMatchAudioManager() {
        // Guard against a typo silently pointing a slider at the wrong stream.
        assertEquals(3, VolumeStream.MEDIA.streamId) // STREAM_MUSIC
        assertEquals(2, VolumeStream.RING.streamId) // STREAM_RING
        assertEquals(5, VolumeStream.NOTIFICATION.streamId) // STREAM_NOTIFICATION
        assertEquals(4, VolumeStream.ALARM.streamId) // STREAM_ALARM
    }

    @Test
    fun orderedCoversEveryStream() {
        assertEquals(VolumeStream.values().size, VolumeState.ordered.size)
        assertEquals(VolumeStream.values().toSet(), VolumeState.ordered.toSet())
    }
}
