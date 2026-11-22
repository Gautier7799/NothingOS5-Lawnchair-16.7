package app.lawnchair.allapps.caddy

import dev.kdrag0n.colorkt.Color
import dev.kdrag0n.monet.theme.ColorScheme
import dev.kdrag0n.monet.theme.ColorSwatch
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * A [ColorScheme] whose swatches publish exactly the keys `Shades.of` produces: 0, 10, 50 and every
 * 100. Anything else is absent, which is what the real palettes do.
 */
private class GridScheme : ColorScheme() {
    private val grid: ColorSwatch = (setOf(0, 10, 50) + (1..10).map { it * 100 })
        .associateWith { dev.kdrag0n.colorkt.rgb.Srgb(0.5, 0.6, 0.9) as Color }

    override val neutral1 = grid
    override val neutral2 = grid
    override val accent1 = grid
    override val accent2 = grid
    override val accent3 = grid
}

class CaddyAccentToneTest {

    @Test
    fun accentHueResolvesTonesThatAreNotOnThePaletteGrid() {
        // Surface tones such as 92 are off the grid. scheme.primary(92) looks up key 80 and throws,
        // so the hue must be taken at a known tone and moved with setLuminance.
        val scheme = GridScheme()
        val hue = CaddyMaterialYou.accentHue(scheme, 92)
        assertTrue("accent hue must resolve for an off-grid tone", hue != 0)
    }

    @Test
    fun primaryLookupFailsOffTheGrid() {
        // Pins the trap the accent hue avoids: this is the behaviour that made the direct lookup
        // unusable for surface tones.
        val scheme = GridScheme()
        try {
            scheme.primary(92)
            fail("scheme.primary(92) was expected to fail for a key that is not in the swatch")
        } catch (_: NullPointerException) {
            // expected: resolveComposeColor checkNotNull fails on a missing key
        }
    }
}
