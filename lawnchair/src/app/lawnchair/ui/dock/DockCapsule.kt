package app.lawnchair.ui.dock

import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.BubbleTextView
import com.android.launcher3.CellLayout
import com.android.launcher3.Hotseat
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
    private val updateBounds = Runnable { updateBounds() }

    private fun dp(value: Int) = (value * density).roundToInt()

    private fun updateBounds() {
        val container = host.iconsContainer ?: return
        val width = container.width
        val height = container.height
        if (width <= 0 || height <= 0) return

        val iconBounds = measureIconRow(container) ?: return

        // Dock settings. The insets move the panel edges inward (the icon row stays put), matching
        // the original Lawnchair behaviour. With every inset at 0 the panel is centred on the icon
        // row, which keeps the approved iOS look.
        val prefs = PreferenceManager.getInstance(context)
        val prefs2 = PreferenceManager2.getInstance(context)
        if (!prefs.hotseatBG.get()) {
            // "Dock background" is off: leave the icons on a transparent row.
            container.background = null
            return
        }
        // The inset sliders are stored in pixels (as their "px" unit indicates).
        val insetLeft = prefs.hotseatBGHorizontalInsetLeft.get()
        val insetRight = prefs.hotseatBGHorizontalInsetRight.get()
        val insetTop = prefs.hotseatBGVerticalInsetTop.get()
        val insetBottom = prefs.hotseatBGVerticalInsetBottom.get()
        val sideMargin = DockCapsuleGeometry.sideMarginPx(density)
        val panelHeight = DockCapsuleGeometry.panelHeightPx(height, density)
        val top = (iconBounds.centerY() - panelHeight / 2)
            .coerceIn(0, (height - panelHeight).coerceAtLeast(0))
        val bottom = (height - top - panelHeight).coerceAtLeast(0)

        // The drawn height shrinks as the vertical insets grow, so the radius is clamped against the
        // real panel height to keep the sides straight.
        val drawnHeight = (height - (top + insetTop) - (bottom + insetBottom)).coerceAtLeast(1)
        val cornerRadius = DockCapsuleGeometry.preferredCornerRadiusPx(
            drawnHeight,
            density,
            prefs2.hotseatBackgroundCornerRadius.firstCached(),
        )

        // Liquid Glass: a near-transparent tinted surface plus a specular top/left edge highlight,
        // so the dock reads as thin glass rather than a solid bar.
        val panel = LiquidGlassDrawable(
            fillColor = resolveColor(),
            cornerRadiusPx = cornerRadius,
            borderWidthPx = dp(DockCapsuleGeometry.GLASS_BORDER_WIDTH_DP).coerceAtLeast(1).toFloat(),
        )
        val inset = InsetDrawable(
            panel,
            sideMargin + insetLeft,
            (top + insetTop).coerceAtLeast(0),
            sideMargin + insetRight,
            (bottom + insetBottom).coerceAtLeast(0),
        )
        container.background = splitFraction?.let { fraction ->
            LayerDrawable(arrayOf(inset, buildDivider(width, fraction)))
        } ?: inset
        // The dock background must never intercept gestures: empty areas fall through to the
        // workspace.
        container.isClickable = false
        container.isFocusable = false
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
        return InsetDrawable(
            line,
            splitX,
            dp(DIVIDER_VERTICAL_INSET_DP),
            (width - splitX - lineWidth).coerceAtLeast(0),
            dp(DIVIDER_VERTICAL_INSET_DP),
        )
    }

    private fun resolveColor(): Int {
        val prefs2 = PreferenceManager2.getInstance(context)
        val prefs = PreferenceManager.getInstance(context)
        val option = prefs2.hotseatBackgroundColor.firstCached()
        val base = option.colorPreferenceEntry.lightColor(context)
        // The opacity slider drives the glass tint directly. Its default keeps the approved
        // ultra-thin look so the wallpaper stays visible through the dock.
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
