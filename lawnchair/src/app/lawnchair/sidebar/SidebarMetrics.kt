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
 * Android-free geometry for the home-screen sidebar.
 *
 * The numbers come from the standalone sidebar prototype so the panel that slides out of the
 * edge looks the same here: an 88 dp column, 28 dp corners, 50 dp icons. Everything that can
 * be computed without a `Context` lives here so the arithmetic is testable on the JVM, which
 * is where the prototype's overlapping icons came from.
 */
object SidebarMetrics {

    /** Width of the sliding panel, in dp. Matches the prototype's 88 dp column. */
    const val PANEL_WIDTH_DP = 88

    /** Corner radius of the panel, in dp. */
    const val PANEL_RADIUS_DP = 28

    /** Size of one app icon, in dp. */
    const val ICON_SIZE_DP = 50

    /** Corner radius of a control tile, in dp. */
    const val TILE_RADIUS_DP = 12

    /** Width of the pull-tab handle, in dp. */
    const val HANDLE_WIDTH_DP = 8

    /** Height of the pull-tab handle, in dp. */
    const val HANDLE_HEIGHT_DP = 44

    /** Vertical padding inside the panel's scrolling column, in dp. */
    const val PANEL_PADDING_V_DP = 8

    /** Horizontal padding inside the panel's scrolling column, in dp. */
    const val PANEL_PADDING_H_DP = 7

    /** Height of one app cell, in dp: icon plus its padding. */
    const val CELL_HEIGHT_DP = 66

    /** Gap, in dp, kept between the panel and the screen edge. */
    const val EDGE_GAP_DP = 8

    /** Width, in dp, one extra column adds to the panel. */
    const val CELL_WIDTH_DP = 66

    /** Fewest columns the panel may show. */
    const val MIN_COLUMNS = 1

    /** Most columns the panel may show. */
    const val MAX_COLUMNS = 4

    /** Default panel opacity, as a percentage. */
    const val DEFAULT_OPACITY_PERCENT = 100

    /** Lowest opacity the panel accepts; below this the icons stop being readable. */
    const val MIN_OPACITY_PERCENT = 40

    /** Smallest height, in dp, the panel may take. */
    const val MIN_PANEL_HEIGHT_DP = 240

    /** Vertical space, in dp, kept clear so the panel clears the status bar and the dock. */
    const val VERTICAL_RESERVE_DP = 120

    /** Horizontal touch slop around the handle, in dp. */
    const val HANDLE_SLOP_X_DP = 24

    /** Vertical touch slop around the handle, in dp. */
    const val HANDLE_SLOP_Y_DP = 40

    /**
     * Height of the panel for [itemCount] entries on a host [hostHeightPx] tall.
     *
     * The panel grows with its content, is never shorter than [MIN_PANEL_HEIGHT_DP] while the
     * host has room for it, and never taller than the host less [VERTICAL_RESERVE_DP], so a
     * long list scrolls instead of running under the status bar or the dock. When the host is
     * too short to leave any room at all the result is zero: a panel cannot be drawn in space
     * that does not exist, and clamping to the minimum would push it off the screen.
     */
    @JvmStatic
    fun panelHeightPx(itemCount: Int, hostHeightPx: Int, density: Float): Int {
        val available = hostHeightPx - dp(VERTICAL_RESERVE_DP, density)
        if (available <= 0) return 0
        val content = dp(PANEL_PADDING_V_DP * 2, density) +
            itemCount.coerceAtLeast(0) * dp(CELL_HEIGHT_DP, density)
        val floor = minOf(dp(MIN_PANEL_HEIGHT_DP, density), available)
        return content.coerceIn(floor, available)
    }

    /**
     * Translation, in px, the panel carries when it is fully open.
     *
     * The panel is laid out flush with its edge and translated by this amount to slide in,
     * so the closed state is this value and the open state is `0`. The sign follows the edge.
     */
    @JvmStatic
    fun slideDistancePx(onRightEdge: Boolean, density: Float): Float {
        val width = dp(PANEL_WIDTH_DP, density).toFloat()
        return if (onRightEdge) width else -width
    }

    /** Whether a touch is close enough to the handle to count as grabbing it. */
    @JvmStatic
    fun isWithinHandleSlop(dx: Float, dy: Float, density: Float): Boolean {
        return kotlin.math.abs(dx) <= dp(HANDLE_SLOP_X_DP, density) &&
            kotlin.math.abs(dy) <= dp(HANDLE_SLOP_Y_DP, density)
    }

    /** Clamps a requested column count into the supported range. */
    @JvmStatic
    fun clampColumns(requested: Int): Int = requested.coerceIn(MIN_COLUMNS, MAX_COLUMNS)

    /**
     * Panel width, in dp, for [columns] columns.
     *
     * The first column is [PANEL_WIDTH_DP]; each extra one adds a cell, so a two-column panel
     * is wide enough for two icons side by side without squeezing them.
     */
    @JvmStatic
    fun panelWidthPx(columns: Int, density: Float): Int {
        val extra = clampColumns(columns) - 1
        return dp(PANEL_WIDTH_DP + extra * CELL_WIDTH_DP, density)
    }

    /**
     * Largest column count whose panel still fits [screenWidthPx].
     *
     * A narrow screen must not get a panel wider than the display, so the request is capped
     * rather than honoured blindly.
     */
    @JvmStatic
    fun maxColumnsForScreen(screenWidthPx: Int, density: Float): Int {
        var best = MIN_COLUMNS
        for (columns in MIN_COLUMNS..MAX_COLUMNS) {
            if (panelWidthPx(columns, density) <= screenWidthPx) best = columns
        }
        return best
    }

    /**
     * Centre, in px from the top of the host, of the edge handle.
     *
     * [fraction] is 0 at the top edge and 1 at the bottom; the result is clamped so the whole
     * handle stays inside the host even at either extreme.
     */
    @JvmStatic
    fun handleCenterPx(fraction: Float, hostHeightPx: Int, handleHeightPx: Int): Int {
        val centre = (hostHeightPx * fraction.coerceIn(0f, 1f)).toInt()
        val half = handleHeightPx / 2
        return centre.coerceIn(half, (hostHeightPx - half).coerceAtLeast(half))
    }

    /** Converts an opacity percentage into the alpha the panel should use. */
    @JvmStatic
    fun opacityFraction(percent: Int): Float = percent.coerceIn(MIN_OPACITY_PERCENT, 100) / 100f

    /**
     * Whether a horizontal drag of [deltaX] px opens the panel from the given edge.
     *
     * The tab is pulled inward, so a positive delta (finger moving right) opens on the left
     * edge and a negative one opens on the right edge. Anything shorter than [slopPx] is a
     * tap, not a pull.
     */
    @JvmStatic
    fun isOpenPull(deltaX: Float, onRightEdge: Boolean, slopPx: Float): Boolean {
        val inward = if (onRightEdge) -deltaX else deltaX
        return inward > slopPx
    }

    /** Whether a panel this tall is worth showing on a host of [hostHeightPx]. */
    @JvmStatic
    fun isPanelUsable(heightPx: Int): Boolean = heightPx > 0

    private fun dp(value: Int, density: Float): Int = (value * density).toInt()
}
