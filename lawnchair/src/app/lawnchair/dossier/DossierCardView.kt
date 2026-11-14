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
import android.view.View
import android.widget.FrameLayout
import app.lawnchair.allapps.caddy.CaddyCardRenderer
import com.android.launcher3.CellLayout
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.dragndrop.DraggableView
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.touch.ItemClickHandler
import com.android.launcher3.touch.ItemLongClickListener

/** Workspace view for a database-backed Dossier item. */
class DossierCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), DraggableView {

    override fun getViewType(): Int = DraggableView.DRAGGABLE_WIDGET


    var onCardClick: (() -> Unit)? = null

    private var bound: DossierInfo? = null
    private var resolveApp: ((String) -> AppInfo?)? = null
    private var rendering = false

    fun bind(
        info: DossierInfo,
        resolveApp: (String) -> AppInfo?,
        launcher: Launcher,
    ) {
        bound = info
        tag = info
        isFocusable = true
        this.resolveApp = resolveApp
        // Normal workspace long-press starts the native drag flow. In edit mode,
        // keep the existing resize-frame interaction as a separate operation.
        setOnLongClickListener {
            if (launcher.isInState(LauncherState.EDIT_MODE)) {
                showResizeFrame(info, launcher)
            } else {
                ItemLongClickListener.INSTANCE_WORKSPACE.onLongClick(this)
            }
        }
        renderIfMeasured()
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
            val availableWidth = (width - 2 * gutter).coerceAtLeast(0)
            val availableHeight = (height - 2 * gutter - dp(TITLE_DP)).coerceAtLeast(0)
            val targetCell = dp(TARGET_CELL_DP).coerceAtLeast(1)
            val pixelColumns = (availableWidth / targetCell).coerceAtLeast(1)
            val pixelRows = (availableHeight / targetCell).coerceAtLeast(1)
            val columns = minOf(info.gridColumns, pixelColumns).coerceAtLeast(1)
            val rows = minOf(info.gridRows, pixelRows).coerceAtLeast(1)
            val cellSizePx = minOf(availableWidth / columns, availableHeight / rows)

            // Do not clear already-rendered icons while AllAppsStore is temporarily
            // incomplete (for example during profile/package refresh). Keep the last
            // stable rendering until every persisted key can be resolved again.
            val resolvedApps = info.appKeys.mapNotNull(resolve)
            if (resolvedApps.size != info.appKeys.size) return

            CaddyCardRenderer.renderCategoryCard(
                host = this,
                title = info.title?.toString().orEmpty(),
                apps = resolvedApps,
                cardClick = { onCardClick?.invoke() },
                iconBinder = { icon, _ ->
                    icon.setOnClickListener(ItemClickHandler.INSTANCE)
                    icon.setOnLongClickListener { performLongClick() }
                },
                fitGridToHost = true,
                columns = columns,
                maxIcons = columns * rows,
                cellSizePx = cellSizePx,
            )
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
    }
}
