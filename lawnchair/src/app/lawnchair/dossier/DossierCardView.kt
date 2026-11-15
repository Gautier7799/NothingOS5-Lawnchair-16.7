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

package app.lawnchair.dossier

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import app.lawnchair.allapps.caddy.CaddyCardRenderer
import app.lawnchair.allapps.caddy.CaddyCategoryDialog
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.CellLayout
import com.android.launcher3.DropTarget
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.dragndrop.DraggableView
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.touch.ItemClickHandler
import com.android.launcher3.util.ComponentKey

/** Workspace view for a database-backed Dossier item. */
class DossierCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr),
    DraggableView,
    DropTarget,
    com.android.launcher3.WorkspaceLayoutManager.WorkspaceChildLongClickListener {

    override fun getViewType(): Int = DraggableView.DRAGGABLE_WIDGET

    /**
     * DragPreviewProvider and DragLayer both rely on non-empty visual bounds.
     * An empty default Rect produces a zero-sized drag bitmap; DragLayer then
     * divides 0 by 0 when calculating the final drop scale.
     */
    override fun getWorkspaceVisualDragBounds(bounds: Rect) {
        // DragPreviewProvider uses these bounds to size the drag bitmap. If either
        // dimension is zero, the bitmap can be only the outline padding wide; then
        // DragLayer's (dragViewWidth - blurSizeOutline) denominator becomes zero
        // and the drop animation receives NaN. Keep the Dossier preview non-empty
        // even during an early/unmeasured layout pass.
        val dragWidth = maxOf(width, measuredWidth, minimumWidth, 1)
        val dragHeight = maxOf(height, measuredHeight, minimumHeight, 1)
        bounds.set(0, 0, dragWidth, dragHeight)
    }

    override fun getSourceVisualDragBounds(bounds: Rect) {
        getWorkspaceVisualDragBounds(bounds)
    }

    private var bound: DossierInfo? = null
    private var resolveApp: ((String) -> AppInfo?)? = null
    private var rendering = false
    private var lastRenderSignature: String? = null

    fun bind(
        info: DossierInfo,
        resolveApp: (String) -> AppInfo?,
    ) {
        bound = info
        tag = info
        isFocusable = true
        this.resolveApp = resolveApp
        renderIfMeasured()
    }

    /**
     * Preserve the launcher's native workspace gesture contract:
     * - EDIT_MODE: long-press opens the Dossier resize frame directly.
     * - NORMAL / OVERVIEW: long-press starts the native workspace drag so the card can
     *   be moved or reordered like any other item.
     *
     * When that drag ends on the card itself (a simple long-press without moving), the
     * resize frame is shown, matching how the launcher reveals the resize frame after a
     * widget long-press. This keeps both gestures available instead of one replacing
     * the other.
     */
    override fun onWorkspaceChildLongClick(): Boolean {
        val info = bound ?: return false
        val launcher = Launcher.getLauncher(context)
        if (launcher.isInState(LauncherState.EDIT_MODE)) {
            return showResizeFrame(info, launcher)
        }
        val started = com.android.launcher3.touch.ItemLongClickListener.INSTANCE_WORKSPACE
            .onLongClick(this)
        if (started) {
            val controller = launcher.dragController
            controller.addDragListener(object : com.android.launcher3.dragndrop.DragController.DragListener {
                override fun onDragStart(
                    dragObject: DropTarget.DragObject,
                    options: DragOptions,
                ) = Unit

                override fun onDragEnd() {
                    controller.removeDragListener(this)
                    val dragInfo = controller.mDragObject?.dragInfo
                    // Only when the card itself was dropped (not dragged onto another
                    // target) and the launcher is back to a stable state.
                    if (dragInfo === info && launcher.isInState(LauncherState.NORMAL)) {
                        post { showResizeFrame(info, launcher) }
                    }
                }
            })
        }
        return started
    }

    private fun showResizeFrame(info: DossierInfo, launcher: Launcher): Boolean {
        val cellLayout = findCellLayout() ?: return false
        val card = DossierCardInfo(
            id = info.id,
            title = info.title?.toString().orEmpty(),
            appKeys = info.appKeys,
            screenId = info.screenId,
            cellX = info.cellX,
            cellY = info.cellY,
            spanX = info.spanX,
            spanY = info.spanY,
        )
        DossierResizeFrame.showForCard(
            launcher = launcher,
            cardView = this,
            card = card,
            cellLayout = cellLayout,
            onResized = { updated ->
                info.cellX = updated.cellX
                info.cellY = updated.cellY
                info.spanX = updated.spanX
                info.spanY = updated.spanY
                launcher.modelWriter.modifyItemInDatabase(
                    info,
                    info.container,
                    info.screenId,
                    info.cellX,
                    info.cellY,
                    info.spanX,
                    info.spanY,
                )
                renderIfMeasured()
            },
        )
        return true
    }

    /** Re-resolve app icons after AllAppsStore publishes a completed update. */
    fun refreshApps() {
        renderIfMeasured()
    }

    /** Opens the category panel listing every app assigned to this card. */
    private fun openCard() {
        val info = bound ?: return
        val resolve = resolveApp ?: return
        CaddyCategoryDialog.show(
            context = context,
            title = info.title?.toString().orEmpty(),
            apps = info.appKeys.mapNotNull(resolve),
        )
    }

    // ---------------------------------------------------------------------
    // DropTarget: accept an app icon dropped from the drawer onto this card.
    // ---------------------------------------------------------------------

    /**
     * Dossier cards are drop targets only for app icons from All Apps. During a
     * workspace-card drag, leaving this enabled lets the source/other Dossier
     * views intercept DragController hit-testing before Workspace can resolve
     * the destination page. That prevents page transfer and can make the drop
     * fall through to an unrelated target.
     */
    override fun isDropEnabled(): Boolean {
        val dragInfo = Launcher.getLauncher(context).dragController.mDragObject?.dragInfo
        return dragInfo is AppInfo
    }

    override fun acceptDrop(dragObject: DropTarget.DragObject): Boolean = appKeyFor(dragObject) != null

    override fun onDragEnter(dragObject: DropTarget.DragObject) {
        alpha = HOVER_ALPHA
    }

    override fun onDragOver(dragObject: DropTarget.DragObject) = Unit

    override fun onDragExit(dragObject: DropTarget.DragObject) {
        alpha = 1f
    }

    override fun onDrop(dragObject: DropTarget.DragObject, options: DragOptions) {
        alpha = 1f
        val info = bound ?: return
        val key = appKeyFor(dragObject) ?: return
        if (!info.addApp(key)) return
        Launcher.getLauncher(context).modelWriter.updateItemInDatabase(info)
        lastRenderSignature = null
        renderIfMeasured()
    }

    override fun prepareAccessibilityDrop() = Unit

    override fun getHitRectRelativeToDragLayer(outRect: Rect) {
        Launcher.getLauncher(context).dragLayer.getViewRectRelativeToSelf(this, outRect)
    }

    override fun getDropView(): View = this

    /** Returns the stable component key for the dragged app, or null for anything else. */
    private fun appKeyFor(dragObject: DropTarget.DragObject): String? {
        val app = dragObject.dragInfo as? AppInfo ?: return null
        val component = app.componentName ?: return null
        return ComponentKey(component, app.user).toString()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Binding can happen before the workspace has completed its first layout.
        // Retry after attachment; onSizeChanged remains the fallback when measurement
        // finishes after this posted callback.
        post { renderIfMeasured() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) renderIfMeasured()
    }

    private fun renderIfMeasured() {
        val info = bound ?: return
        val resolve = resolveApp ?: return
        if (width <= 0 || height <= 0 || rendering) return
        rendering = true
        try {
            val gutter = dp(GUTTER_DP) + dp(CARD_PADDING_DP)
            // Grid settings are global user preferences. Read the current values
            // here rather than the seed-time payload so changing Grille updates
            // already-existing cards after the workspace grid reload.
            val preferences = PreferenceManager2.getInstance(context)
            val gridColumns = preferences.dossierGridColumns.firstCached()
                .coerceIn(DossierInfo.MIN_GRID_SIZE, DossierInfo.MAX_GRID_SIZE)
            val gridRows = preferences.dossierGridRows.firstCached()
                .coerceIn(DossierInfo.MIN_GRID_SIZE, DossierInfo.MAX_GRID_SIZE)
            val grid = DossierCardLayout.compute(
                widthPx = width,
                heightPx = height,
                gutterPx = gutter,
                titlePx = dp(TITLE_DP),
                targetCellPx = dp(TARGET_CELL_DP),
                preferredColumns = gridColumns,
                preferredRows = gridRows,
            )
            val columns = grid.columns
            val rows = grid.rows
            val cellSizePx = grid.cellSizePx

            // Resolve only what is available in this AllAppsStore pass. A single
            // missing app must not suppress the card surface or the icons already ready.
            val resolvedApps = info.appKeys.mapNotNull(resolve)
            val renderSignature = buildString {
                append(info.id).append('|')
                append(info.title).append('|')
                append(info.appKeys.joinToString(",")).append('|')
                append(columns).append('x').append(rows).append('|')
                append(width).append('x').append(height).append('|')
                resolvedApps.forEach { app ->
                    append(app.componentName).append(':')
                    append(app.user).append(':')
                    append(app.title).append(':')
                    append(System.identityHashCode(app.bitmap.icon)).append(';')
                }
            }
            // AllAppsStore may publish multiple identical updates during startup.
            // Rebuilding the entire view tree for each one caused visible blanking and lag.
            if (renderSignature == lastRenderSignature) return
            CaddyCardRenderer.renderCategoryCard(
                host = this,
                title = info.title?.toString().orEmpty(),
                apps = resolvedApps,
                cardClick = { openCard() },
                iconBinder = { icon, _ ->
                    icon.setOnClickListener(ItemClickHandler.INSTANCE)
                    icon.setOnLongClickListener { performLongClick() }
                },
                fitGridToHost = true,
                columns = columns,
                maxIcons = columns * rows,
                cellSizePx = cellSizePx,
            )
            lastRenderSignature = renderSignature
            getChildAt(0)?.setOnLongClickListener {
                performLongClick()
            }
        } finally {
            rendering = false
        }
    }

    private fun findCellLayout(): CellLayout? {
        var current: View? = this
        while (current != null) {
            if (current is CellLayout) return current
            current = current.parent as? View
        }
        return null
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val TARGET_CELL_DP = 60f
        const val GUTTER_DP = 4f
        const val CARD_PADDING_DP = 14f
        const val TITLE_DP = 24f
        const val HOVER_ALPHA = 0.8f
    }
}
