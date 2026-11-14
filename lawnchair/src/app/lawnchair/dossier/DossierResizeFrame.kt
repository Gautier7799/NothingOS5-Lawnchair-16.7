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

    private val backgroundPadding =
        resources.getDimensionPixelSize(R.dimen.resize_frame_background_padding)
    private val touchTargetWidth = 2 * backgroundPadding

    private var leftActive = false
    private var rightActive = false
    private var topActive = false
    private var bottomActive = false

    private var xDown = 0
    private var yDown = 0

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
    }

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
        leftActive = x < touchTargetWidth
        rightActive = x > width - touchTargetWidth
        topActive = y < touchTargetWidth
        bottomActive = y > height - touchTargetWidth
        val anyActive = leftActive || rightActive || topActive || bottomActive
        if (anyActive) {
            handles[INDEX_LEFT]?.alpha = if (leftActive) 1f else DIMMED_HANDLE_ALPHA
            handles[INDEX_RIGHT]?.alpha = if (rightActive) 1f else DIMMED_HANDLE_ALPHA
            handles[INDEX_TOP]?.alpha = if (topActive) 1f else DIMMED_HANDLE_ALPHA
            handles[INDEX_BOTTOM]?.alpha = if (bottomActive) 1f else DIMMED_HANDLE_ALPHA
        }
        return anyActive
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
        // Reorder-free resize: Dossier cards always grow into free space, so we only
        // re-mark the occupied cells instead of running the widget reorder algorithm.
        cellLayout.markCellsAsUnoccupiedForView(cardView)
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

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN && handleTouchDown(ev)) {
            return true
        }
        close(false)
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
                xDown = 0
                yDown = 0
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
