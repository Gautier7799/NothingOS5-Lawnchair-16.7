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
 * The panel is a floating overlay inside the launcher's DragLayer, not a dialog window.
 * Its bounds therefore have to be computed from the host size the launcher reports
 * rather than from window metrics, and they must stay inside the host on every device.
 * Keeping the arithmetic here lets the clamping rules be tested on the JVM.
 */
object CaddyPanelLayout {

    /** Fallback width ceiling, in dp, so the panel does not stretch on tablets. */
    const val MAX_WIDTH_DP = 560

    /** Horizontal gap, in dp, kept between the panel and the host edges. */
    const val SIDE_MARGIN_DP = 16

    /** Minimum height, in dp, so a one-app panel is still comfortable to tap. */
    const val MIN_HEIGHT_DP = 180

    /** Vertical space, in dp, reserved above and below the panel for the workspace. */
    const val VERTICAL_RESERVE_DP = 176

    /** Panel height, in dp, contributed by the title and the outer padding. */
    const val CHROME_HEIGHT_DP = 76

    /** Height of one app row, in dp: icon, label, cell padding and grid margins. */
    const val ROW_HEIGHT_DP = 110

    /** Height, in dp, of the trailing label under the last row. */
    const val LABEL_RESERVE_DP = 34

    /**
     * Width of the panel for a host of [hostWidthPx].
     *
     * The panel never touches the host edges: it keeps [SIDE_MARGIN_DP] on each side and
     * stops growing at [MAX_WIDTH_DP], so it stays centred and readable on tablets.
     */
    @JvmStatic
    fun widthPx(hostWidthPx: Int, density: Float): Int {
        val available = hostWidthPx - dp(SIDE_MARGIN_DP, density) * 2
        val cap = dp(MAX_WIDTH_DP, density)
        return minOf(available, cap).coerceAtLeast(0)
    }

    /**
     * Height of the panel for [appCount] apps laid out in [columns] columns on a host of
     * [hostHeightPx].
     *
     * The content height grows with the number of rows, then is clamped so the panel always
     * leaves [VERTICAL_RESERVE_DP] above and below for the workspace. The result never drops
     * below [MIN_HEIGHT_DP] unless the host itself is shorter than that.
     */
    @JvmStatic
    fun heightPx(appCount: Int, columns: Int, hostHeightPx: Int, density: Float): Int {
        val safeColumns = columns.coerceAtLeast(1)
        val rows = (appCount.coerceAtLeast(0) + safeColumns - 1) / safeColumns
        val desired = dp(CHROME_HEIGHT_DP, density) +
            rows * dp(ROW_HEIGHT_DP, density) +
            dp(LABEL_RESERVE_DP, density)
        val ceiling = hostHeightPx - dp(VERTICAL_RESERVE_DP, density)
        val floor = minOf(dp(MIN_HEIGHT_DP, density), hostHeightPx.coerceAtLeast(0))
        return minOf(desired, ceiling.coerceAtLeast(floor)).coerceAtLeast(floor)
    }

    private fun dp(value: Int, density: Float): Int = (value * density).toInt()
}
