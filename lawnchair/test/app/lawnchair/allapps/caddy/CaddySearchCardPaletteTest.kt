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
import dev.kdrag0n.colorkt.Color as ColorKt
import dev.kdrag0n.colorkt.rgb.Srgb
import dev.kdrag0n.monet.theme.ColorScheme
import dev.kdrag0n.monet.theme.ColorSwatch
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A [ColorScheme] with a flat neutral swatch and a flat accent swatch, so a test can tell which
 * palette a surface was read from.
 */
private class WallpaperScheme(neutral: Srgb, accent: Srgb) : ColorScheme() {
    private fun swatch(color: Srgb): ColorSwatch =
        (setOf(0, 10, 50) + (1..10).map { it * 100 }).associateWith { color as ColorKt }

    override val neutral1 = swatch(neutral)
    override val neutral2 = swatch(neutral)
    override val accent1 = swatch(accent)
    override val accent2 = swatch(accent)
    override val accent3 = swatch(accent)
}

/**
 * Guards the search-actions card against the regression that left it a fixed charcoal block.
 *
 * The card is drawn from Java, so these tests pin the palette it now receives: the surface must
 * come from the wallpaper-tinted neutral role, and the text must stay on-surface.
 */
class CaddySearchCardPaletteTest {

    private val neutral = Srgb(0.95, 0.95, 0.95)
    private val blue = Srgb(0.10, 0.43, 0.95)
    private val red = Srgb(0.91, 0.27, 0.24)

    private fun chroma(color: Int): Double {
        val oklab = Color(color).convert(ColorSpaces.Oklab)
        return sqrt((oklab.green * oklab.green + oklab.blue * oklab.blue).toDouble())
    }

    private fun tone(color: Int): Float = Color(color).convert(ColorSpaces.Oklab).red * 100f

    @Test
    fun searchCardSurfaceFollowsTheWallpaper() {
        val bluePalette = CaddyMaterialYou.searchCardPalette(WallpaperScheme(neutral, blue), night = false)
        val redPalette = CaddyMaterialYou.searchCardPalette(WallpaperScheme(neutral, red), night = false)

        assertNotEquals("the search card must react to the wallpaper", bluePalette.surface, redPalette.surface)
        assertTrue("the surface must carry some wallpaper chroma", chroma(bluePalette.surface) > 0.005)
    }

    @Test
    fun searchCardSurfaceStaysMostlyNeutral() {
        val scheme = WallpaperScheme(neutral, blue)
        val palette = CaddyMaterialYou.searchCardPalette(scheme, night = false)
        val accent = CaddyMaterialYou.accentHue(scheme, CaddyMaterialYou.surfaceContainerTone(false))

        assertTrue(
            "the tint must stay subtle so the card still reads as a surface",
            chroma(palette.surface) < chroma(accent) * 0.5,
        )
    }

    @Test
    fun searchCardSurfaceKeepsTheSurfaceTone() {
        val scheme = WallpaperScheme(neutral, blue)
        val palette = CaddyMaterialYou.searchCardPalette(scheme, night = false)
        val neutralSurface = CaddyMaterialYou.neutralColor(scheme, CaddyMaterialYou.surfaceContainerTone(false))

        assertEquals("the tint must not move the tone", tone(neutralSurface), tone(palette.surface), 2f)
    }

    @Test
    fun searchCardForegroundContrastsWithItsSurface() {
        val palette = CaddyMaterialYou.searchCardPalette(WallpaperScheme(neutral, blue), night = false)

        assertTrue(
            "text must be much darker than a light surface",
            tone(palette.surface) - tone(palette.foreground) >= 50f,
        )
    }

    @Test
    fun darkModeFlipsTheTones() {
        val light = CaddyMaterialYou.searchCardPalette(WallpaperScheme(neutral, blue), night = false)
        val dark = CaddyMaterialYou.searchCardPalette(WallpaperScheme(neutral, blue), night = true)

        assertTrue("a dark surface must be darker than a light one", tone(dark.surface) < tone(light.surface))
        assertTrue("dark-mode text must be lighter than the surface", tone(dark.foreground) > tone(dark.surface))
    }
}
