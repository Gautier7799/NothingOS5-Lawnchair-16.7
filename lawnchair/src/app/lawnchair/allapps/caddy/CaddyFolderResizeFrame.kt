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
import android.graphics.Rect
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.CellLayout
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.dragndrop.DragLayer
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.views.BaseDragLayer

/**
 * Resize UI for a Caddy folder.
 *
 * A Caddy folder is an ordinary native [FolderInfo], so resizing reuses the workspace
 * grid: this frame mirrors the launcher's own resize frame (the same four edge handles
 * and the same "drag an edge to add or remove a whole row/column" gesture) and writes
 * the result back through the native [com.android.launcher3.model.ModelWriter]. It is a
 * separate class only because the native frame is hard-wired to a
 * `LauncherAppWidgetHostView`; the folder is deliberately kept a folder, never a widget.
 */
class CaddyFolderResizeFrame @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractFloatingView(context, attrs, defStyleAttr),
    View.OnKeyListener {

    private val launcher = Launcher.getLauncher(context)
    private val dragLayer: DragLayer = launcher.dragLayer

    private var folderIcon: FolderIcon? = null
    private var cellLayout: CellLayout? = null
    private var info: FolderInfo? = null

    private val handles = arrayOfNulls<View>(HANDLE_COUNT)

    private val backgroundPadding =
        resources.getDimensionPixelSize(R.dimen.resize_frame_background_padding)
    private val touchTargetWidth = 2 * backgroundPadding

    private var leftActive = false
    private var rightActive = false
    private var topActive = false
    private var bottomActive = false

    /** True between a border touch-down and its touch-up; suppresses handle re-brightening. */
    private var resizeInProgress = false

    private var xDown = 0
    private var yDown = 0

    private var startCellX = 0
    private var startCellY = 0
    private var startSpanX = 1
    private var startSpanY = 1

    private val folderLayoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        snapToFolder()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        handles[INDEX_LEFT] = findViewById(R.id.caddy_resize_left_handle)
        handles[INDEX_TOP] = findViewById(R.id.caddy_resize_top_handle)
        handles[INDEX_RIGHT] = findViewById(R.id.caddy_resize_right_handle)
        handles[INDEX_BOTTOM] = findViewById(R.id.caddy_resize_bottom_handle)
    }

    private fun setup(icon: FolderIcon, layout: CellLayout) {
        this.folderIcon = icon
        this.cellLayout = layout
        this.info = icon.mInfo
        startCellX = icon.mInfo.cellX
        startCellY = icon.mInfo.cellY
        startSpanX = icon.mInfo.spanX
        startSpanY = icon.mInfo.spanY
        setOnKeyListener(this)
        icon.addOnLayoutChangeListener(folderLayoutListener)
    }

    /** Aligns the frame with the folder it is resizing. */
    fun snapToFolder() {
        val view = folderIcon ?: return
        if (view.parent == null) return
        val rect = Rect()
        dragLayer.getViewRectRelativeToSelf(view, rect)
        val lp = layoutParams as? BaseDragLayer.LayoutParams ?: return
        lp.width = 2 * backgroundPadding + rect.width()
        lp.height = 2 * backgroundPadding + rect.height()
        lp.x = rect.left - backgroundPadding
        lp.y = rect.top - backgroundPadding
        // Only the initial reveal brightens every handle. Once the user has grabbed an edge,
        // the active/dimmed state set by beginResizeIfPointInRegion must survive the layout
        // passes that fire while resizing; re-brightening here made the handles flicker.
        if (!resizeInProgress) {
            for (handle in handles) handle?.alpha = 1f
        }
        requestLayout()
    }

    private fun handleTouchDown(ev: MotionEvent): Boolean {
        val hitRect = Rect()
        getHitRect(hitRect)
        val x = ev.x.toInt()
        val y = ev.y.toInt()
        if (hitRect.contains(x, y) && beginResizeIfPointInRegion(x - left, y - top)) {
            xDown = x
            yDown = y
            return true
        }
        return false
    }

    private fun beginResizeIfPointInRegion(x: Int, y: Int): Boolean {
        val borders = CaddyFolderResizeHandles.activeBorders(x, y, width, height, touchTargetWidth)
        leftActive = borders.left
        rightActive = borders.right
        topActive = borders.top
        bottomActive = borders.bottom
        if (borders.any) {
            resizeInProgress = true
            handles[INDEX_LEFT]?.alpha = if (leftActive) 1f else DIMMED_HANDLE_ALPHA
            handles[INDEX_RIGHT]?.alpha = if (rightActive) 1f else DIMMED_HANDLE_ALPHA
            handles[INDEX_TOP]?.alpha = if (topActive) 1f else DIMMED_HANDLE_ALPHA
            handles[INDEX_BOTTOM]?.alpha = if (bottomActive) 1f else DIMMED_HANDLE_ALPHA
        }
        return borders.any
    }

    private fun visualizeResizeForDelta(deltaX: Int, deltaY: Int) {
        val layout = cellLayout ?: return
        val view = folderIcon ?: return
        val dp = launcher.deviceProfile
        val xThreshold = (layout.cellWidth + dp.cellLayoutBorderSpacePx.x).toFloat()
        val yThreshold = (layout.cellHeight + dp.cellLayoutBorderSpacePx.y).toFloat()
        if (xThreshold <= 0f || yThreshold <= 0f) return

        val placement = CaddyFolderResize.resize(
            start = CaddyFolderResize.Placement(startCellX, startCellY, startSpanX, startSpanY),
            deltaCellsX = deltaX / xThreshold,
            deltaCellsY = deltaY / yThreshold,
            left = leftActive,
            right = rightActive,
            top = topActive,
            bottom = bottomActive,
            countX = layout.countX,
            countY = layout.countY,
        )
        val lp = view.layoutParams as? CellLayoutLayoutParams ?: return
        if (lp.cellX == placement.cellX && lp.cellY == placement.cellY &&
            lp.cellHSpan == placement.spanX && lp.cellVSpan == placement.spanY
        ) {
            return
        }
        // Match the widget resize safety rule: a folder may only claim cells that are
        // actually free. Temporarily release its current region before checking the
        // proposed one, then restore occupancy if the resize would overlap.
        layout.markCellsAsUnoccupiedForView(view)
        val regionIsVacant = layout.isRegionVacant(
            placement.cellX,
            placement.cellY,
            placement.spanX,
            placement.spanY,
        )
        if (!regionIsVacant) {
            layout.markCellsAsOccupiedForView(view)
            return
        }
        lp.cellX = placement.cellX
        lp.cellY = placement.cellY
        lp.cellHSpan = placement.spanX
        lp.cellVSpan = placement.spanY
        layout.markCellsAsOccupiedForView(view)
        view.requestLayout()
        post { snapToFolder() }
    }

    private fun onTouchUp() {
        val view = folderIcon ?: return
        val layout = cellLayout ?: return
        val folderInfo = info ?: return
        val lp = view.layoutParams as? CellLayoutLayoutParams ?: return
        folderInfo.cellX = lp.cellX
        folderInfo.cellY = lp.cellY
        folderInfo.spanX = lp.cellHSpan
        folderInfo.spanY = lp.cellVSpan
        launcher.modelWriter.modifyItemInDatabase(
            folderInfo,
            folderInfo.container,
            folderInfo.screenId,
            lp.cellX,
            lp.cellY,
            lp.cellHSpan,
            lp.cellVSpan,
        )
        post { snapToFolder() }
        layout.invalidate()
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Only a fresh touch-down closes the frame. The frame can be revealed while the
        // gesture that revealed it is still in flight (a long-press opens it before the
        // finger is lifted); closing on the trailing ACTION_UP/CANCEL of that same gesture
        // made the handles appear and vanish at once.
        if (ev.action == MotionEvent.ACTION_DOWN) {
            if (handleTouchDown(ev)) {
                return true
            }
            close(false)
        }
        return false
    }

    override fun onControllerTouchEvent(ev: MotionEvent): Boolean {
        val x = ev.x.toInt()
        val y = ev.y.toInt()
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> return handleTouchDown(ev)

            MotionEvent.ACTION_MOVE -> visualizeResizeForDelta(x - xDown, y - yDown)

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                visualizeResizeForDelta(x - xDown, y - yDown)
                onTouchUp()
                resizeInProgress = false
                xDown = 0
                yDown = 0
            }
        }
        return true
    }

    override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            close(true)
            return true
        }
        return false
    }

    override fun handleClose(animate: Boolean) {
        folderIcon?.removeOnLayoutChangeListener(folderLayoutListener)
        dragLayer.removeView(this)
    }

    override fun isOfType(type: Int): Boolean = (type and TYPE_WIDGET_RESIZE_FRAME) != 0

    companion object {
        private const val HANDLE_COUNT = 4
        private const val INDEX_LEFT = 0
        private const val INDEX_TOP = 1
        private const val INDEX_RIGHT = 2
        private const val INDEX_BOTTOM = 3
        private const val DIMMED_HANDLE_ALPHA = 0f

        /** Shows the resize frame around [icon] and keeps it in sync until closed. */
        @JvmStatic
        fun showForFolder(launcher: Launcher, icon: FolderIcon, layout: CellLayout) {
            if (icon.parent == null) return
            AbstractFloatingView.closeAllOpenViews(launcher, false)
            val dragLayer = launcher.dragLayer
            val frame = launcher.layoutInflater
                .inflate(R.layout.caddy_resize_frame, dragLayer, false) as CaddyFolderResizeFrame
            frame.setup(icon, layout)
            frame.setTag(icon.tag)
            (frame.layoutParams as BaseDragLayer.LayoutParams).customPosition = true
            dragLayer.addView(frame)
            frame.mIsOpen = true
            frame.post { frame.snapToFolder() }
        }
    }
}
