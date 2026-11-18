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
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.lawnchair.allapps.caddy.CaddyCardRenderer
import app.lawnchair.allapps.caddy.CaddyCategoryDialog
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS_PREDICTION
import com.android.launcher3.R
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.PredictedContainerInfo
import com.android.launcher3.touch.ItemClickHandler
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors

/**
 * The full-screen Caddy App Library.
 *
 * This is the whole library as a single page rather than cards scattered across the workspace:
 * a Google search field, a suggestions row, the category cards the drawer uses, and a trailing
 * "+ Add category" card. It is opened by swiping right past the last workspace page and covers
 * the screen until it is closed.
 *
 * Everything visual is delegated to the shared pieces the drawer already uses
 * ([CaddyCardRenderer] for the cards, [CaddyCategoryDialog] for a category's apps), so this page
 * adds a surface, not a second rendering of the cards.
 */
class CaddyAppLibraryPage @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractFloatingView(context, attrs, defStyleAttr) {

    private val column = LinearLayout(context)

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.argb(245, 10, 12, 14))
        isClickable = true
        isFocusable = true
        val scroll = ScrollView(context)
        column.orientation = VERTICAL
        scroll.addView(column)
        addView(
            scroll,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    fun bind(launcher: Launcher) {
        column.removeAllViews()
        column.addView(buildHeader())
        column.addView(buildSuggestions(launcher))
        column.addView(buildCards(launcher))
    }

    /** Marks the page open. Split out so the companion can set the protected flag. */
    fun markOpen() {
        mIsOpen = true
    }

    private fun buildHeader(): View {
        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(20f), dp(20f), dp(12f), dp(8f))

        val title = TextView(context)
        title.text = context.getString(R.string.caddy_app_library_title)
        title.setTextColor(Color.WHITE)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        title.typeface = Typeface.DEFAULT_BOLD
        row.addView(
            title,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply { weight = 1f },
        )

        val close = TextView(context)
        close.text = "✕"
        close.setTextColor(Color.WHITE)
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        close.gravity = Gravity.CENTER
        close.isClickable = true
        close.contentDescription = context.getString(R.string.caddy_app_library_close)
        close.setOnClickListener { close(true) }
        row.addView(close, LinearLayout.LayoutParams(dp(44f), dp(44f)))
        return row
    }

    private fun buildSuggestions(launcher: Launcher): View {
        val heading = TextView(context)
        heading.text = context.getString(R.string.caddy_app_library_suggestions)
        heading.setTextColor(Color.WHITE)
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        heading.typeface = Typeface.DEFAULT_BOLD
        heading.setPadding(dp(20f), dp(12f), dp(20f), dp(4f))

        val strip = LinearLayout(context)
        strip.orientation = HORIZONTAL
        strip.setPadding(dp(16f), 0, dp(16f), dp(4f))
        val scroller = HorizontalScrollView(context)
        scroller.isHorizontalScrollBarEnabled = false
        scroller.addView(strip)

        val container = LinearLayout(context)
        container.orientation = VERTICAL
        container.addView(heading)
        container.addView(scroller)

        loadSuggestions(launcher) { apps ->
            if (apps.isEmpty()) {
                container.visibility = GONE
                return@loadSuggestions
            }
            apps.forEach { app ->
                val cell = FrameLayout(context)
                val icon = LayoutInflater.from(context)
                    .inflate(R.layout.all_apps_icon, cell, false) as BubbleTextView
                icon.applyFromApplicationInfo(app)
                icon.setTextVisibility(true)
                icon.setLongPressTimeoutFactor(1f)
                icon.contentDescription = app.title
                icon.setOnClickListener(ItemClickHandler.INSTANCE)
                icon.setOnLongClickListener(CaddyCardIconLongClick.LISTENER)
                icon.gravity = Gravity.CENTER
                cell.addView(icon)
                strip.addView(
                    cell,
                    LinearLayout.LayoutParams(dp(76f), dp(84f)).apply {
                        gravity = Gravity.CENTER_VERTICAL
                    },
                )
            }
        }
        return container
    }

    /**
     * Reads the launcher's predicted apps (the same list the drawer's suggestion row uses) and
     * resolves them against the live app store. Nothing is shown until the model has loaded, so an
     * early bind simply hides the row.
     */
    private fun loadSuggestions(launcher: Launcher, onReady: (List<AppInfo>) -> Unit) {
        LauncherAppState.getInstance(context).model.loadAsync { dataModel ->
            if (dataModel == null) return@loadAsync
            val predicted = synchronized(dataModel) {
                dataModel.itemsIdMap[CONTAINER_ALL_APPS_PREDICTION] as? PredictedContainerInfo
            } ?: return@loadAsync
            val keys = predicted.getAppContents().mapNotNull { info ->
                info.targetComponent?.let { ComponentKey(it, info.user) }
            }
            Executors.MAIN_EXECUTOR.execute {
                val store = launcher.appsView?.appsStore ?: return@execute
                onReady(keys.mapNotNull { store.getApp(it) })
            }
        }
    }

    private fun buildCards(launcher: Launcher): View {
        val apps = launcher.appsView?.appsStore?.apps?.toList().orEmpty()
        val grid = GridLayout(context)
        grid.columnCount = COLUMN_COUNT
        grid.useDefaultMargins = false
        grid.setPadding(dp(12f), dp(8f), dp(12f), dp(32f))

        val byKey = apps.associateBy { CaddyHomeCardSource.appKeyOf(it) }
        var index = 0
        CaddyHomeCardSource.build(context, apps).forEach { card ->
            val cardApps = card.appKeys.mapNotNull { byKey[it] }
            if (cardApps.isEmpty()) return@forEach
            grid.addView(
                buildCardCell(card.title, cardApps, isAddCategory = false),
                gridCellParams(index++),
            )
        }
        // Trailing card that opens the category editor, mirroring the drawer's "+ Add category".
        grid.addView(buildCardCell("", emptyList(), isAddCategory = true), gridCellParams(index))
        return grid
    }

    private fun buildCardCell(title: String, apps: List<AppInfo>, isAddCategory: Boolean): View {
        val cell = FrameLayout(context)
        val cardHeightPx = dp(CARD_HEIGHT_DP)
        val widthPx = resources.displayMetrics.widthPixels - dp(24f)
        val cellWidthPx = (widthPx - dp(12f) * (COLUMN_COUNT - 1)) / COLUMN_COUNT
        // A renderer handed no cell size draws no icons, so derive the slot from the fixed card
        // size here and pass it on.
        val geometry = CaddyHomeCardLayout.previewGeometry(
            widthPx = cellWidthPx,
            heightPx = cardHeightPx,
            columns = COLUMN_COUNT,
            requestedRows = ROWS_PER_CARD,
            sideInsetPx = dp(GRID_SIDE_INSET_DP),
            titleReservePx = dp(TITLE_RESERVE_DP),
        )
        CaddyCardRenderer.renderCategoryCard(
            host = cell,
            title = title,
            apps = apps,
            isAddCategory = isAddCategory,
            cardClick = {
                if (isAddCategory) {
                    CaddyCategoryEditor.show(context, allApps())
                } else {
                    CaddyCategoryDialog.show(context, title, apps)
                }
            },
            iconBinder = { icon, app ->
                icon.applyFromApplicationInfo(app)
                icon.contentDescription = app.title
                icon.setOnClickListener(ItemClickHandler.INSTANCE)
                icon.setOnLongClickListener(CaddyCardIconLongClick.LISTENER)
            },
            fitGridToHost = true,
            columns = COLUMN_COUNT,
            maxIcons = geometry.maxIcons,
            cellSizePx = geometry.cellPx,
            showTitle = true,
        )
        return cell
    }

    private fun allApps(): List<AppInfo> = (context as? Launcher)?.appsView?.appsStore?.apps?.toList().orEmpty()

    private fun gridCellParams(index: Int): GridLayout.LayoutParams {
        val params = GridLayout.LayoutParams()
        params.width = 0
        params.height = dp(CARD_HEIGHT_DP)
        params.columnSpec = GridLayout.spec(index % COLUMN_COUNT, 1f)
        params.setMargins(dp(6f), dp(6f), dp(6f), dp(6f))
        return params
    }

    override fun handleClose(animate: Boolean) {
        animate().cancel()
        animate().alpha(0f).setDuration(if (animate) CLOSE_DURATION_MS else 0L)
            .withEndAction { (parent as? ViewGroup)?.removeView(this) }
            .start()
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean = false

    override fun isOfType(type: Int): Boolean = type and TYPE_APP_LIBRARY != 0

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val COLUMN_COUNT = 2
        private const val ROWS_PER_CARD = 2
        private const val CARD_HEIGHT_DP = 168f
        private const val GRID_SIDE_INSET_DP = 36f
        private const val TITLE_RESERVE_DP = 72f
        private const val CLOSE_DURATION_MS = 150L
        private const val OPEN_DURATION_MS = 180L

        /** Whether the App Library is currently open. */
        @JvmStatic
        fun isOpen(launcher: Launcher): Boolean = AbstractFloatingView.getOpenView<CaddyAppLibraryPage>(
            launcher,
            AbstractFloatingView.TYPE_APP_LIBRARY,
        ) != null

        /** Shows the App Library, reusing the open page when one already exists. */
        @JvmStatic
        fun show(launcher: Launcher) {
            val existing = AbstractFloatingView.getOpenView<CaddyAppLibraryPage>(
                launcher,
                AbstractFloatingView.TYPE_APP_LIBRARY,
            )
            if (existing != null) {
                existing.bind(launcher)
                return
            }
            val page = CaddyAppLibraryPage(launcher)
            page.bind(launcher)
            page.alpha = 0f
            page.markOpen()
            launcher.dragLayer.addView(
                page,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            page.animate().alpha(1f).setDuration(OPEN_DURATION_MS).start()
        }

        /** Closes the App Library if it is open. */
        @JvmStatic
        fun close(launcher: Launcher) {
            AbstractFloatingView.closeOpenContainer(launcher, AbstractFloatingView.TYPE_APP_LIBRARY)
        }
    }
}
