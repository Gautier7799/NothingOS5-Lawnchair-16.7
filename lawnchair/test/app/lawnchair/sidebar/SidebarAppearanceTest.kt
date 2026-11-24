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

class SidebarAppearanceTest {

    @Test
    fun `theme resolves saved name regardless of case`() {
        assertEquals(SidebarTheme.OPAQUE, SidebarTheme.resolve("opaque"))
        assertEquals(SidebarTheme.TRANSPARENT, SidebarTheme.resolve("  Transparent "))
        assertEquals(SidebarTheme.TONAL, SidebarTheme.resolve("TONAL"))
    }

    @Test
    fun `unknown and blank theme fall back to tonal`() {
        assertEquals(SidebarTheme.TONAL, SidebarTheme.resolve(null))
        assertEquals(SidebarTheme.TONAL, SidebarTheme.resolve(""))
        assertEquals(SidebarTheme.TONAL, SidebarTheme.resolve("neon"))
    }

    @Test
    fun `opaque theme forces full opacity ignoring the slider`() {
        assertEquals(1f, SidebarAppearance.effectiveOpacity(SidebarTheme.OPAQUE, 40), 0.0001f)
        assertEquals(1f, SidebarAppearance.effectiveOpacity(SidebarTheme.OPAQUE, 100), 0.0001f)
    }

    @Test
    fun `transparent theme uses a fixed translucency`() {
        assertEquals(0.55f, SidebarAppearance.effectiveOpacity(SidebarTheme.TRANSPARENT, 100), 0.0001f)
    }

    @Test
    fun `tonal theme follows the opacity slider`() {
        assertEquals(1f, SidebarAppearance.effectiveOpacity(SidebarTheme.TONAL, 100), 0.0001f)
        assertEquals(0.4f, SidebarAppearance.effectiveOpacity(SidebarTheme.TONAL, 40), 0.0001f)
    }

    @Test
    fun `label size shrinks as columns grow`() {
        val sizes = (1..4).map { SidebarAppearance.labelSizeSp(it) }
        assertEquals(sizes.sortedDescending(), sizes)
        assertTrue(sizes.first() > sizes.last())
    }

    @Test
    fun `label size clamps out-of-range column counts`() {
        assertEquals(SidebarAppearance.labelSizeSp(1), SidebarAppearance.labelSizeSp(0))
        assertEquals(SidebarAppearance.labelSizeSp(4), SidebarAppearance.labelSizeSp(99))
    }

    @Test
    fun `defaults keep labels and fullscreen hiding off`() {
        val defaults = SidebarAppearance()
        assertFalse(defaults.showLabels)
        assertFalse(defaults.autoHideFullscreen)
        assertTrue(defaults.showOutline)
        assertEquals(SidebarTheme.TONAL, defaults.theme)
    }
}
