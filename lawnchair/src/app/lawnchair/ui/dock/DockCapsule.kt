package app.lawnchair.ui.dock

import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import com.android.launcher3.BubbleTextView
import com.android.launcher3.CellLayout
import com.android.launcher3.Hotseat
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import kotlin.math.roundToInt

/**
 * Draws the iOS-style dock capsule behind a [Hotseat]'s icon row.
 *
 * The capsule is sized from the real dock icons rather than the container, so it hugs the row
 * for any number of icons. Geometry lives in [DockCapsuleGeometry]: side margins, panel height
 * and a corner radius that is a fixed fraction of the height (straight sides).
 *
 * Usage:
 * ```
 * DockCapsule.apply(this)          // standard iPhone dock
 * DockCapsule.apply(this, 0.7f)    // iPad-style divider at 70% of the panel width
 * ```
 */
class DockCapsule private constructor(
    private val host: Hotseat,
    /** Fraction of the panel width for an iPad-style divider, or null for a plain dock. */
    private val splitFraction: Float?,
) {
    private val context = host.context
    private val density = context.resources.displayMetrics.density
    private val background = GradientDrawable()
    private val updateBounds = Runnable { updateBounds() }

    private fun dp(value: Int) = (value * density).roundToInt()

    private fun updateBounds() {
        val container = host.iconsContainer ?: return
        val width = container.width
        val height = container.height
        if (width <= 0 || height <= 0) return

        val iconBounds = measureIconRow(container) ?: return

        val sideMargin = DockCapsuleGeometry.sideMarginPx(density)
        val panelHeight = DockCapsuleGeometry.panelHeightPx(height, density)
        val top = (iconBounds.centerY() - panelHeight / 2)
            .coerceIn(0, (height - panelHeight).coerceAtLeast(0))
        val bottom = height - top - panelHeight

        val base = background.apply {
            setColor(resolveColor())
            // Straight sides with a small corner radius, so the panel reads as a rounded
            // rectangle rather than a pill.
            cornerRadius = DockCapsuleGeometry.cornerRadiusPx(panelHeight, density)
        }
        val panel = InsetDrawable(base, sideMargin, top, sideMargin, bottom)
        container.background = splitFraction?.let { fraction ->
            LayerDrawable(arrayOf(panel, buildDivider(width, fraction)))
        } ?: panel
    }

    private fun measureIconRow(container: ViewGroup): Rect? {
        val cellLayout: CellLayout = host.pagedView.getCurrentCellLayout() ?: return null
        val shortcuts = cellLayout.shortcutsAndWidgets ?: return null
        if (shortcuts.childCount == 0) return null

        val bounds = Rect()
        var found = false
        for (i in 0 until shortcuts.childCount) {
            val child = shortcuts.getChildAt(i) ?: continue
            if (child.visibility != View.VISIBLE) continue
            val childBounds = Rect()
            if (child is BubbleTextView) {
                child.getIconBounds(childBounds)
            } else {
                childBounds.set(0, 0, child.width, child.height)
            }
            if (childBounds.isEmpty) continue
            container.offsetDescendantRectToMyCoords(child, childBounds)
            if (!found) {
                bounds.set(childBounds)
                found = true
            } else {
                bounds.union(childBounds)
            }
        }
        return if (found && !bounds.isEmpty) bounds else null
    }

    private fun buildDivider(width: Int, fraction: Float): InsetDrawable {
        val lineWidth = dp(DIVIDER_WIDTH_DP).coerceAtLeast(1)
        val splitX = (width * fraction).roundToInt().coerceIn(0, (width - lineWidth).coerceAtLeast(0))
        val line = GradientDrawable().apply {
            setColor(DIVIDER_COLOR)
            setSize(lineWidth, 0)
        }
        return InsetDrawable(line, splitX, dp(DIVIDER_VERTICAL_INSET_DP),
                (width - splitX - lineWidth).coerceAtLeast(0), dp(DIVIDER_VERTICAL_INSET_DP))
    }

    private fun resolveColor(): Int {
        val prefs2 = PreferenceManager2.getInstance(context)
        val prefs = PreferenceManager.getInstance(context)
        val option = prefs2.hotseatBackgroundColor.firstCached()
        val base = option.colorPreferenceEntry.lightColor(context)
        val alpha = ((prefs.hotseatBGAlpha.get() * 255) / 100).coerceIn(0, 255)
        return Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
    }

    private fun attach() {
        val container = host.iconsContainer ?: return
        container.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateBounds.run() }
        host.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateBounds.run() }
        container.post(updateBounds)
    }

    companion object {
        private const val DIVIDER_WIDTH_DP = 1
        private const val DIVIDER_VERTICAL_INSET_DP = 24
        private val DIVIDER_COLOR = Color.argb(72, 128, 128, 128)

        /** Applies the standard iPhone dock capsule. */
        @JvmStatic
        fun apply(host: Hotseat) {
            DockCapsule(host, null).attach()
        }

        /**
         * Applies the dock capsule with an iPad-style divider drawn at [splitFraction] of the
         * panel width (for example `0.7f` for 70%).
         */
        @JvmStatic
        fun apply(host: Hotseat, splitFraction: Float) {
            DockCapsule(host, splitFraction).attach()
        }
    }
}
