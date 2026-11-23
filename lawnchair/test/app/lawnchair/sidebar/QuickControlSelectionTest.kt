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
 * The selection string survives upgrades, so it has to tolerate values this build did not
 * write. These pin the recovery behaviour rather than the happy path alone.
 */
class QuickControlSelectionTest {

    @Test
    fun resolve_unsetPreferenceGivesEveryControl() {
        assertEquals(QuickControl.defaultOrder, QuickControlSelection.resolve(null))
        assertEquals(QuickControl.defaultOrder, QuickControlSelection.resolve(""))
        assertEquals(QuickControl.defaultOrder, QuickControlSelection.resolve("   "))
    }

    @Test
    fun resolve_keepsOnlyNamedControlsInDisplayOrder() {
        val saved = "${QuickControl.DND.name},${QuickControl.TORCH.name}"
        // Display order, not the order they happened to be written in.
        assertEquals(
            listOf(QuickControl.TORCH, QuickControl.DND),
            QuickControlSelection.resolve(saved),
        )
    }

    @Test
    fun resolve_ignoresUnknownNames() {
        // A control removed in a later build must not break the strip.
        val saved = "${QuickControl.TORCH.name},REMOVED_CONTROL"
        assertEquals(listOf(QuickControl.TORCH), QuickControlSelection.resolve(saved))
    }

    @Test
    fun resolve_ignoresDuplicates() {
        val saved = "${QuickControl.TORCH.name},${QuickControl.TORCH.name}"
        assertEquals(listOf(QuickControl.TORCH), QuickControlSelection.resolve(saved))
    }

    @Test
    fun resolve_allUnknownGivesEmptyList() {
        // The user disabled everything, or the file is from a build with other names. Either
        // way the answer is "show no tiles", not "show all of them".
        assertEquals(emptyList<QuickControl>(), QuickControlSelection.resolve("NOPE,ALSO_NOPE"))
    }

    @Test
    fun resolve_toleratesWhitespaceAroundNames() {
        assertEquals(
            listOf(QuickControl.RINGER),
            QuickControlSelection.resolve("  RINGER  "),
        )
    }

    @Test
    fun serialise_writesDisplayOrder() {
        val written = QuickControlSelection.serialise(
            setOf(QuickControl.RINGER, QuickControl.TORCH),
        )
        assertEquals("TORCH,RINGER", written)
    }

    @Test
    fun serialise_roundTripsThroughResolve() {
        val original = listOf(QuickControl.AUTO_ROTATE, QuickControl.DND)
        assertEquals(original, QuickControlSelection.resolve(QuickControlSelection.serialise(original)))
    }

    @Test
    fun serialise_emptyGivesEmptyString() {
        assertEquals("", QuickControlSelection.serialise(emptyList()))
    }

    @Test
    fun toggle_turnsOnWhenOff() {
        val next = QuickControlSelection.toggle(emptySet(), QuickControl.TORCH)
        assertTrue(QuickControlSelection.isEnabled(next, QuickControl.TORCH))
    }

    @Test
    fun toggle_turnsOffWhenOn() {
        val next = QuickControlSelection.toggle(setOf(QuickControl.TORCH), QuickControl.TORCH)
        assertFalse(QuickControlSelection.isEnabled(next, QuickControl.TORCH))
    }

    @Test
    fun toggle_leavesOtherControlsAlone() {
        val next = QuickControlSelection.toggle(
            setOf(QuickControl.TORCH, QuickControl.DND),
            QuickControl.DND,
        )
        assertTrue(QuickControlSelection.isEnabled(next, QuickControl.TORCH))
        assertFalse(QuickControlSelection.isEnabled(next, QuickControl.DND))
    }
}
