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
    const val SIDE_MARGIN_DP = 4

    /** Preferred panel height, in dp. Capped to the available container height. */
    const val HEIGHT_DP = 124

    /** Extra height added to the dock bar so the panel is taller than the icon row, in dp. */
    const val EXTRA_HEIGHT_DP = 36

    /** Padding between the panel edge and the icon row, in dp. */
    const val INNER_PADDING_DP = 12

    /** Corner radius in dp (iOS-style continuous corners, not a full pill). */
    const val CORNER_RADIUS_DP = 38

    /** Upper bound on the corner radius as a fraction of the height (keeps the sides straight). */
    private const val MAX_CORNER_RADIUS_RATIO = 0.45f

    /** Returns the preferred panel height in px, ignoring the container. */
    fun preferredHeightPx(density: Float): Int = (HEIGHT_DP * density).roundToInt()

    /** Returns the panel height in px: the preferred height, capped to the available space. */
    fun panelHeightPx(containerHeightPx: Int, density: Float): Int =
        minOf(containerHeightPx, preferredHeightPx(density))

    /**
     * Returns the corner radius in px for a panel of [panelHeightPx]. Uses a fixed iOS-style
     * radius, clamped below half the height so the panel keeps straight sides.
     */
    fun cornerRadiusPx(panelHeightPx: Int, density: Float): Float =
        minOf(CORNER_RADIUS_DP * density, panelHeightPx * MAX_CORNER_RADIUS_RATIO)

    /** Returns the side margin in px. */
    fun sideMarginPx(density: Float): Int = (SIDE_MARGIN_DP * density).roundToInt()

    /** Returns the icon-row padding inside the panel, in px. */
    fun innerPaddingPx(density: Float): Int = (INNER_PADDING_DP * density).roundToInt()

    /** Returns the total container padding applied on each side, in px. */
    fun containerPaddingPx(density: Float): Int = sideMarginPx(density) + innerPaddingPx(density)

    /** Returns the extra dock-bar height, in px. */
    fun extraHeightPx(density: Float): Int = (EXTRA_HEIGHT_DP * density).roundToInt()
}
