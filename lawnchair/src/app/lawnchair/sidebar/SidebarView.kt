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

package app.lawnchair.sidebar

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.lawnchair.allapps.caddy.CaddyMaterialYou
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
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
 * The home-screen sidebar panel, hosted in the DragLayer so it shares the launcher's
 * coordinate space and touch stream.
 *
 * Only the panel lives here. The tab that opens it is [SidebarHandle], a separate view that
 * exists while the panel is closed; this view is added on open and removed on close, because
 * [AbstractFloatingView] consumes every touch it receives and would otherwise block the
 * workspace.
 *
 * Icons are real [BubbleTextView]s bound to [AppInfo], so they carry the launcher's icon theme
 * and can be long-pressed to drag an app onto the workspace, exactly like the Caddy panel.
 */
class SidebarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractFloatingView(context, attrs, defStyleAttr) {

    private val launcher: Launcher = Launcher.getLauncher(context)

    private val scrim = View(context)
    private val panel = LinearLayout(context)
    private val scroll = ScrollView(context)
    private val column = LinearLayout(context)
    private val quickControls = QuickControlStrip(context)
    private val volumePanel = VolumePanel(context)
    private val brightnessPanel = BrightnessPanel(context)
    private val appRows = LinearLayout(context)
    private var searchField: EditText? = null
    private var allApps: List<AppInfo> = emptyList()
    private var extrasCount = 0

    private var itemCount = 0
    private var onRightEdge = true
    private var hapticsEnabled = true

    init {
        isClickable = true
        isFocusable = true
        clipChildren = false
        clipToPadding = false

        scrim.setBackgroundColor(0x40000000)
        scrim.setOnClickListener { close(true) }
        addView(
            scrim,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )

        panel.orientation = LinearLayout.VERTICAL
        panel.clipToOutline = true
        panel.elevation = dp(8).toFloat()

        scroll.isVerticalScrollBarEnabled = false
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(
            dp(SidebarMetrics.PANEL_PADDING_H_DP),
            dp(SidebarMetrics.PANEL_PADDING_V_DP),
            dp(SidebarMetrics.PANEL_PADDING_H_DP),
            dp(SidebarMetrics.PANEL_PADDING_V_DP),
        )
        column.addView(
            appRows,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        scroll.addView(
            column,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        panel.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        addView(
            panel,
            LayoutParams(dp(SidebarMetrics.PANEL_WIDTH_DP), LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                rightMargin = dp(SidebarMetrics.EDGE_GAP_DP)
            },
        )

        scrim.alpha = 0f
        applyAppearance()
    }

    /** Re-reads colours, edge, width and opacity from the current preferences. */
    fun applyAppearance() {
        val prefs = PreferenceManager2.getInstance(launcher)
        onRightEdge = prefs.sidebarOnRightEdge.firstCached(prefs)
        hapticsEnabled = prefs.sidebarHaptics.firstCached(prefs)

        val columns = SidebarMetrics.clampColumns(prefs.sidebarColumns.firstCached(prefs))
        val width = SidebarMetrics.panelWidthPx(columns, density)
        (panel.layoutParams as? LayoutParams)?.let { params ->
            params.width = width
            params.gravity = (if (onRightEdge) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
            params.leftMargin = if (onRightEdge) 0 else dp(SidebarMetrics.EDGE_GAP_DP)
            params.rightMargin = if (onRightEdge) dp(SidebarMetrics.EDGE_GAP_DP) else 0
            panel.layoutParams = params
        }

        panel.background = GradientDrawable().apply {
            setColor(CaddyMaterialYou.surfaceContainer(context))
            cornerRadius = dp(SidebarMetrics.PANEL_RADIUS_DP).toFloat()
            setStroke(dp(1), CaddyMaterialYou.outlineVariant(context))
        }

        panel.alpha = 0f
        panel.translationX = SidebarMetrics.slideDistancePx(onRightEdge, density)
        scrim.alpha = 0f
    }

    /**
     * Adds the volume slider row when its preference is on.
     *
     * A device whose streams all report a zero maximum gets no row, and then the panel height
     * arithmetic must not count one, which is why the result is reported back to [bind].
     */
    private fun bindVolumePanel(): Boolean {
        val prefs = PreferenceManager2.getInstance(launcher)
        val enabled = prefs.sidebarVolumeSliders.firstCached()
        if (!enabled) return false
        val panel = volumePanel.build(VolumeState.ordered) ?: return false
        column.addView(panel)
        return true
    }

    /**
     * Adds the brightness slider when its preference is on.
     *
     * A device that cannot report a usable brightness range returns null and gets no row, which
     * is why the result is reported back to [bind].
     */
    private fun bindBrightness(): Boolean {
        val enabled = PreferenceManager2.getInstance(launcher)
            .sidebarBrightnessSlider
            .firstCached()
        if (!enabled) return false
        val row = brightnessPanel.build() ?: return false
        column.addView(row)
        return true
    }

    /**
     * Adds the quick-control strip above the app column when the preference is on.
     *
     * The strip is rebuilt on every bind so a toggle added while the panel was closed shows up,
     * and it is refreshed on attach so a tile never shows a stale state from the last time the
     * panel was open.
     *
     * @return whether the strip was added, which the caller needs for the height arithmetic.
     */
    private fun bindQuickControls(): Boolean {
        val prefs = PreferenceManager2.getInstance(launcher)
        if (!prefs.sidebarQuickControls.firstCached()) return false
        val selected = QuickControlSelection.resolve(
            prefs.sidebarQuickControlList.firstCached(),
        )
        // An empty selection is a valid choice, but a strip with no tiles is just a blank gap.
        if (selected.isEmpty()) return false
        val strip = quickControls.build(selected) ?: return false
        column.addView(strip)
        return true
    }

    /** Fills the panel with [apps], laid out in rows of the configured column count. */
    fun bind(apps: List<AppInfo>) {
        allApps = apps
        column.removeAllViews()
        // Evaluate both: `||` would short-circuit and skip the volume panel whenever the
        // quick-control strip is enabled.
        val hasTiles = bindQuickControls()
        val hasVolume = bindVolumePanel()
        val hasBrightness = bindBrightness()
        val hasSearch = bindSearch()
        bindAppRows()
        // Each extra row takes about one cell of vertical space, so it counts as one app when
        // the panel height is computed. That keeps the sizing arithmetic in SidebarMetrics.
        extrasCount = (if (hasTiles) 1 else 0) + (if (hasVolume) 1 else 0) +
            (if (hasBrightness) 1 else 0) + (if (hasSearch) 1 else 0)
    }

    /**
     * Adds the search field when its preference is on, and returns whether it was added.
     *
     * The field filters the already-chosen sidebar apps rather than the whole installed set,
     * because this panel is a shortlist, not an app drawer. Filtering happens on every
     * keystroke against the cached list, so opening the panel stays instant.
     */
    private fun bindSearch(): Boolean {
        val enabled = PreferenceManager2.getInstance(launcher)
            .sidebarSearch
            .firstCached()
        if (!enabled) return false
        val field = EditText(context).apply {
            hint = context.getString(R.string.sidebar_search_hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(CaddyMaterialYou.onSurface(context))
            setHintTextColor(CaddyMaterialYou.onSurfaceVariant(context))
            background = GradientDrawable().apply {
                setColor(CaddyMaterialYou.surface(context))
                cornerRadius = dp(SidebarMetrics.TILE_RADIUS_DP).toFloat()
                setStroke(dp(1), CaddyMaterialYou.outlineVariant(context))
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            maxLines = 1
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_NONE
            addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        bindAppRows()
                    }
                },
            )
        }
        searchField = field
        column.addView(
            field,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(6) },
        )
        return true
    }

    /**
     * Repaints the app rows for the current query.
     *
     * Only the rows are touched, so the search field keeps its focus and the caret keeps its
     * position while the user types.
     */
    private fun bindAppRows() {
        appRows.removeAllViews()
        val apps = allApps
        val query = searchField?.text?.toString().orEmpty()
        val filtered = SidebarSearch.rank(apps, query) { it.title.toString() }
        itemCount = filtered.size + extrasCount

        if (filtered.isEmpty()) {
            val message = if (apps.isEmpty()) {
                context.getString(R.string.sidebar_empty)
            } else {
                context.getString(R.string.sidebar_no_results)
            }
            val empty = TextView(context).apply {
                text = message
                setTextColor(CaddyMaterialYou.onSurfaceVariant(context))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(16), dp(4), dp(16))
            }
            appRows.addView(
                empty,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        } else {
            val columns = SidebarMetrics.clampColumns(
                PreferenceManager2.getInstance(launcher).sidebarColumns.firstCached(),
            )
            filtered.chunked(columns).forEach { row ->
                appRows.addView(buildRow(row, columns))
            }
        }
        requestLayout()
    }

    /**
     * One row of icons.
     *
     * Each cell takes an equal share of the width so a row stays aligned whether the last row
     * is full or not; padding cells fill the gap rather than stretching the icons.
     */
    private fun buildRow(row: List<AppInfo>, columns: Int): View {
        val rowView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(SidebarMetrics.CELL_HEIGHT_DP),
            )
        }
        row.forEach { app ->
            rowView.addView(
                appCell(app),
                LinearLayout.LayoutParams(0, dp(SidebarMetrics.CELL_HEIGHT_DP), 1f),
            )
        }
        repeat(columns - row.size) {
            rowView.addView(
                View(context),
                LinearLayout.LayoutParams(0, dp(SidebarMetrics.CELL_HEIGHT_DP), 1f),
            )
        }
        return rowView
    }

    /** One app, built the way the workspace builds an icon. */
    private fun appCell(app: AppInfo): View {
        val cell = FrameLayout(context)
        val icon = LayoutInflater.from(context)
            .inflate(R.layout.all_apps_icon, cell, false) as BubbleTextView
        icon.applyFromApplicationInfo(app)
        icon.setTextVisibility(false)
        icon.setLongPressTimeoutFactor(1f)
        icon.contentDescription = app.title
        icon.gravity = Gravity.CENTER
        icon.setOnClickListener { view ->
            // Launch before closing: ItemClickHandler bails out when the view has no window
            // token, which it would not have once detached from the DragLayer.
            ItemClickHandler.INSTANCE.onClick(view)
            close(true)
        }
        icon.setOnLongClickListener { view -> startAppDrag(view) }
        cell.addView(
            icon,
            FrameLayout.LayoutParams(
                dp(SidebarMetrics.ICON_SIZE_DP),
                dp(SidebarMetrics.ICON_SIZE_DP),
                Gravity.CENTER,
            ),
        )
        return cell
    }

    /**
     * Starts dragging [view]'s app onto the workspace.
     *
     * The source is the drawer view, a no-op `DragSource`, so a successful drop belongs
     * entirely to the workspace. The panel closes when the drag starts, as a folder does when
     * an icon is dragged out of it.
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

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean = false

    override fun isOfType(type: Int): Boolean = type and TYPE_SIDEBAR != 0

    override fun handleClose(animate: Boolean) {
        animate().cancel()
        (parent as? ViewGroup)?.removeView(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mIsOpen = true
        if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        quickControls.refresh()
        volumePanel.refresh()
        scrim.animate().alpha(1f).setDuration(OPEN_DURATION_MS).start()
        panel.animate()
            .alpha(
                SidebarMetrics.opacityFraction(
                    PreferenceManager2.getInstance(launcher).sidebarOpacity.firstCached(),
                ),
            )
            .translationX(0f)
            .setDuration(OPEN_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = SidebarMetrics.panelHeightPx(
            itemCount,
            MeasureSpec.getSize(heightMeasureSpec),
            density,
        )
        (panel.layoutParams as? LayoutParams)?.let {
            if (it.height != height) it.height = height
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics,
    ).toInt()

    private val density: Float get() = resources.displayMetrics.density

    companion object {
        private const val OPEN_DURATION_MS = 190L

        /** Shows the sidebar, reusing the open one when there is one. */
        @JvmStatic
        fun show(launcher: Launcher, apps: List<AppInfo>) {
            val existing = AbstractFloatingView.getOpenView<SidebarView>(
                launcher,
                AbstractFloatingView.TYPE_SIDEBAR,
            )
            if (existing != null) {
                existing.applyAppearance()
                existing.bind(apps)
                return
            }
            val dragLayer: BaseDragLayer<Launcher> = launcher.dragLayer
            val view = SidebarView(launcher)
            view.applyAppearance()
            view.bind(apps)
            // The DragLayer casts every child's params to InsettableFrameLayout.LayoutParams
            // when it dispatches insets, so a plain LayoutParams here crashes the launcher.
            dragLayer.addView(
                view,
                BaseDragLayer.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }
}
