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

package app.lawnchair.allapps.caddy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the regression behind flat grey Caddy cards.
 *
 * The cards used to ask the framework for `android:color/system_surface_*`. The framework only
 * publishes the raw accent and neutral palettes, so every lookup returned nothing and all cards
 * fell back to one hard-coded grey. The roles are now resolved from the launcher's Monet palette
 * at explicit tones; these tests pin the tones themselves, because a wrong tone is what makes two
 * distinct roles collapse onto the same colour.
 */
class CaddyMaterialYouTest {

    @Test
    fun lightSurfaceIsLighterThanDarkSurface() {
        assertTrue(CaddyMaterialYou.surfaceTone(isNight = false) > CaddyMaterialYou.surfaceTone(isNight = true))
    }

    @Test
    fun lightContainerIsLighterThanDarkContainer() {
        assertTrue(
            CaddyMaterialYou.surfaceContainerTone(isNight = false) >
                CaddyMaterialYou.surfaceContainerTone(isNight = true),
        )
    }

    @Test
    fun surfaceAndContainerStayDistinctInLightMode() {
        assertNotEquals(
            CaddyMaterialYou.surfaceTone(isNight = false),
            CaddyMaterialYou.surfaceContainerTone(isNight = false),
        )
    }

    @Test
    fun surfaceAndContainerStayDistinctInDarkMode() {
        assertNotEquals(
            CaddyMaterialYou.surfaceTone(isNight = true),
            CaddyMaterialYou.surfaceContainerTone(isNight = true),
        )
    }

    @Test
    fun onSurfaceContrastsWithTheSurfaceInLightMode() {
        assertTrue(
            "on-surface must be much darker than a light surface",
            CaddyMaterialYou.surfaceTone(isNight = false) - CaddyMaterialYou.onSurfaceTone(isNight = false) >= 50,
        )
    }

    @Test
    fun onSurfaceContrastsWithTheSurfaceInDarkMode() {
        assertTrue(
            "on-surface must be much lighter than a dark surface",
            CaddyMaterialYou.onSurfaceTone(isNight = true) - CaddyMaterialYou.surfaceTone(isNight = true) >= 50,
        )
    }

    @Test
    fun onSurfaceVariantSitsBetweenSurfaceAndOnSurface() {
        val lightSurface = CaddyMaterialYou.surfaceTone(isNight = false)
        val lightOnSurface = CaddyMaterialYou.onSurfaceTone(isNight = false)
        val variant = CaddyMaterialYou.onSurfaceVariantTone(isNight = false)
        assertTrue("secondary text must stay readable", variant > lightOnSurface && variant < lightSurface)
    }

    @Test
    fun outlineVariantIsVisibleOnTheLightSurface() {
        val lightSurface = CaddyMaterialYou.surfaceTone(isNight = false)
        val outline = CaddyMaterialYou.outlineVariantTone(isNight = false)
        assertTrue("the hairline must not match the surface it sits on", outline < lightSurface)
    }

    @Test
    fun rolesAreNotAllTheSameTone() {
        val lightTones = setOf(
            CaddyMaterialYou.surfaceTone(isNight = false),
            CaddyMaterialYou.surfaceContainerTone(isNight = false),
            CaddyMaterialYou.onSurfaceTone(isNight = false),
            CaddyMaterialYou.onSurfaceVariantTone(isNight = false),
            CaddyMaterialYou.outlineVariantTone(isNight = false),
        )
        assertEquals("each role needs its own tone, otherwise they render identically", 5, lightTones.size)
    }

    @Test
    fun tonesStayInsideTheValidRange() {
        for (night in listOf(false, true)) {
            val tones = listOf(
                CaddyMaterialYou.surfaceTone(night),
                CaddyMaterialYou.surfaceContainerTone(night),
                CaddyMaterialYou.onSurfaceTone(night),
                CaddyMaterialYou.onSurfaceVariantTone(night),
                CaddyMaterialYou.outlineVariantTone(night),
            )
            tones.forEach { assertTrue("tone $it out of range", it in 0..100) }
        }
    }
}
