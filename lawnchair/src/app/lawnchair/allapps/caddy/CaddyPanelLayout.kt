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

/**
 * Android-free sizing rules for the Caddy category panel.
 *
 * The numbers mirror the panel the category card has always shown: the same chrome,
 * row cost and label reserve, so the overlay looks identical to the old dialog. The
 * arithmetic lives here only so the clamping can be tested on the JVM.
 */
object CaddyPanelLayout {

    /** Width ceiling, in dp, so the panel does not stretch on tablets. */
    const val MAX_WIDTH_DP = 560

    /** Total horizontal margin, in dp, split evenly between the two sides. */
    const val SIDE_MARGIN_DP = 16

    /** Smallest height, in dp, a panel may take. */
    const val MIN_HEIGHT_DP = 300

    /** Vertical space, in dp, kept free above and below the panel. */
    const val VERTICAL_RESERVE_DP = 176

    /** Panel height, in dp, contributed by the title and the outer padding. */
    const val CHROME_HEIGHT_DP = 76

    /** Height of one app row, in dp: icon frame, label and cell padding. */
    const val ROW_HEIGHT_DP = 96

    /** Height, in dp, of the trailing label under the last row. */
    const val LABEL_RESERVE_DP = 34

    /**
     * Width of the panel for a host of [hostWidthPx].
     *
     * The panel keeps [SIDE_MARGIN_DP] on each side and stops growing at
     * [MAX_WIDTH_DP], so it stays centred and readable on tablets.
     */
    @JvmStatic
    fun widthPx(hostWidthPx: Int, density: Float): Int {
        val available = hostWidthPx - dp(SIDE_MARGIN_DP * 2, density)
        val cap = dp(MAX_WIDTH_DP, density)
        return minOf(available, cap).coerceAtLeast(0)
    }

    /** Horizontal padding, in dp, the panel applies on each side. */
    const val PANEL_PADDING_H_DP = 20

    /** Width, in dp, of one app cell's outer margins, both sides combined. */
    const val CELL_MARGIN_DP = 4

    /**
     * Width of one app cell for a panel of [panelWidthPx].
     *
     * Cells are laid out at a fixed width rather than as zero-width weighted columns.
     * A weighted column only resolves once the grid itself has a resolved width, so any
     * layout pass that measures the grid before the panel does collapses every column to
     * zero and stacks the icons on one point. Computing the width here removes that
     * dependency: the grid gets a definite size on its very first measure.
     */
    @JvmStatic
    fun cellWidthPx(panelWidthPx: Int, columns: Int, density: Float): Int {
        val safeColumns = columns.coerceAtLeast(1)
        val content = panelWidthPx - dp(PANEL_PADDING_H_DP * 2, density)
        return (content / safeColumns - dp(CELL_MARGIN_DP, density)).coerceAtLeast(0)
    }

    /**
     * Height of the panel for [appCount] apps laid out in [columns] columns on a host of
     * [hostHeightPx].
     *
     * Identical to what the dialog computed: the content height grows with the number of
     * rows, then is clamped to the larger of [MIN_HEIGHT_DP] and the host less
     * [VERTICAL_RESERVE_DP]. The floor is on the ceiling, not on the result, so tall
     * content still gets the full workspace-free band.
     */
    @JvmStatic
    fun heightPx(appCount: Int, columns: Int, hostHeightPx: Int, density: Float): Int {
        val safeColumns = columns.coerceAtLeast(1)
        val rows = (appCount.coerceAtLeast(0) + safeColumns - 1) / safeColumns
        val desired = dp(CHROME_HEIGHT_DP, density) +
            rows * dp(ROW_HEIGHT_DP, density) +
            dp(LABEL_RESERVE_DP, density)
        val ceiling = maxOf(
            dp(MIN_HEIGHT_DP, density),
            hostHeightPx - dp(VERTICAL_RESERVE_DP, density),
        )
        return minOf(desired, ceiling)
    }

    private fun dp(value: Int, density: Float): Int = (value * density).toInt()
}
