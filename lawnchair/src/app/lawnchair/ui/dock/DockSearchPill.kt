package app.lawnchair.ui.dock

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.android.launcher3.Hotseat
import com.android.launcher3.Launcher
import com.android.launcher3.R
import kotlin.math.roundToInt

/**
 * Draws the small floating search pill that sits just above the iOS dock when the dock search bar
 * is turned off. Tapping it opens the launcher search.
 */
class DockSearchPill private constructor(private val host: Hotseat) {

    private val context = host.context
    private val density = context.resources.displayMetrics.density
    private val pill = LinearLayout(context)
    private val position = Runnable { position() }

    private fun dp(value: Int) = (value * density).roundToInt()

    private fun build() {
        val height = DockCapsuleGeometry.searchPillHeightPx(density)
        val horizontalPadding = dp(PILL_HORIZONTAL_PADDING_DP)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = Gravity.CENTER
        pill.setPadding(horizontalPadding, 0, horizontalPadding, 0)
        pill.background = glassBackground(height)
        pill.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            height,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        )

        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_search)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(ICON_SIZE_DP), dp(ICON_SIZE_DP))
        }
        val label = TextView(context).apply {
            text = context.getString(R.string.label_search)
            setTextColor(Color.WHITE)
            textSize = LABEL_TEXT_SIZE_SP
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(LABEL_MARGIN_DP) }
        }
        pill.addView(icon)
        pill.addView(label)
        pill.setOnClickListener {
            Launcher.getLauncher(context).startSearch("", false, null, true)
        }
    }

    /** Ultra-thin glass capsule: a faint tint with a hairline white edge. */
    private fun glassBackground(heightPx: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = heightPx / 2f
        setColor(Color.argb(56, 255, 255, 255))
        setStroke(dp(DockCapsuleGeometry.GLASS_BORDER_WIDTH_DP).coerceAtLeast(1), EDGE_COLOR)
    }

    private fun position() {
        val container = host.iconsContainer ?: return
        val containerHeight = container.height
        if (containerHeight <= 0) return
        val panelHeight = DockCapsuleGeometry.panelHeightPx(containerHeight, density)
        val panelTop = (containerHeight - panelHeight) / 2
        val pillHeight = DockCapsuleGeometry.searchPillHeightPx(density)
        val gap = DockCapsuleGeometry.searchPillGapPx(density)
        // The pill floats in the gap above the panel; the container clips to padding off, so it can
        // overflow upward.
        pill.translationY = (panelTop - gap - pillHeight - container.paddingTop).toFloat()
    }

    private fun attach() {
        val container = host.iconsContainer ?: return
        if (pill.parent != null) return
        container.addView(pill)
        container.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> position.run() }
        container.post(position)
    }

    companion object {
        private const val PILL_HORIZONTAL_PADDING_DP = 12
        private const val ICON_SIZE_DP = 14
        private const val LABEL_MARGIN_DP = 4
        private const val LABEL_TEXT_SIZE_SP = 12f
        private val EDGE_COLOR = Color.argb(90, 255, 255, 255)

        /** Adds the floating search pill above the dock. */
        @JvmStatic
        fun apply(host: Hotseat) {
            DockSearchPill(host).apply {
                build()
                attach()
            }
        }
    }
}
