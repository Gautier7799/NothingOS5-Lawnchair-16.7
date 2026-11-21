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
import android.view.ViewGroup
import android.widget.FrameLayout
import app.lawnchair.allapps.caddy.CaddyCardRenderer
import app.lawnchair.allapps.caddy.CaddyCategoryPanel
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.BubbleTextView
import com.android.launcher3.CellLayout
import com.android.launcher3.DropTarget
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.dragndrop.DraggableView
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.popup.PopupContainerWithArrow
import com.android.launcher3.touch.ItemClickHandler
import com.android.launcher3.views.ActivityContext

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
                        // startDrag() makes the card INVISIBLE. A drag that ends without a
                        // successful drop (a long-press held in place) never runs the drop
                        // animation that would restore it, so the card vanished while the
                        // resize handles stayed. Restore visibility before revealing the frame.
                        visibility = View.VISIBLE
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

    /**
     * Reveals the resize frame around this card without a long-press.
     *
     * A card dropped from the drawer has no long-press that opened the frame, so the
     * caller invokes this once the launcher has settled to give the user the same
     * resize entry point a workspace item gets after its own gesture.
     */
    fun revealResizeFrame() {
        val info = bound ?: return
        showResizeFrame(info, Launcher.getLauncher(context))
    }

    /**
     * Forces the next render to rebuild, e.g. after the Dossier Grille settings changed.
     * The grid dimensions are part of the render signature, but clearing it makes the
     * rebuild explicit and immune to an identical app/geometry signature.
     */
    fun refreshGrid() {
        lastRenderSignature = null
        renderIfMeasured()
    }

    /** Opens the category panel listing every app assigned to this card. */
    private fun openCard() {
        val info = bound ?: return
        val resolve = resolveApp ?: return
        CaddyCategoryPanel.show(
            launcher = Launcher.getLauncher(context),
            title = info.title?.toString().orEmpty(),
            apps = info.appKeys.mapNotNull(resolve),
        )
    }

    /**
     * Shows the launcher's standard item popup for an icon inside the card.
     *
     * The popup is anchored to the icon and offers App info plus the other system
     * shortcuts, exactly as a workspace icon does. Returns false when no popup could
     * be shown, so the caller can fall back to the card's own long-press behaviour.
     */
    private fun showIconPopup(icon: BubbleTextView): Boolean = PopupContainerWithArrow.showForIcon(icon) != null

    // ---------------------------------------------------------------------
    // DropTarget: accept an app icon dropped from the drawer onto this card.
    // ---------------------------------------------------------------------

    /**
     * Dossier cards accept an app icon dragged from All Apps and an app shortcut
     * dragged from the workspace. Both carry a resolvable component key, so either
     * can be appended to the card. A Dossier card drag (or any other item) is left
     * disabled: during a workspace-card drag, keeping this enabled lets the
     * source/other Dossier views intercept DragController hit-testing before
     * Workspace can resolve the destination page, which prevents page transfer.
     */
    override fun isDropEnabled(): Boolean {
        val dragInfo = Launcher.getLauncher(context).dragController.mDragObject?.dragInfo
        return dragInfo is AppInfo || dragInfo is WorkspaceItemInfo
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
        val launcher = Launcher.getLauncher(context)
        launcher.modelWriter.updateItemInDatabase(info)
        // A shortcut dragged from the workspace moves into the card, so its own row and
        // view are removed; otherwise the same app would appear both on the workspace and
        // inside the card. An All Apps icon has no workspace row and needs no cleanup.
        val source = dragObject.dragInfo
        if (source is WorkspaceItemInfo && source.container >= 0) {
            val sourceView = dragObject.originalView as? View
            if (sourceView != null) {
                launcher.removeItem(sourceView, source, true, "moved into Dossier card")
            }
        }
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
        // Accept both an All Apps icon (AppInfo) and a workspace shortcut
        // (WorkspaceItemInfo). Both resolve to a canonical ComponentKey string, which is
        // exactly the format the Dossier payload stores and ItemInflater parses back.
        val info = dragObject.dragInfo as? ItemInfo ?: return null
        val key = info.componentKey?.toString() ?: return null
        return key.takeIf(DossierAppKeys::isCanonical)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Dossier-to-Dossier gaps are tightened using per-card margins. Recalculate
        // after the page has attached so every sibling card is present in the container.
        post {
            (parent as? ViewGroup)?.let(::updateDossierSpacing)
            renderIfMeasured()
        }
    }

    override fun onDetachedFromWindow() {
        val oldContainer = parent as? ViewGroup
        super.onDetachedFromWindow()
        // A card may have been moved to another page. Restore the spacing of the cards
        // that remain on its previous page after this view has been removed.
        oldContainer?.post { updateDossierSpacing(oldContainer) }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) {
            renderIfMeasured()
            (parent as? ViewGroup)?.let { container ->
                container.post { updateDossierSpacing(container) }
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            (parent as? ViewGroup)?.let { container ->
                container.post { updateDossierSpacing(container) }
            }
        }
    }

    /**
     * Tighten the gap between adjacent Dossier cards to the same value on both axes.
     *
     * The negative margins expand each card into half of the surrounding cell border
     * gap, so two neighbouring cards end up [CARD_GAP_DP] apart regardless of whether
     * they sit side by side or stacked. Using one target gap for both axes keeps the
     * spacing consistent and removes the large empty bands a card row would otherwise
     * leave around it. Ordinary icons, folders, and widgets keep their original spacing.
     */
    private fun updateDossierSpacing(container: ViewGroup) {
        val cards = (0 until container.childCount)
            .mapNotNull { container.getChildAt(it) as? DossierCardView }
        // Resolve the device profile through the ActivityContext: this runs during layout,
        // including in the Settings previews where the context is not a Launcher.
        val activityContext: ActivityContext? = ActivityContext.lookupContextNoThrow(context)
        val borderSpace = findCellLayout()?.let {
            activityContext?.deviceProfile?.cellLayoutBorderSpacePx
        }
        val overlapX = DossierCardLayout.overlapForGapPx(borderSpace?.x ?: 0, dp(CARD_GAP_DP))
        val overlapY = DossierCardLayout.overlapForGapPx(borderSpace?.y ?: 0, dp(CARD_GAP_DP))
        cards.forEach { card ->
            val params = card.layoutParams as? CellLayoutLayoutParams ?: return@forEach
            val left = params.getCellX()
            val top = params.getCellY()
            val right = left + params.cellHSpan
            val bottom = top + params.cellVSpan
            var hasLeft = false
            var hasRight = false
            var hasAbove = false
            var hasBelow = false
            cards.forEach { sibling ->
                if (sibling === card) return@forEach
                val sp = sibling.layoutParams as? CellLayoutLayoutParams ?: return@forEach
                val sLeft = sp.getCellX()
                val sTop = sp.getCellY()
                val sRight = sLeft + sp.cellHSpan
                val sBottom = sTop + sp.cellVSpan
                if (sLeft < right && sRight > left) {
                    if (sBottom == top) hasAbove = true
                    if (sTop == bottom) hasBelow = true
                }
                if (sTop < bottom && sBottom > top) {
                    if (sRight == left) hasLeft = true
                    if (sLeft == right) hasRight = true
                }
            }
            val leftMargin = if (hasLeft) -overlapX else 0
            val rightMargin = if (hasRight) -overlapX else 0
            val topMargin = if (hasAbove) -overlapY else 0
            val bottomMargin = if (hasBelow) -overlapY else 0
            if (params.leftMargin != leftMargin || params.rightMargin != rightMargin ||
                params.topMargin != topMargin || params.bottomMargin != bottomMargin
            ) {
                params.leftMargin = leftMargin
                params.rightMargin = rightMargin
                params.topMargin = topMargin
                params.bottomMargin = bottomMargin
                card.requestLayout()
            }
        }
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
                    // Long-pressing an icon opens the launcher's own item popup, so the
                    // card offers the same actions as a workspace icon: App info (the
                    // explicitly requested behaviour) plus widgets, install and deep
                    // shortcuts. When the popup cannot be shown the press falls back to
                    // the card's own long-press (move / resize) so the gesture is never lost.
                    icon.setOnLongClickListener { view ->
                        showIconPopup(view as BubbleTextView) || performLongClick()
                    }
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

        /**
         * The gap left between two adjacent Dossier cards, on both axes. Each card grows
         * into half of the surrounding cell border gap, so the visible gap becomes this
         * value; on a 16dp grid gap it is clamped to that natural gap so cards never
         * overlap. The same value is used horizontally and vertically.
         */
        const val CARD_GAP_DP = 14f
        const val GUTTER_DP = 4f
        const val CARD_PADDING_DP = 14f
        const val TITLE_DP = 24f
        const val HOVER_ALPHA = 0.8f
    }
}
