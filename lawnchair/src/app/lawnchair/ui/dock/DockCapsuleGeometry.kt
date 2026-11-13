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
    const val SIDE_MARGIN_DP = 12

    /** Panel height, in dp. The panel is capped to this so it does not fill the whole container. */
    const val HEIGHT_DP = 96

    /** Corner radius as a fraction of the panel height (straight sides, rounded corners). */
    const val CORNER_RADIUS_RATIO = 0.24f

    /** Returns the panel height in px: the requested height, capped to the available space. */
    fun panelHeightPx(containerHeightPx: Int, density: Float): Int =
        minOf(containerHeightPx, (HEIGHT_DP * density).roundToInt())

    /**
     * Returns the corner radius in px for a panel of [panelHeightPx]. Proportional to the height
     * and always below half the height, so the panel keeps straight sides.
     */
    fun cornerRadiusPx(panelHeightPx: Int): Float = panelHeightPx * CORNER_RADIUS_RATIO

    /** Returns the side margin in px. */
    fun sideMarginPx(density: Float): Int = (SIDE_MARGIN_DP * density).roundToInt()
}
