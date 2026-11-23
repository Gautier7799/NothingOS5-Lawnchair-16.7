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
 * The ringer cycle is the one piece of the quick controls that can silently misbehave: asking
 * for silent without notification-policy access is ignored by `AudioManager`, which leaves the
 * tile showing a state the phone is not in. These tests pin the cycle so it only offers silent
 * when the request will be honoured.
 */
class QuickControlStateTest {

    @Test
    fun ringerCycle_normalGoesToVibrate() {
        assertEquals(
            QuickControlState.RINGER_VIBRATE,
            QuickControlState.nextRingerMode(QuickControlState.RINGER_NORMAL, policyAccessGranted = true),
        )
        assertEquals(
            QuickControlState.RINGER_VIBRATE,
            QuickControlState.nextRingerMode(QuickControlState.RINGER_NORMAL, policyAccessGranted = false),
        )
    }

    @Test
    fun ringerCycle_vibrateGoesToSilentOnlyWithPolicyAccess() {
        assertEquals(
            QuickControlState.RINGER_SILENT,
            QuickControlState.nextRingerMode(QuickControlState.RINGER_VIBRATE, policyAccessGranted = true),
        )
        assertEquals(
            QuickControlState.RINGER_NORMAL,
            QuickControlState.nextRingerMode(QuickControlState.RINGER_VIBRATE, policyAccessGranted = false),
        )
    }

    @Test
    fun ringerCycle_silentReturnsToNormal() {
        assertEquals(
            QuickControlState.RINGER_NORMAL,
            QuickControlState.nextRingerMode(QuickControlState.RINGER_SILENT, policyAccessGranted = true),
        )
    }

    @Test
    fun ringerCycle_neverReachesSilentWithoutPolicyAccess() {
        // Without notification-policy access `AudioManager` ignores a silent request, so the
        // cycle must stay in the two states the framework will honour.
        var mode = QuickControlState.RINGER_NORMAL
        repeat(10) {
            mode = QuickControlState.nextRingerMode(mode, policyAccessGranted = false)
            assertFalse(
                "silent is unreachable without policy access",
                mode == QuickControlState.RINGER_SILENT,
            )
        }
    }

    @Test
    fun ringerCycle_visitsEveryReachableModeWithPolicyAccess() {
        val visited = mutableSetOf<Int>()
        var mode = QuickControlState.RINGER_NORMAL
        repeat(3) {
            mode = QuickControlState.nextRingerMode(mode, policyAccessGranted = true)
            visited += mode
        }
        // Three taps from normal must reach vibrate, silent, and come back to normal.
        assertEquals(
            setOf(
                QuickControlState.RINGER_VIBRATE,
                QuickControlState.RINGER_SILENT,
                QuickControlState.RINGER_NORMAL,
            ),
            visited,
        )
    }

    @Test
    fun isRingerActive_onlyNormalIsInactive() {
        assertFalse(QuickControlState.isRingerActive(QuickControlState.RINGER_NORMAL))
        assertTrue(QuickControlState.isRingerActive(QuickControlState.RINGER_VIBRATE))
        assertTrue(QuickControlState.isRingerActive(QuickControlState.RINGER_SILENT))
    }

    @Test
    fun ringerSubtitles() {
        assertEquals("Normal", QuickControlState.ringerSubtitle(QuickControlState.RINGER_NORMAL))
        assertEquals("Vibrate", QuickControlState.ringerSubtitle(QuickControlState.RINGER_VIBRATE))
        assertEquals("Silent", QuickControlState.ringerSubtitle(QuickControlState.RINGER_SILENT))
    }

    @Test
    fun toggleSubtitle() {
        assertEquals("On", QuickControlState.toggleSubtitle(true))
        assertEquals("Off", QuickControlState.toggleSubtitle(false))
    }

    @Test
    fun onlySettingsSystemTilesNeedWriteSettings() {
        assertTrue(QuickControlState.needsWriteSettings(QuickControl.AUTO_ROTATE))
        assertTrue(QuickControlState.needsWriteSettings(QuickControl.AUTO_BRIGHTNESS))
        assertFalse(QuickControlState.needsWriteSettings(QuickControl.TORCH))
        assertFalse(QuickControlState.needsWriteSettings(QuickControl.DND))
        assertFalse(QuickControlState.needsWriteSettings(QuickControl.RINGER))
    }

    @Test
    fun onlyDndNeedsPolicyAccess() {
        assertTrue(QuickControlState.needsPolicyAccess(QuickControl.DND))
        assertFalse(QuickControlState.needsPolicyAccess(QuickControl.TORCH))
        assertFalse(QuickControlState.needsPolicyAccess(QuickControl.RINGER))
    }

    @Test
    fun defaultOrderCoversEveryControlOnce() {
        val order = QuickControl.defaultOrder
        assertEquals(QuickControl.entries.size, order.size)
        assertEquals(QuickControl.entries.toSet(), order.toSet())
    }

    @Test
    fun defaultOrderIsNotSharedMutableState() {
        val first = QuickControl.defaultOrder
        (first as MutableList<QuickControl>).clear()
        assertEquals(QuickControl.entries.size, QuickControl.defaultOrder.size)
    }
}
