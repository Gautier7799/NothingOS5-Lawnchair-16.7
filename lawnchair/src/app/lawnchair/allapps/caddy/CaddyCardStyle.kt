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
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import app.lawnchair.util.resolveFolderPreviewColor
import com.android.launcher3.Utilities

/**
 * Shared visual tokens for a Caddy card surface.
 *
 * The card look is defined once here so the app drawer renderer and the native
 * Home Screen Caddy folder produce the same surface. Both callers only supply the
 * context; the geometry, tinting and stroke come from this object.
 */
object CaddyCardStyle {

    /** Corner radius of the card surface, in dp. */
    const val CORNER_RADIUS_DP = 28f

    private const val STROKE_DP = 1f

    /**
     * Builds the translucent, accent-tinted card surface. The accent is resolved from
     * the same Monet source the drawer uses, so the card follows the wallpaper palette
     * and Lawnchair's folder tint fallback.
     */
    fun createCardBackground(context: Context): GradientDrawable {
        val themeColor = resolveDynamicColor(context)
        // Translucent Nothing-style glass, tinted by Lawnchair's existing folder /
        // preview color so the user's selected and dynamic colors remain visible.
        val glassRed = Math.round(Color.red(themeColor) * 0.30f + 28 * 0.70f)
        val glassGreen = Math.round(Color.green(themeColor) * 0.30f + 31 * 0.70f)
        val glassBlue = Math.round(Color.blue(themeColor) * 0.30f + 38 * 0.70f)
        val topGlass = Color.argb(184, glassRed, glassGreen, glassBlue)
        val bottomGlass = Color.argb(
            148,
            Math.max(12, glassRed - 10),
            Math.max(14, glassGreen - 10),
            Math.max(18, glassBlue - 10),
        )
        val accentStroke = Color.argb(
            116,
            Color.red(themeColor),
            Color.green(themeColor),
            Color.blue(themeColor),
        )

        val background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(topGlass, bottomGlass),
        )
        background.cornerRadius = Utilities.dpToPx(CORNER_RADIUS_DP, context).toFloat()
        background.setStroke(Utilities.dpToPx(STROKE_DP, context), accentStroke)
        return background
    }

    /** Corner radius of the card surface, in pixels. */
    fun cornerRadiusPx(context: Context): Float = Utilities.dpToPx(CORNER_RADIUS_DP, context).toFloat()

    /**
     * Resolve Android's themed accent so Caddy follows the system Monet palette
     * (including wallpaper-based colors on Android 12+). Keeps Lawnchair's folder tint
     * as a compatibility fallback for themes that do not expose colorAccent.
     */
    fun resolveDynamicColor(context: Context): Int {
        val resources: Resources = context.resources
        val systemPaletteColors = arrayOf(
            "system_accent1_600",
            "system_accent2_600",
            "system_accent3_600",
        )
        for (colorName in systemPaletteColors) {
            val colorId = resources.getIdentifier(colorName, "color", "android")
            if (colorId != 0) {
                try {
                    return resources.getColor(colorId, context.theme)
                } catch (ignored: RuntimeException) {
                    // Try the next system palette entry or themed accent.
                }
            }
        }

        val value = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.colorAccent, value, true)) {
            if (value.resourceId != 0) {
                try {
                    return resources.getColor(value.resourceId, context.theme)
                } catch (ignored: RuntimeException) {
                    // Fall through to the resolved inline color or legacy fallback.
                }
            }
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
                value.type <= TypedValue.TYPE_LAST_COLOR_INT
            ) {
                return value.data
            }
        }
        return resolveFolderPreviewColor(context)
    }
}
