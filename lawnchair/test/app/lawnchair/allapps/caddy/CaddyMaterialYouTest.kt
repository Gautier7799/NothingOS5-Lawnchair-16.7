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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import dev.kdrag0n.colorkt.rgb.Srgb
import kotlin.math.sqrt
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
    fun everyRoleResolvesToItsOwnColourForTheSameScheme() {
        // The palette memo used to hold a single value keyed on (scheme, night) only, so the first
        // role resolved answered every later lookup: onSurface returned the surface colour and the
        // card title was drawn invisibly on its own background.
        val scheme = WallpaperScheme(neutral = Srgb(0.95, 0.95, 0.95), accent = Srgb(0.10, 0.43, 0.95))
        val light = CaddyMaterialYou.Role.entries.map { CaddyMaterialYou.roleColor(scheme, it, night = false) }
        val dark = CaddyMaterialYou.Role.entries.map { CaddyMaterialYou.roleColor(scheme, it, night = true) }

        assertEquals("every role must resolve to a distinct colour", 5, light.toSet().size)
        assertEquals("every role must resolve to a distinct colour", 5, dark.toSet().size)
    }

    @Test
    fun everyRoleOwnsAnIndependentMemoSlot() {
        // The bug this pins: a single shared slot keyed only on (scheme, night) meant the first
        // role resolved answered every later lookup, so the card title got the surface colour and
        // became invisible. Distinct slots are what makes that impossible.
        val slots = CaddyMaterialYou.Role.entries.map { CaddyMaterialYou.roleCache(it) }
        assertEquals(
            "each role needs its own memo slot",
            CaddyMaterialYou.Role.entries.size,
            slots.distinct().size,
        )

        val scheme = WallpaperScheme(neutral = Srgb(0.95, 0.95, 0.95), accent = Srgb(0.10, 0.43, 0.95))
        // Filling one slot must not answer a lookup in another.
        val surfaceSlot = CaddyMaterialYou.roleCache(CaddyMaterialYou.Role.SurfaceContainer)
        surfaceSlot.getOrCompute(scheme) { 0x111111 }
        val foreground = CaddyMaterialYou.roleCache(CaddyMaterialYou.Role.OnSurface)
            .getOrCompute(scheme) { 0x222222 }
        assertNotEquals("a filled slot must not leak into another role", 0x111111, foreground)
    }

    @Test
    fun onSurfaceIsFarFromTheSurfaceItSitsOn() {
        // Text is only legible when it is far from its background in luminance. This is the
        // user-visible symptom of the shared-cache bug: the title vanished into the card.
        val scheme = WallpaperScheme(neutral = Srgb(0.95, 0.95, 0.95), accent = Srgb(0.10, 0.43, 0.95))
        for (night in listOf(false, true)) {
            val surface = CaddyMaterialYou.roleColor(scheme, CaddyMaterialYou.Role.SurfaceContainer, night)
            val foreground = CaddyMaterialYou.roleColor(scheme, CaddyMaterialYou.Role.OnSurface, night)
            val surfaceL = Color(surface).convert(ColorSpaces.Oklab).red
            val foregroundL = Color(foreground).convert(ColorSpaces.Oklab).red
            assertTrue(
                "on-surface must contrast with the card surface (night=$night)",
                kotlin.math.abs(surfaceL - foregroundL) > 0.5f,
            )
        }
    }

    @Test
    fun accentToneIsLighterInDarkModeSoItStaysVisible() {
        assertTrue(
            "an accent tuned for light mode is too dark on a dark surface",
            CaddyMaterialYou.accentTone(isNight = true) > CaddyMaterialYou.accentTone(isNight = false),
        )
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
                CaddyMaterialYou.accentTone(night),
            )
            tones.forEach { assertTrue("tone $it out of range", it in 0..100) }
        }
    }

    @Test
    fun surfaceTonesAreValidPaletteKeysForTheAccentLookup() {
        // The accent hue is taken with setLuminance, so it must never go through scheme.primary(),
        // which resolves a fixed key of 1000 - 10 * tone and throws for tones off the palette grid.
        // Surface tones such as 92 map to key 80, which does not exist in the swatch.
        for (night in listOf(false, true)) {
            for (tone in listOf(CaddyMaterialYou.surfaceTone(night), CaddyMaterialYou.surfaceContainerTone(night))) {
                val key = 1000 - 10 * tone
                assertTrue(
                    "tone $tone maps to palette key $key, which the swatch does not contain; " +
                        "the accent hue must be resolved with setLuminance instead of scheme.primary()",
                    key !in PALETTE_KEYS,
                )
            }
        }
    }

    private fun chroma(color: Int): Double {
        val oklab = Color(color).convert(ColorSpaces.Oklab)
        return sqrt((oklab.green * oklab.green + oklab.blue * oklab.blue).toDouble())
    }

    @Test
    fun surfaceCarriesTheWallpaperHue() {
        // A grey surface is what the neutral swatch produces on its own (chroma 6), and it is the
        // regression being fixed: the cards looked identical whatever the wallpaper.
        val grey = 0xFFF2F2F2.toInt()
        val blue = CaddyMaterialYou.blendOklab(grey, 0xFF1B6EF3.toInt(), SURFACE_TINT)
        val red = CaddyMaterialYou.blendOklab(grey, 0xFFE8453C.toInt(), SURFACE_TINT)

        assertTrue("the tinted surface must not stay grey", chroma(blue) > 0.02)
        assertTrue("the tinted surface must not stay grey", chroma(red) > 0.02)
        assertNotEquals("two wallpapers must give two surfaces", blue, red)
    }

    @Test
    fun tintingKeepsTheSurfaceLightness() {
        // Blending in Oklab keeps L, so a light surface stays light and text contrast is untouched.
        val grey = 0xFFF2F2F2.toInt()
        val tinted = CaddyMaterialYou.blendOklab(grey, 0xFF1B6EF3.toInt(), SURFACE_TINT)
        val greyL = Color(grey).convert(ColorSpaces.Oklab).red
        val tintedL = Color(tinted).convert(ColorSpaces.Oklab).red
        assertEquals("tinting must not change the tone", greyL, tintedL, 0.02f)
    }

    @Test
    fun tintingStaysSubtleEnoughToReadAsASurface() {
        // The card must stay closer to the neutral surface than to the raw accent, otherwise the
        // content loses its background and the card looks like a coloured block.
        val grey = 0xFFF2F2F2.toInt()
        val accent = 0xFF1B6EF3.toInt()
        val tinted = CaddyMaterialYou.blendOklab(grey, accent, SURFACE_TINT)
        assertTrue("the surface must stay mostly neutral", chroma(tinted) < chroma(accent) * 0.5)
    }

    private companion object {
        const val SURFACE_TINT = 0.22f

        /** The keys `Shades.of` publishes: tone 0, 10, 50, then every 100. */
        val PALETTE_KEYS = setOf(0, 10, 50) + (1..10).map { it * 100 }
    }
}
