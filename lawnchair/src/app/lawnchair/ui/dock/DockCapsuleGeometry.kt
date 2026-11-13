package app.lawnchair.ui.dock

import kotlin.math.roundToInt

/**
 * Pure geometry for the iOS-style dock capsule. Kept free of Android types so it can be unit
 * tested on the JVM.
 *
 * The dock is a rounded rectangle with straight sides: the corner radius is a fixed fraction of
 * the panel height, so it never grows into a pill (which is what happens when the radius is
 * clamped to half the height on a short container).
 */
object DockCapsuleGeometry {

    /** Side margin from the screen edge to the panel, in dp. */
    const val SIDE_MARGIN_DP = 6

    /** Preferred panel height, in dp. Capped to the available container height. */
    const val HEIGHT_DP = 116

    /** Extra height added to the dock bar so the panel is taller than the icon row, in dp. */
    const val EXTRA_HEIGHT_DP = 28

    /** Padding between the panel edge and the icon row, in dp. */
    const val INNER_PADDING_DP = 12

    /** Corner radius as a fraction of the panel height (straight sides, rounded corners). */
    const val CORNER_RADIUS_RATIO = 0.24f

    /** Returns the preferred panel height in px, ignoring the container. */
    fun preferredHeightPx(density: Float): Int = (HEIGHT_DP * density).roundToInt()

    /** Returns the panel height in px: the preferred height, capped to the available space. */
    fun panelHeightPx(containerHeightPx: Int, density: Float): Int =
        minOf(containerHeightPx, preferredHeightPx(density))

    /**
     * Returns the corner radius in px for a panel of [panelHeightPx]. Proportional to the height
     * and always below half the height, so the panel keeps straight sides.
     */
    fun cornerRadiusPx(panelHeightPx: Int): Float = panelHeightPx * CORNER_RADIUS_RATIO

    /** Returns the side margin in px. */
    fun sideMarginPx(density: Float): Int = (SIDE_MARGIN_DP * density).roundToInt()

    /** Returns the icon-row padding inside the panel, in px. */
    fun innerPaddingPx(density: Float): Int = (INNER_PADDING_DP * density).roundToInt()

    /** Returns the total container padding applied on each side, in px. */
    fun containerPaddingPx(density: Float): Int = sideMarginPx(density) + innerPaddingPx(density)

    /** Returns the extra dock-bar height, in px. */
    fun extraHeightPx(density: Float): Int = (EXTRA_HEIGHT_DP * density).roundToInt()
}
