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
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.CellLayout
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.dragndrop.DragLayer
import com.android.launcher3.views.BaseDragLayer

/**
 * The resize UI for a Dossier card.
 *
 * This mirrors the native app-widget resize frame ([com.android.launcher3.AppWidgetResizeFrame]):
 * a floating frame with the same four corner handles that follows the card, and the
 * same "drag an edge to add or remove a whole row/column" gesture. It is a separate
 * class only because the native frame is hard-wired to a `LauncherAppWidgetHostView`;
 * the visual language and the interaction are the launcher's own.
 *
 * On release the new placement is handed back through the `onResized` callback so the
 * caller can persist it.
 */
class DossierResizeFrame @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractFloatingView(context, attrs, defStyleAttr),
    View.OnKeyListener {

    private val launcher = Launcher.getLauncher(context)
    private val dragLayer: DragLayer = launcher.dragLayer

    private var cardView: View? = null
    private var cellLayout: CellLayout? = null
    private var card: DossierCardInfo? = null
    private var onResized: (DossierCardInfo) -> Unit = {}

    private val handles = arrayOfNulls<View>(HANDLE_COUNT)
    private val gestureExclusionRects = ArrayList<Rect>(HANDLE_COUNT)

    private val backgroundPadding =
        resources.getDimensionPixelSize(R.dimen.resize_frame_background_padding)
    private val desiredTouchTarget = 2 * backgroundPadding
    private val minTouchTarget = resources.getDimensionPixelSize(R.dimen.widget_handle_margin)

    /** Edge thickness actually used for hit testing, recomputed per gesture. */
    private var touchTargetWidth = 0

    private var leftActive = false
    private var rightActive = false
    private var topActive = false
    private var bottomActive = false

    /** True between a border touch-down and its touch-up; suppresses handle re-brightening. */
    private var resizeInProgress = false

    private var xDown = 0
    private var yDown = 0

    // Frame rectangle captured at touch-down, so the frame can follow the finger while the
    // drag is in flight (the native frame does the same in visualizeResizeForDelta).
    private var frameBaselineLeft = 0
    private var frameBaselineTop = 0
    private var frameBaselineRight = 0
    private var frameBaselineBottom = 0

    private var startCellX = 0
    private var startCellY = 0
    private var startSpanX = 1
    private var startSpanY = 1

    private val cardLayoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        snapToCard()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        handles[INDEX_LEFT] = findViewById(R.id.dossier_resize_left_handle)
        handles[INDEX_TOP] = findViewById(R.id.dossier_resize_top_handle)
        handles[INDEX_RIGHT] = findViewById(R.id.dossier_resize_right_handle)
        handles[INDEX_BOTTOM] = findViewById(R.id.dossier_resize_bottom_handle)
        for (i in 0 until HANDLE_COUNT) {
            gestureExclusionRects.add(Rect())
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        // The handles overhang the card; without excluding them from the system gesture
        // area a touch that starts there is stolen by the system before it reaches us.
        for (i in 0 until HANDLE_COUNT) {
            val handle = handles[i] ?: continue
            gestureExclusionRects[i].set(handle.left, handle.top, handle.right, handle.bottom)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            systemGestureExclusionRects = gestureExclusionRects
        }
    }

    override fun canInterceptEventsInSystemGestureRegion(): Boolean = true

    private fun setup(
        cardView: View,
        card: DossierCardInfo,
        cellLayout: CellLayout,
        onResized: (DossierCardInfo) -> Unit,
    ) {
        this.cardView = cardView
        this.card = card
        this.cellLayout = cellLayout
        this.onResized = onResized
        startCellX = card.cellX
        startCellY = card.cellY
        startSpanX = card.spanX
        startSpanY = card.spanY
        setOnKeyListener(this)
        cardView.addOnLayoutChangeListener(cardLayoutListener)
    }

    /** Aligns the frame with the card it is resizing. */
    fun snapToCard() {
        // While a resize is in flight the frame is driven by the finger, not by the card;
        // snapping here would fight the drag and make the frame stutter.
        if (resizeInProgress) return
        val view = cardView ?: return
        if (view.parent == null) return
        val rect = Rect()
        dragLayer.getViewRectRelativeToSelf(view, rect)
        val lp = layoutParams as? BaseDragLayer.LayoutParams ?: return
        lp.width = 2 * backgroundPadding + rect.width()
        lp.height = 2 * backgroundPadding + rect.height()
        lp.x = rect.left - backgroundPadding
        lp.y = rect.top - backgroundPadding
        for (handle in handles) handle?.alpha = 1f
        requestLayout()
    }

    /**
     * Starts a resize gesture for a touch-down given in DragLayer coordinates.
     *
     * Both dispatch paths funnel through here so they can never disagree: the controller
     * callbacks already hand over DragLayer coordinates, and the plain-view path converts
     * its local point before calling (see [onTouchEvent]).
     */
    private fun handleTouchDownAt(x: Int, y: Int): Boolean {
        val hitRect = Rect()
        getHitRect(hitRect)
        if (hitRect.contains(x, y) && beginResizeIfPointInRegion(x, y)) {
            // Each resize gesture starts from the card's latest committed placement.
            // The frame can remain open for several gestures, so values captured in setup()
            // would otherwise make a second drag jump back to the original span.
            val view = cardView
            val lp = view?.layoutParams as? CellLayoutLayoutParams
            if (lp != null) {
                startCellX = lp.cellX
                startCellY = lp.cellY
                startSpanX = lp.cellHSpan
                startSpanY = lp.cellVSpan
            }
            val frameLp = layoutParams as? BaseDragLayer.LayoutParams
            if (frameLp != null) {
                frameBaselineLeft = frameLp.x
                frameBaselineTop = frameLp.y
                frameBaselineRight = frameLp.x + frameLp.width
                frameBaselineBottom = frameLp.y + frameLp.height
            }
            xDown = x
            yDown = y
            return true
        }
        return false
    }

    private fun beginResizeIfPointInRegion(dragLayerX: Int, dragLayerY: Int): Boolean {
        // Cap the edge target so a single-cell card keeps a free centre: otherwise 48dp per
        // side covers the whole card and every tap reads as a resize grab.
        touchTargetWidth = DossierResizeHandles.effectiveTouchTarget(
            width = width,
            height = height,
            desired = desiredTouchTarget,
            minTouchTarget = minTouchTarget,
        )
        val borders = DossierResizeHandles.activeBordersForDragLayerPoint(
            dragLayerX = dragLayerX,
            dragLayerY = dragLayerY,
            frameLeft = left,
            frameTop = top,
            frameWidth = width,
            frameHeight = height,
            touchTargetWidth = touchTargetWidth,
        )
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

    /** Moves the frame with the finger so the resize is visible immediately. */
    private fun applyFrameDelta(deltaX: Int, deltaY: Int) {
        if (!resizeInProgress) return
        val lp = layoutParams as? BaseDragLayer.LayoutParams ?: return
        var left = frameBaselineLeft
        var right = frameBaselineRight
        var top = frameBaselineTop
        var bottom = frameBaselineBottom
        if (leftActive) left += deltaX
        if (rightActive) right += deltaX
        if (topActive) top += deltaY
        if (bottomActive) bottom += deltaY
        val minSize = 2 * backgroundPadding
        if (right - left < minSize) {
            if (leftActive) left = right - minSize else right = left + minSize
        }
        if (bottom - top < minSize) {
            if (topActive) top = bottom - minSize else bottom = top + minSize
        }
        left = left.coerceIn(0, dragLayer.width)
        right = right.coerceIn(0, dragLayer.width)
        top = top.coerceIn(0, dragLayer.height)
        bottom = bottom.coerceIn(0, dragLayer.height)
        lp.x = left
        lp.y = top
        lp.width = right - left
        lp.height = bottom - top
        requestLayout()
    }

    private fun visualizeResizeForDelta(deltaX: Int, deltaY: Int) {
        val cellLayout = cellLayout ?: return
        val cardView = cardView ?: return
        val dp = launcher.deviceProfile
        val xThreshold = (cellLayout.cellWidth + dp.cellLayoutBorderSpacePx.x).toFloat()
        val yThreshold = (cellLayout.cellHeight + dp.cellLayoutBorderSpacePx.y).toFloat()
        if (xThreshold <= 0f || yThreshold <= 0f) return

        val placement = DossierResizeBounds.resize(
            start = DossierResizeBounds.Placement(startCellX, startCellY, startSpanX, startSpanY),
            deltaCellsX = deltaX / xThreshold,
            deltaCellsY = deltaY / yThreshold,
            left = leftActive,
            right = rightActive,
            top = topActive,
            bottom = bottomActive,
            countX = cellLayout.countX,
            countY = cellLayout.countY,
        )
        val lp = cardView.layoutParams as? CellLayoutLayoutParams ?: return
        if (lp.cellX == placement.cellX && lp.cellY == placement.cellY &&
            lp.cellHSpan == placement.spanX && lp.cellVSpan == placement.spanY
        ) {
            return
        }
        // Match the widget resize safety rule: a Dossier may only claim cells that
        // are actually free. Temporarily release its current region before checking
        // the proposed one, then restore occupancy if the resize would overlap.
        cellLayout.markCellsAsUnoccupiedForView(cardView)
        val regionIsVacant = cellLayout.isRegionVacant(
            placement.cellX,
            placement.cellY,
            placement.spanX,
            placement.spanY,
        )
        if (!regionIsVacant) {
            cellLayout.markCellsAsOccupiedForView(cardView)
            return
        }
        lp.cellX = placement.cellX
        lp.cellY = placement.cellY
        lp.cellHSpan = placement.spanX
        lp.cellVSpan = placement.spanY
        cellLayout.markCellsAsOccupiedForView(cardView)
        cardView.requestLayout()
        post { snapToCard() }
    }

    private fun onTouchUp() {
        val cardView = cardView ?: return
        val cellLayout = cellLayout ?: return
        val lp = cardView.layoutParams as? CellLayoutLayoutParams ?: return
        val base = card ?: return
        val updated = base.copy(
            cellX = lp.cellX,
            cellY = lp.cellY,
            spanX = lp.cellHSpan,
            spanY = lp.cellVSpan,
        )
        onResized(updated)
        post { snapToCard() }
        cellLayout.invalidate()
    }

    /** Restores the placement captured at the start of the current gesture. */
    private fun restoreResizeStart() {
        val cardView = cardView ?: return
        val cellLayout = cellLayout ?: return
        val lp = cardView.layoutParams as? CellLayoutLayoutParams ?: return

        cellLayout.markCellsAsUnoccupiedForView(cardView)
        lp.cellX = startCellX
        lp.cellY = startCellY
        lp.cellHSpan = startSpanX
        lp.cellVSpan = startSpanY
        cellLayout.markCellsAsOccupiedForView(cardView)
        cardView.requestLayout()
        cellLayout.invalidate()
    }

    /** Clears gesture-only state after either a committed or cancelled resize. */
    private fun finishResizeGesture() {
        resizeInProgress = false
        leftActive = false
        rightActive = false
        topActive = false
        bottomActive = false
        xDown = 0
        yDown = 0
        snapToCard()
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Only a fresh touch-down closes the frame. The frame can be revealed while the
        // gesture that revealed it is still in flight (a long-press opens it before the
        // finger is lifted); closing on the trailing ACTION_UP/CANCEL of that same gesture
        // made the handles appear and vanish at once.
        if (ev.action == MotionEvent.ACTION_DOWN) {
            if (handleTouchDownAt(ev.x.toInt(), ev.y.toInt())) {
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
            MotionEvent.ACTION_DOWN -> return handleTouchDownAt(x, y)

            MotionEvent.ACTION_MOVE -> {
                visualizeResizeForDelta(x - xDown, y - yDown)
                applyFrameDelta(x - xDown, y - yDown)
            }

            MotionEvent.ACTION_UP -> {
                visualizeResizeForDelta(x - xDown, y - yDown)
                onTouchUp()
                finishResizeGesture()
            }

            MotionEvent.ACTION_CANCEL -> {
                // Cancellation is not a successful resize. Restore the exact placement
                // captured at ACTION_DOWN and do not write a partial gesture to the DB.
                restoreResizeStart()
                finishResizeGesture()
            }
        }
        return true
    }

    /**
     * Handles the touch events the launcher dispatches straight to this view.
     *
     * The launcher only consults its TouchControllers when a touch starts outside a
     * mandatory system-gesture strip; inside the strip the event is delivered to this view
     * as an ordinary child instead (see
     * [com.android.launcher3.views.BaseDragLayer.canFindActiveController]). The frame's
     * handles overhang the card, so a grab routinely starts in that strip. Without this
     * handler the inherited [AbstractFloatingView.onTouchEvent] simply consumed the gesture
     * and the card never resized. Points arrive in local coordinates here, so they are
     * converted to the DragLayer space the resize maths expects.
     */
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!resizeInProgress && ev.action != MotionEvent.ACTION_DOWN) return true
        val x = ev.x.toInt() + left
        val y = ev.y.toInt() + top
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                if (handleTouchDownAt(x, y)) return true
                // A tap that misses the handles asks to leave the frame. Defer the close so
                // this view is not pulled out of the DragLayer mid-dispatch, and decline the
                // event so the tap still reaches the card underneath.
                post { close(false) }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!resizeInProgress) return false
                visualizeResizeForDelta(x - xDown, y - yDown)
                applyFrameDelta(x - xDown, y - yDown)
            }

            MotionEvent.ACTION_UP -> {
                if (resizeInProgress) {
                    visualizeResizeForDelta(x - xDown, y - yDown)
                    onTouchUp()
                    finishResizeGesture()
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (resizeInProgress) {
                    restoreResizeStart()
                    finishResizeGesture()
                }
            }
        }
        return true
    }

    override fun onKey(v: View, keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
            close(true)
            return true
        }
        return false
    }

    override fun handleClose(animate: Boolean) {
        cardView?.removeOnLayoutChangeListener(cardLayoutListener)
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

        /** Shows the resize frame around [cardView] and keeps it in sync until closed. */
        @JvmStatic
        fun showForCard(
            launcher: Launcher,
            cardView: View,
            card: DossierCardInfo,
            cellLayout: CellLayout,
            onResized: (DossierCardInfo) -> Unit,
        ) {
            if (cardView.parent == null) return
            AbstractFloatingView.closeAllOpenViews(launcher, false)
            val dragLayer = launcher.dragLayer
            val frame = launcher.layoutInflater
                .inflate(R.layout.dossier_resize_frame, dragLayer, false) as DossierResizeFrame
            frame.setup(cardView, card, cellLayout, onResized)
            frame.setTag(cardView.tag)
            (frame.layoutParams as BaseDragLayer.LayoutParams).customPosition = true
            dragLayer.addView(frame)
            frame.mIsOpen = true
            frame.post { frame.snapToCard() }
        }
    }
}
