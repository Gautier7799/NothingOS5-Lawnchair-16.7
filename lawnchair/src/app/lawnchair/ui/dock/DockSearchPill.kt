package app.lawnchair.ui.dock

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.lawnchair.qsb.LauncherSearch
import com.android.launcher3.Hotseat
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.views.BaseDragLayer
import kotlin.math.roundToInt

/**
 * Draws the small floating search pill that sits just above the iOS dock when the dock search bar
 * is turned off. Tapping it opens the launcher search.
 *
 * The pill floats in the gap above the dock panel, which is outside the dock's own bounds. A child
 * drawn outside its parent still paints when `clipChildren` is off, but it never receives touches,
 * so the pill is hosted by the DragLayer instead of the Hotseat. Because it is no longer a
 * descendant of the dock it does not inherit the dock's alpha, so `Hotseat` pushes its alpha down
 * explicitly.
 */
class DockSearchPill private constructor(private val host: Hotseat) {

    private val context = host.context
    private val density = context.resources.displayMetrics.density
    private val pill = LinearLayout(context)
    private val position = Runnable { position() }
    private val attachRunnable = Runnable { attach() }
    private var attachAttempts = 0
    private val containerLocation = IntArray(2)
    private val dragLayerLocation = IntArray(2)

    private fun dp(value: Int) = (value * density).roundToInt()

    private fun build() {
        val height = DockCapsuleGeometry.searchPillHeightPx(density)
        val horizontalPadding = dp(PILL_HORIZONTAL_PADDING_DP)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = Gravity.CENTER
        pill.setPadding(horizontalPadding, 0, horizontalPadding, 0)
        pill.background = glassBackground(height)
        // The pill is a direct child of the DragLayer, which requires BaseDragLayer.LayoutParams.
        // FrameLayout.LayoutParams is a different type, so DragLayer's inset dispatch would throw
        // ClassCastException while setting insets on it.
        pill.layoutParams = BaseDragLayer.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            height,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }

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
            LauncherSearch.open(Launcher.getLauncher(context))
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
        val parent = pill.parent as? View ?: return
        // The pill is a DragLayer sibling, so the dock has to be resolved in the DragLayer's
        // coordinate space before the pill can be placed above it.
        container.getLocationInWindow(containerLocation)
        parent.getLocationInWindow(dragLayerLocation)
        val containerTopInParent = containerLocation[1] - dragLayerLocation[1]
        val pillTop = containerTopInParent +
            DockCapsuleGeometry.searchPillTopPx(containerHeight, density)
        pill.translationY = pillTop.toFloat()
    }

    private fun attach() {
        if (pill.parent != null) return
        val container = host.iconsContainer ?: return
        // The Hotseat is inflated before the Launcher resolves its DragLayer, so the first attempt
        // usually has no host yet. Retry on the next message-loop turn.
        val dragLayer = (context as? Launcher)?.dragLayer
        if (dragLayer == null) {
            if (attachAttempts++ < MAX_ATTACH_ATTEMPTS) host.post(attachRunnable)
            return
        }
        dragLayer.addView(pill)
        container.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> position.run() }
        host.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> position.run() }
        container.post(position)
    }

    /** The pill view, so the host can hide and show it with the rest of the search bar. */
    val view: View get() = pill

    companion object {
        private const val PILL_HORIZONTAL_PADDING_DP = 12
        private const val ICON_SIZE_DP = 14
        private const val LABEL_MARGIN_DP = 4
        private const val LABEL_TEXT_SIZE_SP = 12f
        private const val MAX_ATTACH_ATTEMPTS = 30
        private val EDGE_COLOR = Color.argb(90, 255, 255, 255)

        /** Adds the floating search pill above the dock and returns it. */
        @JvmStatic
        fun apply(host: Hotseat): View {
            val pill = DockSearchPill(host)
            pill.build()
            pill.attach()
            return pill.view
        }
    }
}
