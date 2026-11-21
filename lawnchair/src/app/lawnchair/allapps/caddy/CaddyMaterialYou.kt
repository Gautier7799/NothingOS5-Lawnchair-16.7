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

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.toArgb
import app.lawnchair.theme.ThemeProvider
import app.lawnchair.theme.setLuminance
import dev.kdrag0n.monet.theme.ColorScheme

/**
 * The Material 3 surface roles Caddy draws with, derived from the launcher's own Monet palette so
 * the cards follow the wallpaper and the accent style chosen in settings.
 *
 * The framework does not publish the surface roles as `android:color/system_surface_*` resources;
 * it only ships the raw `system_accent{1,2,3}_*` and `system_neutral{1,2}_*` palettes. Looking the
 * surface roles up by name therefore always missed, and every card silently fell back to a flat
 * grey. The tones below are the ones `toComposeColorScheme` assigns to the Compose theme, so Caddy
 * and the rest of Lawnchair resolve the same colours.
 */
object CaddyMaterialYou {

    /** The card / sheet surface: the M3 "surface container high" role. */
    fun surfaceContainer(context: Context): Int = tintedSurface(context, LIGHT.surfaceContainerHigh, DARK.surfaceContainerHigh)

    /** The page backdrop tint: the M3 "surface" role, used at an alpha over the blurred wallpaper. */
    fun surface(context: Context): Int = tintedSurface(context, LIGHT.surface, DARK.surface)

    /** Primary text on a surface. */
    fun onSurface(context: Context): Int = neutral(context, LIGHT.onSurface, DARK.onSurface)

    /** Secondary text on a surface. */
    fun onSurfaceVariant(context: Context): Int = neutralVariant(context, LIGHT.onSurfaceVariant, DARK.onSurfaceVariant)

    /** The hairline outline drawn around surfaces. */
    fun outlineVariant(context: Context): Int = neutralVariant(context, LIGHT.outlineVariant, DARK.outlineVariant)

    /** The wallpaper accent, used for selected states and the "+ Add" card. */
    fun accent(context: Context): Int = schemeColor(context) { accentColor(it, accentTone(isNight(context))) }
        ?: frameworkColor(context, "system_accent1_600")
        ?: CaddyCardRenderer.resolveDynamicColor(context)

    /** The translucent tint laid over the blurred wallpaper, so the blur carries the theme colour. */
    fun backdropTint(context: Context, alphaPercent: Int): Int {
        val base = surface(context)
        val alpha = Math.round(alphaPercent.coerceIn(0, 100) / 100f * MAX_BACKDROP_ALPHA)
        return Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
    }

    private const val MAX_BACKDROP_ALPHA = 255

    /**
     * The palette swatches only carry tones 0, 10, 50 and every 100. Any other tone is obtained by
     * resolving a tone that exists and moving it to the requested luminance, which is exactly what
     * `toComposeColorScheme` does.
     */
    private const val TONE_SEED = 40

    /**
     * How much of the accent hue is mixed into a surface. Enough that a change of wallpaper is
     * visible at a glance, low enough that the card still reads as a surface behind its content.
     */
    private const val SURFACE_TINT = 0.22f

    private fun neutral(context: Context, lightTone: Int, darkTone: Int): Int {
        val tone = if (isNight(context)) darkTone else lightTone
        return schemeColor(context) { neutralColor(it, tone) } ?: fallbackSurface(context)
    }

    /**
     * A surface that actually carries the wallpaper colour.
     *
     * The neutral swatch is generated with a chroma of 6, so a Material You surface is grey by
     * design and moving the wallpaper barely shows. Take the accent hue (chroma 36) at the surface
     * tone instead, then pull it back toward the neutral tone so the card still reads as a surface
     * rather than as a coloured block.
     */
    private fun tintedSurface(context: Context, lightTone: Int, darkTone: Int): Int {
        val tone = if (isNight(context)) darkTone else lightTone
        val tinted = schemeColor(context) { accentHue(it, tone) }
        if (tinted == null) return fallbackSurface(context)
        return blendOklab(tinted, neutral(context, lightTone, darkTone), SURFACE_TINT)
    }

    /**
     * Mixes the hue of [tint] into [base] while keeping the lightness of [base].
     *
     * Only the Oklab `a`/`b` (chroma) axes are blended. Blending `L` as well would move the tone of
     * the surface, which is what the text contrast was computed against.
     */
    internal fun blendOklab(base: Int, tint: Int, amount: Float): Int {
        val baseOklab = ComposeColor(base).convert(ColorSpaces.Oklab)
        val tintOklab = ComposeColor(tint).convert(ColorSpaces.Oklab)
        val mixed = ComposeColor(
            baseOklab.red,
            baseOklab.green * (1f - amount) + tintOklab.green * amount,
            baseOklab.blue * (1f - amount) + tintOklab.blue * amount,
            alpha = 1f,
            colorSpace = ColorSpaces.Oklab,
        )
        return mixed.convert(ColorSpaces.Srgb).toArgb()
    }

    private fun neutralVariant(context: Context, lightTone: Int, darkTone: Int): Int {
        val tone = if (isNight(context)) darkTone else lightTone
        return schemeColor(context) { neutralVariantColor(it, tone) } ?: fallbackSurface(context)
    }

    private fun schemeColor(context: Context, pick: (ColorScheme) -> Int): Int? = runCatching {
        pick(ThemeProvider.INSTANCE.get(context).colorScheme)
    }.getOrNull()

    /** The neutral palette colour at [tone], for the wallpaper-derived scheme. */
    internal fun neutralColor(scheme: ColorScheme, tone: Int): Int = scheme.neutral(TONE_SEED).setLuminance(tone.toFloat()).toArgb()

    /** The neutral-variant palette colour at [tone], used for text and hairlines. */
    internal fun neutralVariantColor(scheme: ColorScheme, tone: Int): Int = scheme.neutralVariant(TONE_SEED).setLuminance(tone.toFloat()).toArgb()

    /**
     * The accent hue at any [tone].
     *
     * `scheme.primary(tone)` resolves a fixed key of `1000 - 10 * tone` and throws for tones that
     * are not on the palette grid, so the hue is taken at a known tone and moved to [tone].
     */
    internal fun accentHue(scheme: ColorScheme, tone: Int): Int = scheme.primary(TONE_SEED).setLuminance(tone.toFloat()).toArgb()

    /** The accent colour at [tone], used for selected states. */
    internal fun accentColor(scheme: ColorScheme, tone: Int): Int = scheme.primary(tone).toArgb()

    /** The surface container high tone for the current light/dark mode. */
    internal fun surfaceContainerTone(isNight: Boolean): Int = if (isNight) DARK.surfaceContainerHigh else LIGHT.surfaceContainerHigh

    /** The accent tone for the current light/dark mode. */
    internal fun accentTone(isNight: Boolean): Int = if (isNight) DARK.accent else LIGHT.accent

    /** The surface tone for the current light/dark mode. */
    internal fun surfaceTone(isNight: Boolean): Int = if (isNight) DARK.surface else LIGHT.surface

    /** The on-surface tone for the current light/dark mode. */
    internal fun onSurfaceTone(isNight: Boolean): Int = if (isNight) DARK.onSurface else LIGHT.onSurface

    /** The on-surface-variant tone for the current light/dark mode. */
    internal fun onSurfaceVariantTone(isNight: Boolean): Int = if (isNight) DARK.onSurfaceVariant else LIGHT.onSurfaceVariant

    /** The outline-variant tone for the current light/dark mode. */
    internal fun outlineVariantTone(isNight: Boolean): Int = if (isNight) DARK.outlineVariant else LIGHT.outlineVariant

    internal fun isNight(context: Context): Boolean = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

    private fun fallbackSurface(context: Context): Int {
        val night = isNight(context)
        val base = if (night) 30 else 250
        val accent = CaddyCardRenderer.resolveDynamicColor(context)
        val weight = 0.06f
        return Color.rgb(
            Math.round(base * (1f - weight) + Color.red(accent) * weight),
            Math.round(base * (1f - weight) + Color.green(accent) * weight),
            Math.round(base * (1f - weight) + Color.blue(accent) * weight),
        )
    }

    private fun frameworkColor(context: Context, name: String): Int? {
        val id = context.resources.getIdentifier(name, "color", "android")
        if (id == 0) return null
        return runCatching { context.resources.getColor(id, context.theme) }.getOrNull()
    }

    /** Tones per role, mirroring the light and dark branches of `toComposeColorScheme`. */
    private data class Tones(
        val surface: Int,
        val surfaceContainerHigh: Int,
        val onSurface: Int,
        val onSurfaceVariant: Int,
        val outlineVariant: Int,
        val accent: Int,
    )

    private val LIGHT = Tones(
        surface = 94,
        surfaceContainerHigh = 92,
        onSurface = 10,
        onSurfaceVariant = 30,
        outlineVariant = 80,
        accent = 40,
    )

    private val DARK = Tones(
        surface = 6,
        surfaceContainerHigh = 17,
        onSurface = 90,
        onSurfaceVariant = 80,
        outlineVariant = 30,
        accent = 80,
    )
}
