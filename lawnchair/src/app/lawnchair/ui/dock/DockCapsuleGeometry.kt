package app.lawnchair.ui.dock

import kotlin.math.roundToInt

/**
 * Pure geometry for the iOS-style dock capsule. Kept free of Android types so it can be unit
 * tested on the JVM.
 *
 * The dock is a rounded rectangle with straight sides: the corner radius is a fixed dp value
 * (iOS style) clamped below half the height, so it never grows into a pill on a short container.
 */
object DockCapsuleGeometry {

    /** Side margin from the screen edge to the panel, in dp. */
    const val SIDE_MARGIN_DP = 2

    /** Preferred panel height, in dp. Capped to the available container height. */
    const val HEIGHT_DP = 90

    /** Extra height added to the dock bar so the panel is taller than the icon row, in dp. */
    const val EXTRA_HEIGHT_DP = 44

    /** Padding between the panel edge and the icon row, in dp. */
    const val INNER_PADDING_DP = 12

    /** Corner radius in dp (iOS-style continuous corners, not a full pill). */
    const val CORNER_RADIUS_DP = 32

    /** Gap between the dock panel and the bottom navigation bar, in dp. */
    const val BOTTOM_MARGIN_DP = 12

    /** Height of the floating search pill above the dock, in dp. */
    const val SEARCH_PILL_HEIGHT_DP = 28

    /** Gap between the search pill and the dock panel, in dp. */
    const val SEARCH_PILL_GAP_DP = 14

    /** Width of the specular edge highlight, in dp. */
    const val GLASS_BORDER_WIDTH_DP = 1

    /**
     * Opacity of the ultra-thin glass tint, as a percentage. Keeps the wallpaper visible through
     * the dock (iOS 26/27 Liquid Glass).
     */
    const val GLASS_TINT_PERCENT = 14

    /** Upper bound on the corner radius as a fraction of the height (keeps the sides straight). */
    private const val MAX_CORNER_RADIUS_RATIO = 0.45f

    /** Returns the preferred panel height in px, ignoring the container. */
    fun preferredHeightPx(density: Float): Int = (HEIGHT_DP * density).roundToInt()

    /** Returns the panel height in px: the preferred height, capped to the available space. */
    fun panelHeightPx(containerHeightPx: Int, density: Float): Int = minOf(containerHeightPx, preferredHeightPx(density))

    /**
     * Returns the corner radius in px for a panel of [panelHeightPx]. Uses a fixed iOS-style
     * radius, clamped below half the height so the panel keeps straight sides.
     */
    fun cornerRadiusPx(panelHeightPx: Int, density: Float): Float = minOf(CORNER_RADIUS_DP * density, panelHeightPx * MAX_CORNER_RADIUS_RATIO)

    /**
     * Returns the corner radius (px) for the dock panel. The user's [userRadiusDp] overrides the
     * built-in iOS default; a null/negative value keeps the approved [CORNER_RADIUS_DP] look. The
     * result is always clamped below half the height so the sides stay straight.
     */
    fun preferredCornerRadiusPx(panelHeightPx: Int, density: Float, userRadiusDp: Float?): Float {
        val dp = userRadiusDp?.takeIf { it >= 0f } ?: CORNER_RADIUS_DP.toFloat()
        return minOf(dp * density, panelHeightPx * MAX_CORNER_RADIUS_RATIO)
    }

    /** Returns the side margin in px. */
    fun sideMarginPx(density: Float): Int = (SIDE_MARGIN_DP * density).roundToInt()

    /** Returns the icon-row padding inside the panel, in px. */
    fun innerPaddingPx(density: Float): Int = (INNER_PADDING_DP * density).roundToInt()

    /** Returns the total container padding applied on each side, in px. */
    fun containerPaddingPx(density: Float): Int = sideMarginPx(density) + innerPaddingPx(density)

    /** Returns the extra dock-bar height, in px. */
    fun extraHeightPx(density: Float): Int = (EXTRA_HEIGHT_DP * density).roundToInt()

    /** Returns the gap to the navigation bar, in px. */
    fun bottomMarginPx(density: Float): Int = (BOTTOM_MARGIN_DP * density).roundToInt()

    /** Returns the floating search pill height, in px. */
    fun searchPillHeightPx(density: Float): Int = (SEARCH_PILL_HEIGHT_DP * density).roundToInt()

    /** Returns the gap between the search pill and the dock, in px. */
    fun searchPillGapPx(density: Float): Int = (SEARCH_PILL_GAP_DP * density).roundToInt()
}
