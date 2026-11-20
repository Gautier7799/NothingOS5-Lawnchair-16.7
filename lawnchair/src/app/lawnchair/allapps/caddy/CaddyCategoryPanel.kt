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
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.android.launcher3.AbstractFloatingView
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
 * The panel listing every app of a Caddy category.
 *
 * This is a floating overlay **inside the launcher's DragLayer**, not a dialog window.
 * That placement is what makes dragging an app out of the panel work: the launcher's
 * drag machinery maps the drag preview through the view hierarchy
 * (`DragPreviewProvider` -> `BaseDragLayer.getLocationInDragLayer`) and feeds the drag
 * through the DragLayer's touch stream. A view hosted in a separate window fails both:
 * the coordinate walk never reaches the DragLayer, and the move/up events go to the
 * dialog window instead of the drag driver, so the drop is refused.
 *
 * Because the icons are ordinary DragLayer descendants here, each one keeps the
 * launcher's own click and long-press handlers. A long-press therefore starts the same
 * all-apps drag the drawer uses, and the app can be dropped on the workspace.
 */
class CaddyCategoryPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractFloatingView(context, attrs, defStyleAttr) {

    private val launcher: Launcher = Launcher.getLauncher(context)

    /** Scrim behind the panel; a tap on it closes the panel. */
    private val scrim = View(context)

    /** The rounded surface that holds the title and the app grid. */
    private val panel = LinearLayout(context)

    /** Number of apps in the current grid, needed to size the panel on a host resize. */
    private var appCount = 0

    init {
        // The base class is a LinearLayout; a single child keeps the overlay a plain
        // full-size frame, and the scrim and the panel stack inside it.
        orientation = VERTICAL
        isClickable = true
        isFocusable = true
        clipChildren = false
        clipToPadding = false

        scrim.setBackgroundColor(Color.argb(SCRIM_DIM_ALPHA, 0, 0, 0))
        scrim.setOnClickListener { close(true) }

        panel.orientation = VERTICAL
        panel.setPadding(dp(20f), dp(18f), dp(20f), dp(16f))
        panel.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                CaddyMaterialYou.surfaceContainer(context),
                CaddyMaterialYou.surface(context),
            ),
        ).apply {
            cornerRadius = dp(28f).toFloat()
            setStroke(dp(1f), CaddyMaterialYou.outlineVariant(context))
        }
        panel.clipToOutline = true
        panel.elevation = dp(12f).toFloat()
        // Absorb taps on the panel's own padding so only the scrim closes the panel.
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
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        addView(
            content,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    /** Fills the panel with [apps] under [title] and sizes it to the host. */
    fun bind(title: CharSequence?, apps: List<AppInfo>) {
        panel.removeAllViews()

        val heading = TextView(context)
        heading.text = title
        heading.setTextColor(CaddyMaterialYou.onSurface(context))
        heading.setTextSize(20f)
        heading.typeface = Typeface.DEFAULT_BOLD
        heading.maxLines = 1
        heading.ellipsize = TextUtils.TruncateAt.END
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

        val grid = GridLayout(context)
        grid.columnCount = COLUMN_COUNT
        grid.useDefaultMargins = false
        scrollView.addView(
            grid,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        apps.forEach { app ->
            val cell = LinearLayout(context)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER
            cell.setPadding(dp(2f), dp(8f), dp(2f), dp(8f))

            val icon = LayoutInflater.from(context)
                .inflate(R.layout.caddy_panel_icon, cell, false) as CaddyPanelIcon
            icon.applyFromApplicationInfo(app)
            icon.setTextVisibility(false)
            icon.contentDescription = app.title
            icon.setOnClickListener { view ->
                // Click before closing: ItemClickHandler bails out when the view has no
                // window token, which it would not have once detached from the DragLayer.
                ItemClickHandler.INSTANCE.onClick(view)
                close(true)
            }
            // A long-press starts the launcher's own all-apps drag, which is what makes
            // dragging the icon onto the workspace possible. The panel is a DragLayer
            // child, so the drag preview and the touch stream both resolve correctly.
            icon.setOnLongClickListener { view -> startAppDrag(view) }
            cell.addView(
                icon,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(ICON_CELL_DP),
                ),
            )

            val label = TextView(context)
            label.text = app.title
            label.setTextColor(CaddyMaterialYou.onSurfaceVariant(context))
            label.setTextSize(11f)
            label.gravity = Gravity.CENTER
            label.maxLines = 2
            label.ellipsize = TextUtils.TruncateAt.END
            cell.addView(
                label,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(LABEL_HEIGHT_DP),
                ).apply { topMargin = dp(4f) },
            )

            grid.addView(
                cell,
                GridLayout.LayoutParams().apply {
                    width = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(2f), dp(2f), dp(2f), dp(2f))
                },
            )
        }

        appCount = apps.size
        applyPanelSize(appCount)
    }

    /**
     * Sizes the panel from the host size.
     *
     * The overlay fills the DragLayer, so its own size is the host size. Doing this on a
     * size change (rather than in `onMeasure`) keeps the pass that produced the size from
     * being invalidated by the child params it would write.
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyPanelSize(appCount)
    }

    private fun applyPanelSize(appCount: Int) {
        if (width == 0 || height == 0) return
        val density = resources.displayMetrics.density
        panel.layoutParams = (
            panel.layoutParams as? FrameLayout.LayoutParams
                ?: FrameLayout.LayoutParams(0, 0, Gravity.CENTER)
            ).apply {
            width = CaddyPanelLayout.widthPx(width, density)
            height = CaddyPanelLayout.heightPx(appCount, COLUMN_COUNT, height, density)
        }
    }

    /**
     * Starts dragging [view]'s app onto the workspace.
     *
     * This mirrors the drawer's all-apps long-press but does not require the launcher to be
     * in the All Apps state: the panel can be opened from the workspace too, and dragging
     * has to work in both cases. The source is the drawer view, which is a no-op
     * `DragSource`, so a successful drop is owned entirely by the workspace.
     *
     * The panel closes as soon as the drag begins, exactly as a folder does when an icon is
     * dragged out of it, so the workspace the user is dropping onto is fully visible.
     */
    private fun startAppDrag(view: View): Boolean {
        val icon = (view as? CaddyPanelIcon) ?: return false
        if (!ItemLongClickListener.canStartDrag(launcher)) return false
        if (launcher.workspace.isSwitchingState) return false
        icon.cancelLongPress()
        launcher.dragController.addDragListener(object : DragController.DragListener {
            override fun onDragStart(dragObject: DropTarget.DragObject, options: DragOptions) {
                icon.visibility = View.INVISIBLE
                close(true)
            }

            override fun onDragEnd() {
                icon.visibility = View.VISIBLE
                launcher.dragController.removeDragListener(this)
            }
        })
        launcher.workspace.beginDragShared(icon, launcher.appsView, DragOptions())
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

    /**
     * Touches inside the panel belong to its icons, so the overlay must not intercept
     * them. Only a press outside the panel closes it, which the scrim handles.
     */
    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean = false

    private fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        resources.displayMetrics,
    ).toInt()

    companion object {
        private const val COLUMN_COUNT = 4
        private const val CLOSE_DURATION_MS = 150L
        private const val OPEN_DURATION_MS = 180L
        private const val SCRIM_DIM_ALPHA = 84

        /** Height of one icon, in dp, inside a grid cell. */
        private const val ICON_CELL_DP = 56f

        /** Height of the app label, in dp, under its icon. */
        private const val LABEL_HEIGHT_DP = 30f

        /** Whether a panel is currently open. */
        @JvmStatic
        fun isOpen(launcher: Launcher): Boolean = AbstractFloatingView.getOpenView<CaddyCategoryPanel>(
            launcher,
            AbstractFloatingView.TYPE_CADDY_PANEL,
        ) != null

        /**
         * Shows [apps] under [title], reusing the open panel when there is one.
         *
         * Hosting the panel in the DragLayer is the whole point: it is what lets the user
         * drag an app out of the panel onto the workspace.
         */
        @JvmStatic
        fun show(launcher: Launcher, title: CharSequence?, apps: List<AppInfo>) {
            if (apps.isEmpty()) return

            val dragLayer: BaseDragLayer<Launcher> = launcher.dragLayer
            val existing = AbstractFloatingView.getOpenView<CaddyCategoryPanel>(
                launcher,
                AbstractFloatingView.TYPE_CADDY_PANEL,
            )
            if (existing != null) {
                existing.bind(title, apps)
                return
            }

            val view = CaddyCategoryPanel(launcher)
            view.bind(title, apps)
            view.markOpen()
            view.alpha = 0f
            dragLayer.addView(
                view,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            view.animate()
                .alpha(1f)
                .setDuration(OPEN_DURATION_MS)
                .start()
        }
    }
}
