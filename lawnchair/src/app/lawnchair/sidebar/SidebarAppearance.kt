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

/**
 * How the sidebar panel is painted.
 *
 * Ported from the standalone "Samart sidebar" app, which offered a theme choice plus an optional
 * custom panel colour. Kept Android-free; [SidebarView] maps these to real colours.
 */
enum class SidebarTheme {
    /** Follow the wallpaper-derived Material You surface, which is Lawnchair's default look. */
    TONAL,

    /** Solid, opaque surface. Icons stay readable over any wallpaper. */
    OPAQUE,

    /** Translucent surface, letting the wallpaper show through. */
    TRANSPARENT,
    ;

    companion object {
        /** Resolves a saved name, tolerating case and unknown values. */
        fun resolve(saved: String?): SidebarTheme = entries.firstOrNull {
            it.name.equals(saved?.trim(), ignoreCase = true)
        } ?: TONAL
    }
}

/**
 * The appearance switches that decide what the panel shows.
 *
 * Grouped so [SidebarView] reads one object instead of a dozen preference getters, and so the
 * derivation rules below can be tested without an Android context.
 */
data class SidebarAppearance(
    val theme: SidebarTheme = SidebarTheme.TONAL,
    val showLabels: Boolean = false,
    val showOutline: Boolean = true,
    val autoHideFullscreen: Boolean = false,
) {
    companion object {
        /**
         * Label size in dp for a panel of [columns] columns.
         *
         * Labels are the one thing that makes two columns unreadable, so the text shrinks as
         * columns are added rather than wrapping onto two lines and clipping.
         */
        fun labelSizeSp(columns: Int): Int = when (columns.coerceIn(1, 4)) {
            1 -> 11
            2 -> 10
            3 -> 9
            else -> 8
        }

        /**
         * Opacity the panel should actually use.
         *
         * A transparent theme ignores the opacity slider entirely: honouring both would let the
         * user pick "transparent" and then get an opaque panel, which reads as a broken switch.
         */
        fun effectiveOpacity(theme: SidebarTheme, requestedPercent: Int): Float = when (theme) {
            SidebarTheme.OPAQUE -> 1f
            SidebarTheme.TRANSPARENT -> 0.55f
            SidebarTheme.TONAL -> SidebarMetrics.opacityFraction(requestedPercent)
        }
    }
}
