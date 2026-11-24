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

/** Gesture classification decides which action fires, so its arithmetic is pinned here. */
class SidebarGestureTest {

    private val slop = 16f

    @Test
    fun `a drag shorter than the slop in both axes is not a gesture`() {
        assertEquals(SidebarGesture.NONE, SidebarGesture.classify(10f, 10f, slop, onRightEdge = true))
        assertEquals(SidebarGesture.NONE, SidebarGesture.classify(0f, 0f, slop, onRightEdge = true))
        assertEquals(SidebarGesture.NONE, SidebarGesture.classify(-15f, 15f, slop, onRightEdge = true))
    }

    @Test
    fun `pulling leftwards from the right edge opens the panel`() {
        assertEquals(
            SidebarGesture.SWIPE_IN,
            SidebarGesture.classify(-60f, 0f, slop, onRightEdge = true),
        )
    }

    @Test
    fun `pulling rightwards from the left edge opens the panel`() {
        assertEquals(
            SidebarGesture.SWIPE_IN,
            SidebarGesture.classify(60f, 0f, slop, onRightEdge = false),
        )
    }

    @Test
    fun `the outward direction is not an opening pull`() {
        // Rightwards on the right edge moves away from the panel.
        assertEquals(
            SidebarGesture.NONE,
            SidebarGesture.classify(60f, 0f, slop, onRightEdge = true),
        )
        assertEquals(
            SidebarGesture.NONE,
            SidebarGesture.classify(-60f, 0f, slop, onRightEdge = false),
        )
    }

    @Test
    fun `vertical drags report up and down`() {
        assertEquals(
            SidebarGesture.SWIPE_UP,
            SidebarGesture.classify(0f, -60f, slop, onRightEdge = true),
        )
        assertEquals(
            SidebarGesture.SWIPE_DOWN,
            SidebarGesture.classify(0f, 60f, slop, onRightEdge = true),
        )
    }

    @Test
    fun `the larger axis wins on a diagonal drag`() {
        // Mostly vertical: a 10px horizontal drift must not turn it into a horizontal gesture.
        assertEquals(
            SidebarGesture.SWIPE_UP,
            SidebarGesture.classify(-10f, -80f, slop, onRightEdge = true),
        )
        // Mostly horizontal.
        assertEquals(
            SidebarGesture.SWIPE_IN,
            SidebarGesture.classify(-80f, -10f, slop, onRightEdge = true),
        )
    }

    @Test
    fun `defaults open the panel only for the inward pull`() {
        val bindings = SidebarGestureBindings.defaults
        assertEquals(
            SidebarGestureAction.OPEN_PANEL,
            SidebarGestureBindings.actionFor(bindings, SidebarGesture.SWIPE_IN),
        )
        assertEquals(
            SidebarGestureAction.NONE,
            SidebarGestureBindings.actionFor(bindings, SidebarGesture.SWIPE_UP),
        )
    }

    @Test
    fun `an unset preference resolves to the defaults`() {
        assertEquals(SidebarGestureBindings.defaults, SidebarGestureBindings.resolve(null))
        assertEquals(SidebarGestureBindings.defaults, SidebarGestureBindings.resolve(""))
        assertEquals(SidebarGestureBindings.defaults, SidebarGestureBindings.resolve("   "))
    }

    @Test
    fun `a saved binding round trips`() {
        val original = mapOf(
            SidebarGesture.SWIPE_IN to SidebarGestureAction.OPEN_SEARCH,
            SidebarGesture.SWIPE_UP to SidebarGestureAction.OPEN_ASSISTANT,
            SidebarGesture.SWIPE_DOWN to SidebarGestureAction.OPEN_NOTIFICATIONS,
        )
        val saved = SidebarGestureBindings.serialise(original)
        assertEquals(original, SidebarGestureBindings.resolve(saved))
    }

    @Test
    fun `unknown names in a saved string are ignored, not fatal`() {
        val resolved = SidebarGestureBindings.resolve(
            "swipe_in:NOT_A_REAL_ACTION,nonsense:OPEN_PANEL,swipe_up:OPEN_QR",
        )
        // Every bad entry is dropped and the direction falls back to its default.
        assertEquals(
            SidebarGestureAction.OPEN_PANEL,
            SidebarGestureBindings.actionFor(resolved, SidebarGesture.SWIPE_IN),
        )
        assertEquals(
            SidebarGestureAction.NONE,
            SidebarGestureBindings.actionFor(resolved, SidebarGesture.SWIPE_UP),
        )
    }

    @Test
    fun `a direction missing from a saved string keeps its default`() {
        // Only swipe_up was ever written; swipe_in must still open the panel.
        val resolved = SidebarGestureBindings.resolve("swipe_up:OPEN_ALL_APPS")
        assertEquals(
            SidebarGestureAction.OPEN_PANEL,
            SidebarGestureBindings.actionFor(resolved, SidebarGesture.SWIPE_IN),
        )
        assertEquals(
            SidebarGestureAction.OPEN_ALL_APPS,
            SidebarGestureBindings.actionFor(resolved, SidebarGesture.SWIPE_UP),
        )
    }

    @Test
    fun `no gesture maps to no action`() {
        assertEquals(
            SidebarGestureAction.NONE,
            SidebarGestureBindings.actionFor(
                mapOf(SidebarGesture.SWIPE_IN to SidebarGestureAction.OPEN_SEARCH),
                SidebarGesture.NONE,
            ),
        )
    }

    @Test
    fun `serialised bindings are stable and ordered`() {
        val saved = SidebarGestureBindings.serialise(SidebarGestureBindings.defaults)
        assertEquals("swipe_in:OPEN_PANEL,swipe_up:NONE,swipe_down:NONE", saved)
        // Re-serialising a parsed value produces the same string, so the file never churns.
        assertEquals(saved, SidebarGestureBindings.serialise(SidebarGestureBindings.resolve(saved)))
    }
}
