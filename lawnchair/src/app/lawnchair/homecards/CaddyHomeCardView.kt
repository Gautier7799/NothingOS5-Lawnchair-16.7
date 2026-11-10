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
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import app.lawnchair.allapps.caddy.CaddyCardRenderer
import com.android.launcher3.Utilities
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.touch.ItemClickHandler
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
 * A card is resizable: dragging the handle in its bottom-right corner reports a new
 * grid size through [onResize], which the controller persists. The card's grid grows
 * with the card, so a taller or wider card reveals more apps at once.
 */
class CaddyHomeCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    var onCardClick: (() -> Unit)? = null
    var onCardLongClick: (() -> Boolean)? = null
    var onResize: ((spanX: Int, spanY: Int) -> Boolean)? = null

    fun bind(card: CaddyHomeCardInfo, resolveApp: (String) -> AppInfo?) {
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
            columns = card.spanX.coerceAtLeast(MIN_SPAN),
            maxIcons = card.spanX.coerceAtLeast(MIN_SPAN) * card.spanY.coerceAtLeast(MIN_SPAN),
        )
        setOnLongClickListener { onCardLongClick?.invoke() ?: false }
        attachResizeHandle(card)
    }

    private fun attachResizeHandle(card: CaddyHomeCardInfo) {
        val handleSize = Utilities.dpToPx(HANDLE_SIZE_DP, context)
        val handle = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x66FFFFFF)
                setStroke(Utilities.dpToPx(1f, context), 0xAAFFFFFF.toInt())
            }
            contentDescription = "Resize card"
        }
        addView(
            handle,
            LayoutParams(handleSize, handleSize, Gravity.BOTTOM or Gravity.END).apply {
                val margin = Utilities.dpToPx(4f, context)
                setMargins(margin, margin, margin, margin)
            },
        )
        handle.setOnTouchListener(ResizeTouchListener(card))
    }

    /** Turns a drag on the handle into a whole-cell span change, applied on release. */
    private inner class ResizeTouchListener(private val card: CaddyHomeCardInfo) : OnTouchListener {

        private var startRawX = 0f
        private var startRawY = 0f
        private var targetSpanX = card.spanX
        private var targetSpanY = card.spanY

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    targetSpanX = card.spanX.coerceAtLeast(MIN_SPAN)
                    targetSpanY = card.spanY.coerceAtLeast(MIN_SPAN)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val cellWidth = (width / targetSpanX.toFloat()).coerceAtLeast(1f)
                    val cellHeight = (height / targetSpanY.toFloat()).coerceAtLeast(1f)
                    val deltaColumns = ((event.rawX - startRawX) / cellWidth).roundToInt()
                    val deltaRows = ((event.rawY - startRawY) / cellHeight).roundToInt()
                    targetSpanX = (card.spanX + deltaColumns).coerceAtLeast(MIN_SPAN)
                    targetSpanY = (card.spanY + deltaRows).coerceAtLeast(MIN_SPAN)
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        onResize?.invoke(targetSpanX, targetSpanY)
                    }
                    return true
                }
            }
            return false
        }
    }

    private companion object {
        const val MIN_SPAN = 2
        const val HANDLE_SIZE_DP = 22f
    }
}
