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
import android.util.AttributeSet
import android.widget.FrameLayout
import app.lawnchair.allapps.caddy.CaddyCardRenderer
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.touch.ItemClickHandler
import com.android.launcher3.touch.ItemLongClickListener

/**
 * A Dossier card on the workspace.
 *
 * The card is drawn by [CaddyCardRenderer] with `fitGridToHost = true`, so the
 * icon grid measures itself against the card's real (resizable) grid area rather
 * than a fixed preview size. Because the card is resized by changing its
 * CellLayout span, the icon grid is re-rendered whenever the view is measured at
 * a new size, so a resized Dossier shows more or fewer apps instead of stretching
 * the old layout. App icons reuse the drawer's own click and long-click handlers,
 * so tapping an icon launches the app and long-pressing it behaves exactly as it
 * does inside the drawer.
 */
class DossierCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Invoked when the card is long-pressed; opens the native resize UI. */
    var onCardResizeRequested: (() -> Unit)? = null

    /** Invoked when the card itself is tapped. */
    var onCardClick: (() -> Unit)? = null

    private var bound: DossierCardInfo? = null
    private var resolveApp: ((String) -> AppInfo?)? = null
    private var rendering = false

    fun bind(card: DossierCardInfo, resolveApp: (String) -> AppInfo?) {
        bound = card
        this.resolveApp = resolveApp
        setOnLongClickListener {
            onCardResizeRequested?.invoke()
            true
        }
        renderIfMeasured()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) renderIfMeasured()
    }

    private fun renderIfMeasured() {
        val card = bound ?: return
        val resolve = resolveApp ?: return
        if (width <= 0 || height <= 0) return
        if (rendering) return
        rendering = true
        try {
            val gutter = dp(GUTTER_DP) + dp(CARD_PADDING_DP)
            val availableWidth = (width - 2 * gutter).coerceAtLeast(0)
            val availableHeight = (height - 2 * gutter - dp(TITLE_DP)).coerceAtLeast(0)

            // Fit as many icon cells as the card's real size allows, so a larger
            // Dossier shows more apps instead of stretching the original grid.
            val targetCell = dp(TARGET_CELL_DP).coerceAtLeast(1)
            val columns = (availableWidth / targetCell).coerceAtLeast(1)
            val rows = (availableHeight / targetCell).coerceAtLeast(1)
            val cellSizePx = minOf(availableWidth / columns, availableHeight / rows)

            CaddyCardRenderer.renderCategoryCard(
                host = this,
                title = card.title,
                apps = card.appKeys.mapNotNull(resolve),
                cardClick = { onCardClick?.invoke() },
                iconBinder = { icon, _ ->
                    icon.setOnClickListener(ItemClickHandler.INSTANCE)
                    icon.setOnLongClickListener(ItemLongClickListener.INSTANCE_ALL_APPS)
                },
                fitGridToHost = true,
                columns = columns,
                maxIcons = columns * rows,
                cellSizePx = cellSizePx,
            )
            // The renderer's card consumes touches, so the long-press that opens the
            // resize UI must be attached to the card it draws, not to this host.
            getChildAt(0)?.setOnLongClickListener {
                onCardResizeRequested?.invoke()
                true
            }
        } finally {
            rendering = false
        }
    }

    /** The card currently rendered, or null before the first bind. */
    fun currentCard(): DossierCardInfo? = bound

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        /** Preferred icon-cell size; the grid fits as many as the card allows. */
        const val TARGET_CELL_DP = 60f

        /** Mirrors [CaddyCardRenderer]'s own outer gutter and inner padding. */
        const val GUTTER_DP = 4f
        const val CARD_PADDING_DP = 14f
        const val TITLE_DP = 24f
    }
}
