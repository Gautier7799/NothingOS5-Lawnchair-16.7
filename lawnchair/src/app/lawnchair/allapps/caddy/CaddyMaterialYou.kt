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

/**
 * The Material 3 surface roles Caddy draws with, resolved from the framework palette so the cards
 * and the page backdrop follow the same wallpaper-derived theme the rest of the system uses.
 *
 * The framework exposes these roles as `android:color/system_*` resources on Android 12+. When a
 * device or ROM does not provide one, the role falls back to a light/dark neutral blended with the
 * wallpaper accent, so the look stays close instead of turning into flat grey.
 */
object CaddyMaterialYou {

    /** The card / sheet surface: the M3 "surface container high" role. */
    fun surfaceContainer(context: Context): Int = frameworkColor(context, "system_surface_container_high") ?: fallbackSurface(context)

    /** The page backdrop tint: the M3 "surface" role, used at an alpha over the blurred wallpaper. */
    fun surface(context: Context): Int = frameworkColor(context, "system_surface") ?: fallbackSurface(context)

    /** Primary text on a surface. */
    fun onSurface(context: Context): Int = frameworkColor(context, "system_on_surface") ?: if (isNight(context)) Color.WHITE else Color.rgb(28, 27, 31)

    /** Secondary text on a surface. */
    fun onSurfaceVariant(context: Context): Int = frameworkColor(context, "system_on_surface_variant") ?: if (isNight(context)) Color.rgb(202, 196, 208) else Color.rgb(73, 69, 79)

    /** The hairline outline drawn around surfaces. */
    fun outlineVariant(context: Context): Int = frameworkColor(context, "system_outline_variant") ?: if (isNight(context)) Color.rgb(73, 69, 79) else Color.rgb(202, 196, 208)

    /** The wallpaper accent, used for selected states and the "+ Add" card. */
    fun accent(context: Context): Int = frameworkColor(context, "system_accent1_600") ?: CaddyCardRenderer.resolveDynamicColor(context)

    /** The translucent tint laid over the blurred wallpaper, so the blur carries the theme colour. */
    fun backdropTint(context: Context, alphaPercent: Int): Int {
        val base = surface(context)
        val alpha = Math.round(alphaPercent.coerceIn(0, 100) / 100f * MAX_BACKDROP_ALPHA)
        return Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
    }

    private const val MAX_BACKDROP_ALPHA = 255

    private fun isNight(context: Context): Boolean = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
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
}
