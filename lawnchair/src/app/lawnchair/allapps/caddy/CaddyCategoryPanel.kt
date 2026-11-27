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

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.DropTarget
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.touch.ItemClickHandler
import com.android.launcher3.touch.ItemLongClickListener
import com.android.launcher3.views.BaseDragLayer

/**
 * The panel shown when a Caddy category card is opened.
 *
 * The layout is a direct port of the dialog this replaces: the same glass surface, icon
 * frame, label metrics, dim and open animation, so the two look identical. The one
 * difference is the host. A dialog owns a separate window, which breaks dragging an app
 * onto the workspace: `DragPreviewProvider` cannot map a non-DragLayer view, and the
 * move/up events go to the dialog window instead of the drag driver. Hosting the panel
 * as an [AbstractFloatingView] inside the DragLayer keeps one coordinate space and one
 * touch stream, so the drag completes.
 */
class CaddyCategoryPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractFloatingView(context, attrs, defStyleAttr) {

    private val launcher: Launcher = Launcher.getLauncher(context)

    private val scrim = View(context)
    private val panel = LinearLayout(context)
    private var appCount = 0

    init {
        orientation = VERTICAL
        isClickable = true
        isFocusable = true
        clipChildren = false
        clipToPadding = false

        scrim.setBackgroundColor(Color.argb(DIM_ALPHA, 0, 0, 0))
        scrim.setOnClickListener { close(true) }

        panel.orientation = VERTICAL
        panel.setPadding(dp(20f), dp(18f), dp(20f), dp(16f))
        panel.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.argb(246, Color.red(glassTop()), Color.green(glassTop()), Color.blue(glassTop())),
                Color.argb(250, Color.red(glassBase()), Color.green(glassBase()), Color.blue(glassBase())),
                Color.argb(252, Color.red(glassBottom()), Color.green(glassBottom()), Color.blue(glassBottom())),
            ),
        ).apply {
            cornerRadius = dp(30f).toFloat()
            setStroke(dp(1f), Color.argb(48, 255, 255, 255))
        }
        panel.clipToOutline = true
        panel.elevation = dp(12f).toFloat()
        panel.isClickable = true

        val content = FrameLayout(context)
        content.addView(
            scrim,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        content.addView(
            panel,
            FrameLayout.LayoutParams(
                CaddyPanelLayout.widthPx(
                    resources.displayMetrics.widthPixels,
                    resources.displayMetrics.density,
                ),
                CaddyPanelLayout.heightPx(
                    0,
                    COLUMN_COUNT,
                    resources.displayMetrics.heightPixels,
                    resources.displayMetrics.density,
                ),
                Gravity.CENTER,
            ),
        )
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun glassBase() = Color.rgb(27, 35, 37)

    private fun glassTop() = Color.rgb(39, 49, 51)

    private fun glassBottom() = Color.rgb(25, 33, 35)

    /** Fills the panel with [apps] under [title] and sizes it to the host. */
    fun bind(title: CharSequence?, apps: List<AppInfo>) {
        panel.removeAllViews()

        val density = resources.displayMetrics.density
        val panelWidth = CaddyPanelLayout.widthPx(resources.displayMetrics.widthPixels, density)
        (panel.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.width = panelWidth
            it.height = CaddyPanelLayout.heightPx(
                apps.size,
                COLUMN_COUNT,
                resources.displayMetrics.heightPixels,
                density,
            )
        }

        val heading = TextView(context)
        heading.text = title
        heading.setTextColor(Color.WHITE)
        heading.setTextSize(20f)
        heading.typeface = Typeface.DEFAULT_BOLD
        heading.setPadding(0, 0, 0, dp(14f))
        panel.addView(
            heading,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val scrollView = ScrollView(context)
        scrollView.isFillViewport = false
        panel.addView(
            scrollView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        val grid = LinearLayout(context)
        grid.orientation = LinearLayout.VERTICAL
        scrollView.addView(
            grid,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val cellWidth = CaddyPanelLayout.cellWidthPx(panelWidth, COLUMN_COUNT, density)
        apps.chunked(COLUMN_COUNT).forEach { rowApps ->
            val row = LinearLayout(context)
            row.orientation = LinearLayout.HORIZONTAL
            grid.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            rowApps.forEach { app -> row.addView(appCell(app), cellParams(cellWidth)) }
            // Keep the last row's cells the same width as every other row's.
            repeat(COLUMN_COUNT - rowApps.size) { row.addView(SpacerView(context), cellParams(cellWidth)) }
        }

        appCount = apps.size
        requestLayout()
    }

    /** One app, built exactly as the dialog built it. */
    private fun appCell(app: AppInfo): LinearLayout {
        val cell = LinearLayout(context)
        cell.orientation = LinearLayout.VERTICAL
        cell.gravity = Gravity.CENTER
        cell.setPadding(dp(3f), dp(8f), dp(3f), dp(8f))

        val iconFrame = FrameLayout(context)
        cell.addView(
            iconFrame,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(68f)),
        )

        val icon = LayoutInflater.from(context)
            .inflate(R.layout.all_apps_icon, iconFrame, false) as BubbleTextView
        icon.applyFromApplicationInfo(app)
        icon.setTextVisibility(false)
        icon.setLongPressTimeoutFactor(1f)
        icon.contentDescription = app.title
        icon.setOnClickListener { view ->
            // Launch before closing: ItemClickHandler bails out when the view has no
            // window token, which it would not have once detached from the DragLayer.
            ItemClickHandler.INSTANCE.onClick(view)
            close(true)
        }
        icon.setOnLongClickListener { view -> startAppDrag(view) }
        icon.setCenterVertically(false)
        // all_apps_icon.xml attaches all_apps_fastscroll_icon_anim, which writes scaleX/scaleY
        // (1 and 1.15). That would wipe the fit below on the next state change.
        icon.stateListAnimator = null
        icon.gravity = Gravity.CENTER
        iconFrame.clipChildren = false
        iconFrame.clipToPadding = false
        icon.clipToOutline = false

        // Same defect that was fixed for the card preview in #79, still present here: the icon
        // view was given a fixed 68dp box while the drawable inside it is drawn at its own
        // intrinsic size (the all-apps bitmap, typically 168px). BaseIcon.AllApps also pads 8dp
        // left and right, and TextView starts a compound drawable at the left padding, so the
        // drawable began 8dp in and extended far past the box, cropping both sides and making
        // the icon read as a square/angular blob instead of a circle.
        //
        // The padding override that used to sit here (setIconSizeOverride via setPadding) could
        // never work: BubbleTextView.applyCompoundDrawables() sets the drawable bounds and then
        // calls updateIcon() -> TextView.setCompoundDrawables(), which resets a compound
        // drawable's bounds to its intrinsic size.
        //
        // Give the view a box exactly the drawable's drawn size so nothing can overflow, then
        // scale the view down into the cell. A View scale is applied at draw time and nothing in
        // TextView resets it.
        icon.setPadding(0, 0, 0, 0)
        var boxSize = drawableSizePx(icon)
        iconFrame.addView(
            icon,
            FrameLayout.LayoutParams(boxSize, boxSize, Gravity.CENTER),
        )
        iconFrame.addOnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            // A high-resolution icon can replace the placeholder after the first layout and
            // change the drawable size; re-sync the box so the view never crops the drawable.
            val current = drawableSizePx(icon)
            if (current != boxSize) {
                boxSize = current
                (icon.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                    lp.width = current
                    lp.height = current
                    icon.layoutParams = lp
                }
            }
            val scale = CaddyCardMetrics.iconScale(
                cellWidthPx = right - left,
                cellHeightPx = bottom - top,
                nativeSizePx = boxSize,
            )
            if (scale == icon.scaleX && scale == icon.scaleY) return@addOnLayoutChangeListener
            icon.scaleX = scale
            icon.scaleY = scale
        }

        val label = TextView(context)
        label.text = app.title
        label.setTextColor(Color.WHITE)
        label.setTextSize(11f)
        label.gravity = Gravity.CENTER
        label.maxLines = 2
        label.ellipsize = TextUtils.TruncateAt.END
        cell.addView(
            label,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(34f),
            ).apply { topMargin = dp(5f) },
        )
        return cell
    }

    private fun cellParams(cellWidth: Int) = LinearLayout.LayoutParams(cellWidth, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        setMargins(dp(2f), dp(2f), dp(2f), dp(2f))
    }

    /** The size the icon's drawable is drawn at: its intrinsic size, or the view's icon size. */
    private fun drawableSizePx(icon: BubbleTextView): Int {
        val intrinsic = icon.icon?.intrinsicWidth ?: 0
        if (intrinsic > 0) return intrinsic
        return icon.iconSize.takeIf { it > 0 } ?: 1
    }

    /** An empty cell, so a short last row keeps the same column widths. */
    private class SpacerView(context: Context) : View(context)

    /**
     * Sizes the panel from the space this view was actually given.
     *
     * The size cannot be set when the view is built: the cells use zero-width weighted
     * columns, and a weight only resolves once the grid has a definite width. If the panel
     * is still `WRAP_CONTENT` when the grid is measured, every column measures to zero and
     * the icons stack on a single point. Measuring here guarantees a resolved width before
     * the grid sees a measure spec, so the four columns always get real space.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val panelWidth = CaddyPanelLayout.widthPx(MeasureSpec.getSize(widthMeasureSpec), density)
        val panelHeight = CaddyPanelLayout.heightPx(
            appCount,
            COLUMN_COUNT,
            MeasureSpec.getSize(heightMeasureSpec),
            density,
        )
        (panel.layoutParams as? FrameLayout.LayoutParams)?.let {
            if (it.width != panelWidth || it.height != panelHeight) {
                it.width = panelWidth
                it.height = panelHeight
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /**
     * Starts dragging [view]'s app onto the workspace.
     *
     * The source is the drawer view, a no-op `DragSource`, so a successful drop belongs
     * entirely to the workspace. The panel closes when the drag starts, exactly as a
     * folder does when an icon is dragged out of it.
     */
    private fun startAppDrag(view: View): Boolean {
        if (!ItemLongClickListener.canStartDrag(launcher)) return false
        if (launcher.workspace.isSwitchingState) return false
        view.cancelLongPress()
        launcher.dragController.addDragListener(object : DragController.DragListener {
            override fun onDragStart(dragObject: DropTarget.DragObject, options: DragOptions) {
                view.visibility = View.INVISIBLE
                close(true)
            }

            override fun onDragEnd() {
                view.visibility = View.VISIBLE
                launcher.dragController.removeDragListener(this)
            }
        })
        launcher.workspace.beginDragShared(view, launcher.appsView, DragOptions())
        return true
    }

    /** Marks the panel open. Split out because `mIsOpen` is protected on the base class. */
    fun markOpen() {
        mIsOpen = true
    }

    override fun isOfType(type: Int): Boolean = type and TYPE_CADDY_PANEL != 0

    override fun handleClose(animate: Boolean) {
        animate().cancel()
        if (!animate) {
            removeSelf()
            return
        }
        animate()
            .alpha(0f)
            .setDuration(CLOSE_DURATION_MS)
            .withEndAction(::removeSelf)
            .start()
    }

    private fun removeSelf() {
        (parent as? ViewGroup)?.removeView(this)
    }

    /** Touches inside the panel belong to its icons, so the overlay must not intercept. */
    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean = false

    private fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        resources.displayMetrics,
    ).toInt()

    companion object {
        private const val COLUMN_COUNT = 4
        private const val CLOSE_DURATION_MS = 150L
        private const val OPEN_DURATION_MS = 190L

        /** Dim behind the panel, matching the dialog's 0.34f window dim. */
        private const val DIM_ALPHA = 87

        /**
         * Shows [apps] under [title], reusing the open panel when there is one.
         *
         * Hosting the panel in the DragLayer is the whole point: it is what lets the user
         * drag an app out of the panel onto the workspace.
         */
        @JvmStatic
        fun show(launcher: Launcher, title: CharSequence?, apps: List<AppInfo>) {
            if (apps.isEmpty()) return

            val existing = AbstractFloatingView.getOpenView<CaddyCategoryPanel>(
                launcher,
                AbstractFloatingView.TYPE_CADDY_PANEL,
            )
            if (existing != null) {
                existing.bind(title, apps)
                return
            }

            val dragLayer: BaseDragLayer<Launcher> = launcher.dragLayer
            val view = CaddyCategoryPanel(launcher)
            view.bind(title, apps)
            view.markOpen()
            view.alpha = 0f
            view.panel.scaleX = 0.965f
            view.panel.scaleY = 0.965f
            // The DragLayer casts child params to InsettableFrameLayout.LayoutParams during
            // inset dispatch, so a plain LayoutParams here crashes the launcher.
            dragLayer.addView(
                view,
                BaseDragLayer.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            view.animate()
                .alpha(1f)
                .setDuration(OPEN_DURATION_MS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    view.panel.scaleX = 1f
                    view.panel.scaleY = 1f
                }
                .start()
        }
    }
}
