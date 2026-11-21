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
    fun surfaceContainer(context: Context): Int = neutral(context, LIGHT.surfaceContainerHigh, DARK.surfaceContainerHigh)

    /** The page backdrop tint: the M3 "surface" role, used at an alpha over the blurred wallpaper. */
    fun surface(context: Context): Int = neutral(context, LIGHT.surface, DARK.surface)

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

    private fun neutral(context: Context, lightTone: Int, darkTone: Int): Int {
        val tone = if (isNight(context)) darkTone else lightTone
        return schemeColor(context) { neutralColor(it, tone) } ?: fallbackSurface(context)
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
