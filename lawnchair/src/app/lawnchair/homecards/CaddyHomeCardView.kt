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
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import app.lawnchair.allapps.caddy.CaddyCardRenderer
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.BubbleTextView
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
 * Gestures are split by where the touch starts, and the card claims the ones the icons
 * must not swallow:
 *  - bottom-right corner -> resize to the dragged whole-cell span;
 *  - top-right corner -> remove the card;
 *  - a long-press on the card's own surface (not on an icon) -> pick the card up and drag it;
 *  - a tap on the card's own surface -> open the category;
 *  - a touch on an icon -> left entirely to the icon, so its click and App Info long-press work.
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

    /** Whether the card draws its title. */
    var showTitle: Boolean = true

    /** Icons per row; the card's height decides how many rows fit. */
    var iconColumns: Int = 2

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val homeScreenLocked: Boolean
        get() = PreferenceManager2.getInstance(context).lockHomeScreen.firstCached()
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private var boundCard: CaddyHomeCardInfo? = null
    private var appResolver: ((String) -> AppInfo?)? = null
    private var lastCellPx = 0
    private var lastVerticalReservePx = -1
    private var measuredTitleHeightPx = 0

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x59FFFFFF
        style = Paint.Style.STROKE
        strokeWidth = Utilities.dpToPx(2f, context).toFloat()
    }

    // Gesture state, shared by the intercept and the touch handler.
    private var mode = MODE_NONE
    private var startRawX = 0f
    private var startRawY = 0f
    private var dragging = false
    private var targetSpanX = MIN_SPAN
    private var targetSpanY = MIN_SPAN

    private val pickUp = Runnable {
        dragging = true
        animate().scaleX(1.05f).scaleY(1.05f)
            .translationZ(dp(8f).toFloat()).setDuration(120).start()
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun bind(card: CaddyHomeCardInfo, resolveApp: (String) -> AppInfo?) {
        boundCard = card
        appResolver = resolveApp
        render()
    }

    /**
     * Renders the card. The icon size is derived from the card's real measured span, so the
     * icons stay inside their rows on any grid instead of overflowing and overlapping.
     */
    private fun render() {
        val card = boundCard ?: return
        val resolveApp = appResolver ?: return
        val columns = iconColumns.coerceIn(1, card.spanX.coerceAtLeast(1))
        val rows = card.spanY.coerceAtLeast(MIN_SPAN)
        val maxIcons = columns * rows
        val verticalReserve = verticalReservePx()
        val cellPx = CaddyHomeCardLayout.cellSizePx(
            width = width,
            height = height,
            columns = columns,
            rows = rows,
            sideInsetPx = dp(GRID_SIDE_INSET_DP),
            titleReservePx = verticalReserve,
        )
        lastCellPx = cellPx
        lastVerticalReservePx = verticalReserve
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
            showTitle = showTitle,
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            val card = boundCard ?: return
            val columns = iconColumns.coerceIn(1, card.spanX.coerceAtLeast(1))
            val rows = card.spanY.coerceAtLeast(MIN_SPAN)
            val verticalReserve = verticalReservePx()
            val cell = CaddyHomeCardLayout.cellSizePx(
                width = w,
                height = h,
                columns = columns,
                rows = rows,
                sideInsetPx = dp(GRID_SIDE_INSET_DP),
                titleReservePx = verticalReserve,
            )
            if (cell != lastCellPx || verticalReserve != lastVerticalReservePx) render()
        }
    }

    /**
     * Measures the vertical space consumed by the rendered card chrome. Prefer the title's
     * real measured height; before its first layout use a conservative single-line estimate.
     * The remaining height is the only space available to the icon rows.
     */
    private fun verticalReservePx(): Int {
        val card = getChildAt(0) as? LinearLayout
        val hostVerticalPadding = paddingTop + paddingBottom
        val cardVerticalPadding = card?.let { it.paddingTop + it.paddingBottom } ?: dp(16f)
        val measuredTitle = card?.getChildAt(0)?.measuredHeight ?: 0
        if (measuredTitle > 0) measuredTitleHeightPx = measuredTitle
        val titleHeight = if (showTitle) {
            measuredTitleHeightPx.takeIf { it > 0 } ?: dp(24f)
        } else {
            0
        }
        val gridIndex = if (showTitle) 1 else 0
        val gridTopMargin = (card?.getChildAt(gridIndex)?.layoutParams as? LinearLayout.LayoutParams)
            ?.topMargin ?: dp(6f)
        return CaddyHomeCardLayout.verticalReservePx(
            hostPaddingPx = hostVerticalPadding,
            cardPaddingPx = cardVerticalPadding,
            titleHeightPx = titleHeight,
            gridTopMarginPx = gridTopMargin,
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (width <= 0 || height <= 0 || boundCard == null) return
        val verticalReserve = verticalReservePx()
        if (verticalReserve != lastVerticalReservePx) render()
    }

    /** Draws the two corner affordances: resize (bottom-right) and remove (top-right). */
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (homeScreenLocked) return
        val inset = dp(CORNER_INSET_DP)
        val radius = dp(CORNER_ARC_DP)
        val right = width - inset
        canvas.drawArc(
            (right - radius).toFloat(),
            (height - inset - radius).toFloat(),
            right.toFloat(),
            (height - inset).toFloat(),
            0f,
            90f,
            false,
            cornerPaint,
        )
        canvas.drawArc(
            (right - radius).toFloat(),
            inset.toFloat(),
            right.toFloat(),
            (inset + radius).toFloat(),
            270f,
            90f,
            false,
            cornerPaint,
        )
    }

    /**
     * Claims corner touches (which may land on an icon) and long-presses on the card's own
     * surface (which must not become an icon long-click). Everything else, in particular any
     * touch on an icon, is left to the children so normal clicks and App Info keep working.
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            startRawX = ev.rawX
            startRawY = ev.rawY
            dragging = false
            val region = if (homeScreenLocked) MODE_MOVE else regionFor(ev.x, ev.y)
            mode = when {
                region != MODE_MOVE -> region
                hitTest(this, ev.x, ev.y) is BubbleTextView -> MODE_NONE
                else -> MODE_MOVE
            }
            if (mode != MODE_NONE) {
                if (mode == MODE_MOVE && !homeScreenLocked) postDelayed(pickUp, longPressTimeout)
                return true
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
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
                            val card = boundCard
                            val delta = if (card != null) cellsFor(card, translationX, translationY) else 0 to 0
                            resetLift()
                            onMove?.invoke(delta.first, delta.second)
                        } else if (onGroupTap?.invoke() != true) {
                            performClick()
                        }
                    }

                    MODE_RESIZE -> {
                        resetScale()
                        onResize?.invoke(targetSpanX, targetSpanY)
                    }

                    MODE_DELETE -> onDelete?.invoke()

                    else -> Unit
                }
                mode = MODE_NONE
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(pickUp)
                if (dragging) resetLift()
                if (mode == MODE_RESIZE) resetScale()
                dragging = false
                mode = MODE_NONE
                return true
            }
        }
        return super.onTouchEvent(event)
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

    private fun previewResize(rawX: Float, rawY: Float) {
        val card = boundCard ?: return
        val cellWidth = (width / card.spanX.toFloat()).coerceAtLeast(1f)
        val cellHeight = (height / card.spanY.toFloat()).coerceAtLeast(1f)
        val deltaColumns = ((rawX - startRawX) / cellWidth).roundToInt()
        val deltaRows = ((rawY - startRawY) / cellHeight).roundToInt()
        targetSpanX = (card.spanX + deltaColumns).coerceAtLeast(MIN_SPAN)
        targetSpanY = (card.spanY + deltaRows).coerceAtLeast(MIN_SPAN)
        scaleX = (targetSpanX.toFloat() / card.spanX).coerceAtLeast(0.5f)
        scaleY = (targetSpanY.toFloat() / card.spanY).coerceAtLeast(0.5f)
    }

    private fun cellsFor(card: CaddyHomeCardInfo, tx: Float, ty: Float): Pair<Int, Int> {
        val cellW = (width / card.spanX.toFloat()).coerceAtLeast(1f)
        val cellH = (height / card.spanY.toFloat()).coerceAtLeast(1f)
        return (tx / cellW).roundToInt() to (ty / cellH).roundToInt()
    }

    private fun resetLift() {
        animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(120).start()
        translationX = 0f
        translationY = 0f
        dragging = false
    }

    private fun resetScale() {
        animate().scaleX(1f).scaleY(1f).setDuration(120).start()
    }

    /** The deepest visible descendant at ([x], [y]), in this view's coordinates. */
    private fun hitTest(view: View, x: Float, y: Float): View? {
        if (view.visibility != View.VISIBLE) return null
        val left = view.left - view.scrollX
        val top = view.top - view.scrollY
        if (x < left || x > left + view.width || y < top || y > top + view.height) return null
        if (view is ViewGroup) {
            for (index in view.childCount - 1 downTo 0) {
                hitTest(view.getChildAt(index), x - left, y - top)?.let { return it }
            }
        }
        return view
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

        /** How close to a corner a touch must start to hit that corner action. */
        const val CORNER_HIT_DP = 56f

        const val CORNER_INSET_DP = 8f
        const val CORNER_ARC_DP = 22f
    }
}
