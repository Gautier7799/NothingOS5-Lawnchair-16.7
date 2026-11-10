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

package app.lawnchair.homecards

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import app.lawnchair.allapps.caddy.CaddyCardRenderer
import com.android.launcher3.Utilities
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.touch.ItemClickHandler
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A Caddy category card on the workspace.
 *
 * The card is rendered entirely by [CaddyCardRenderer], the same object the app
 * drawer uses, so a home card is identical to a drawer card. App icons reuse the
 * drawer's own click handler, and long-pressing an icon opens the native App Info
 * pop-up (App info / Uninstall / Personalise / Open in store) via
 * [CaddyCardIconLongClick].
 *
 * All gestures are handled by one listener on the card so they survive the card being
 * re-rendered (which happens whenever the launcher rebinds the workspace):
 *  - touch in the bottom-right corner -> resize to the dragged whole-cell span;
 *  - touch in the top-right corner -> remove the card;
 *  - long-press anywhere else -> pick the card up and drag it to another cell;
 *  - plain tap -> click the card (or advance a stack).
 */
class CaddyHomeCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    var onCardClick: (() -> Unit)? = null
    var onMove: ((deltaCellsX: Int, deltaCellsY: Int) -> Boolean)? = null
    var onResize: ((spanX: Int, spanY: Int) -> Boolean)? = null
    var onDelete: (() -> Boolean)? = null

    /** Called on a plain tap of a stacked card; returns true when it advanced the stack. */
    var onGroupTap: (() -> Boolean)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private var boundCard: CaddyHomeCardInfo? = null
    private var appResolver: ((String) -> AppInfo?)? = null
    private var lastCellPx = 0

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x59FFFFFF
        style = Paint.Style.STROKE
        strokeWidth = Utilities.dpToPx(2f, context).toFloat()
    }

    fun bind(card: CaddyHomeCardInfo, resolveApp: (String) -> AppInfo?) {
        boundCard = card
        appResolver = resolveApp
        setOnTouchListener(GestureListener(card))
        render()
    }

    /**
     * Renders the card. The icon size is derived from the card's real measured span, so the
     * icons stay inside their rows on any grid instead of overflowing and overlapping.
     */
    private fun render() {
        val card = boundCard ?: return
        val resolveApp = appResolver ?: return
        val columns = card.spanX.coerceAtLeast(MIN_SPAN)
        val rows = card.spanY.coerceAtLeast(MIN_SPAN)
        val maxIcons = columns * rows
        val cellPx = if (width > 0 && height > 0) {
            val horizontal = (width - dp(GRID_SIDE_INSET_DP)) / columns
            val vertical = (height - dp(TITLE_RESERVE_DP)) / rows
            maxOf(dp(MIN_CELL_DP), minOf(horizontal, vertical))
        } else {
            0
        }
        lastCellPx = cellPx
        CaddyCardRenderer.renderCategoryCard(
            host = this,
            title = card.title,
            apps = card.appKeys.mapNotNull(resolveApp),
            cardClick = { onCardClick?.invoke() },
            iconBinder = { icon, _ ->
                icon.setOnClickListener(ItemClickHandler.INSTANCE)
                icon.setOnLongClickListener(CaddyCardIconLongClick.LISTENER)
            },
            fitGridToHost = true,
            columns = columns,
            maxIcons = maxIcons,
            cellSizePx = cellPx,
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            val card = boundCard ?: return
            val columns = card.spanX.coerceAtLeast(MIN_SPAN)
            val rows = card.spanY.coerceAtLeast(MIN_SPAN)
            val horizontal = (w - dp(GRID_SIDE_INSET_DP)) / columns
            val vertical = (h - dp(TITLE_RESERVE_DP)) / rows
            val cell = maxOf(dp(MIN_CELL_DP), minOf(horizontal, vertical))
            if (cell != lastCellPx) render()
        }
    }

    /** Draws the two corner affordances: resize (bottom-right) and remove (top-right). */
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val inset = dp(CORNER_INSET_DP)
        val radius = dp(CORNER_ARC_DP)
        val right = width - inset
        val bottom = height - inset
        canvas.drawArc(
            (right - radius).toFloat(),
            (bottom - radius).toFloat(),
            right.toFloat(),
            bottom.toFloat(),
            0f,
            90f,
            false,
            cornerPaint,
        )
        val top = inset
        canvas.drawArc(
            (right - radius).toFloat(),
            top.toFloat(),
            right.toFloat(),
            (top + radius).toFloat(),
            270f,
            90f,
            false,
            cornerPaint,
        )
    }

    /** One listener for every gesture, so a re-render mid-gesture cannot break it. */
    private inner class GestureListener(private val card: CaddyHomeCardInfo) : OnTouchListener {

        private var startRawX = 0f
        private var startRawY = 0f
        private var dragging = false
        private var mode = MODE_NONE

        private val pickUp = Runnable {
            dragging = true
            lift()
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    dragging = false
                    mode = regionFor(event.x, event.y)
                    if (mode == MODE_MOVE) {
                        postDelayed(pickUp, longPressTimeout)
                    }
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    when (mode) {
                        MODE_MOVE -> {
                            val dx = event.rawX - startRawX
                            val dy = event.rawY - startRawY
                            if (!dragging) {
                                if (abs(dx) > touchSlop || abs(dy) > touchSlop) removeCallbacks(pickUp)
                                return true
                            }
                            translationX = dx
                            translationY = dy
                        }

                        MODE_RESIZE -> previewResize(event.rawX, event.rawY)

                        else -> Unit
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    removeCallbacks(pickUp)
                    when (mode) {
                        MODE_MOVE -> {
                            if (dragging) {
                                val delta = cellsFor(translationX, translationY)
                                resetLift()
                                onMove?.invoke(delta.first, delta.second)
                            } else if (onGroupTap?.invoke() != true) {
                                performClick()
                            }
                        }

                        MODE_RESIZE -> onResize?.invoke(targetSpanX, targetSpanY)

                        MODE_DELETE -> onDelete?.invoke()

                        else -> Unit
                    }
                    mode = MODE_NONE
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    removeCallbacks(pickUp)
                    if (dragging) resetLift()
                    dragging = false
                    mode = MODE_NONE
                    return true
                }
            }
            return false
        }

        private fun regionFor(x: Float, y: Float): Int {
            val hit = dp(CORNER_HIT_DP).toFloat()
            val nearRight = x > width - hit
            val nearBottom = y > height - hit
            val nearTop = y < hit
            return when {
                nearRight && nearBottom -> MODE_RESIZE
                nearRight && nearTop -> MODE_DELETE
                else -> MODE_MOVE
            }
        }

        private var targetSpanX = card.spanX
        private var targetSpanY = card.spanY

        private fun previewResize(rawX: Float, rawY: Float) {
            val cellWidth = (width / card.spanX.toFloat()).coerceAtLeast(1f)
            val cellHeight = (height / card.spanY.toFloat()).coerceAtLeast(1f)
            val deltaColumns = ((rawX - startRawX) / cellWidth).roundToInt()
            val deltaRows = ((rawY - startRawY) / cellHeight).roundToInt()
            targetSpanX = (card.spanX + deltaColumns).coerceAtLeast(MIN_SPAN)
            targetSpanY = (card.spanY + deltaRows).coerceAtLeast(MIN_SPAN)
            scaleX = (targetSpanX.toFloat() / card.spanX).coerceAtLeast(0.5f)
            scaleY = (targetSpanY.toFloat() / card.spanY).coerceAtLeast(0.5f)
        }

        private fun cellsFor(tx: Float, ty: Float): Pair<Int, Int> {
            val cellW = (width / card.spanX.toFloat()).coerceAtLeast(1f)
            val cellH = (height / card.spanY.toFloat()).coerceAtLeast(1f)
            return (tx / cellW).roundToInt() to (ty / cellH).roundToInt()
        }

        private fun lift() {
            animate().scaleX(1.05f).scaleY(1.05f)
                .translationZ(dp(8f).toFloat()).setDuration(120).start()
        }

        private fun resetLift() {
            animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(120).start()
            translationX = 0f
            translationY = 0f
            dragging = false
        }
    }

    private fun dp(value: Float): Int = Utilities.dpToPx(value, context)

    private companion object {
        const val MIN_SPAN = 2
        const val MODE_NONE = 0
        const val MODE_MOVE = 1
        const val MODE_RESIZE = 2
        const val MODE_DELETE = 3

        /** Space taken by the card's own horizontal padding when the grid is sized. */
        const val GRID_SIDE_INSET_DP = 36f

        /** Height reserved for the title row and paddings above the icon grid. */
        const val TITLE_RESERVE_DP = 44f

        /** Never shrink an icon slot below this, even on a dense grid. */
        const val MIN_CELL_DP = 40f

        /** How close to a corner a touch must start to hit that corner action. */
        const val CORNER_HIT_DP = 56f

        const val CORNER_INSET_DP = 8f
        const val CORNER_ARC_DP = 22f
    }
}
