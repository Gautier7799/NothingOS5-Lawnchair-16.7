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

import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
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
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.PredictedContainerInfo
import com.android.launcher3.touch.ItemClickHandler
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors

/**
 * The full-screen Caddy App Library.
 *
 * This is the whole library as a single page rather than cards scattered across the workspace:
 * a title header, a suggestions row, the category cards the drawer uses, and a trailing
 * "+ Add category" card. It is opened by swiping past the last workspace page and covers the
 * screen until it is closed. Long-pressing a card deletes it.
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

    private var launcher: Launcher? = null

    /** Rebuilds the page when the app list changes, so new categories appear without reopening. */
    private val appsListener = AllAppsStore.OnUpdateListener { launcher?.let(::bind) }

    init {
        orientation = VERTICAL
        isClickable = true
        isFocusable = true
        val scroll = ScrollView(context)
        column.orientation = VERTICAL
        scroll.addView(column)
        addView(
            scroll,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        applyBackground()
    }

    /**
     * A translucent scrim tinted by the wallpaper's Monet accent, so the page reads as part of the
     * system surface instead of a fixed black slab. It is recomputed on bind because the accent can
     * change with the wallpaper.
     */
    private fun applyBackground() {
        val accent = CaddyCardRenderer.resolveDynamicColor(context)
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val base = if (night) 16 else 244
        val weight = 0.10f
        val r = Math.round(base * (1f - weight) + Color.red(accent) * weight)
        val g = Math.round(base * (1f - weight) + Color.green(accent) * weight)
        val b = Math.round(base * (1f - weight) + Color.blue(accent) * weight)
        setBackgroundColor(Color.argb(if (night) 246 else 240, r, g, b))
    }

    fun bind(launcher: Launcher) {
        this.launcher = launcher
        applyBackground()
        column.removeAllViews()
        column.addView(buildHeader())
        column.addView(buildSuggestions(launcher))
        column.addView(buildCards(launcher))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        launcher?.appsView?.appsStore?.addUpdateListener(appsListener)
    }

    override fun onDetachedFromWindow() {
        launcher?.appsView?.appsStore?.removeUpdateListener(appsListener)
        super.onDetachedFromWindow()
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
        val grid = GridLayout(context)
        grid.columnCount = COLUMN_COUNT
        grid.useDefaultMargins = false
        grid.setPadding(dp(6f), dp(8f), dp(6f), dp(32f))

        val apps = launcher.appsView?.appsStore?.apps?.toList().orEmpty()
        // Render nothing until the app list is loaded. Rendering a lone "+ Add category" card
        // first made it stretch across the page and then jump when the category cards arrived;
        // the app store listener rebuilds this view once the apps are ready.
        if (apps.isEmpty()) return grid

        val hidden = SharedPreferencesCaddyHomeCardHiddenStore(context).load()
        val byKey = apps.associateBy { CaddyHomeCardSource.appKeyOf(it) }
        var index = 0
        CaddyHomeCardSource.build(context, apps)
            .filterNot { it.id in hidden }
            .forEach { card ->
                val cardApps = card.appKeys.mapNotNull { byKey[it] }
                if (cardApps.isEmpty()) return@forEach
                grid.addView(
                    buildCardCell(launcher, card.id, card.title, cardApps, isAddCategory = false),
                    gridCellParams(index++),
                )
            }
        // Trailing card that opens the category editor, mirroring the drawer's "+ Add category".
        grid.addView(
            buildCardCell(launcher, ADD_CARD_ID, "", emptyList(), isAddCategory = true),
            gridCellParams(index),
        )
        return grid
    }

    private fun buildCardCell(
        launcher: Launcher,
        cardId: Int,
        title: String,
        apps: List<AppInfo>,
        isAddCategory: Boolean,
    ): View {
        val cell = FrameLayout(context)
        val cardHeightPx = dp(CARD_HEIGHT_DP)
        // A renderer handed no cell size draws no icons, so derive the slot from the fixed card
        // size here and pass it on.
        val geometry = CaddyHomeCardLayout.previewGeometry(
            widthPx = cardCellWidthPx(),
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
            cardLongClick = if (isAddCategory) {
                null
            } else {
                {
                    confirmDelete(launcher, cardId, title)
                    true
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

    /** Asks before deleting a card; the drawer's automatic categories cannot be rebuilt by hand. */
    private fun confirmDelete(launcher: Launcher, cardId: Int, title: String) {
        AlertDialog.Builder(context)
            .setTitle(R.string.caddy_category_delete_title)
            .setMessage(context.getString(R.string.caddy_category_delete_message, title))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.caddy_category_delete) { _, _ ->
                deleteCard(cardId, title)
                bind(launcher)
            }
            .show()
    }

    /**
     * Deletes a card. A user-created category is removed from the shared custom-category store, and
     * the card id is hidden in the same store the workspace uses, so the card does not return on
     * the next rebuild. Automatic categories are only hidden, since they are regenerated.
     */
    private fun deleteCard(cardId: Int, title: String) {
        SharedPreferencesCaddyHomeCardHiddenStore(context).hide(cardId)
        val prefs = context.getSharedPreferences(
            CaddyHomeCardSource.CUSTOM_CATEGORIES_PREFS,
            Context.MODE_PRIVATE,
        )
        val updated = CaddyCategoryStore.removeCategory(
            existing = prefs.getString(CaddyHomeCardSource.CUSTOM_CATEGORIES_KEY, null),
            title = title,
        )
        prefs.edit().putString(CaddyHomeCardSource.CUSTOM_CATEGORIES_KEY, updated).commit()
    }

    private fun allApps(): List<AppInfo> = launcher?.appsView?.appsStore?.apps?.toList().orEmpty()

    /** The width of one card cell, used so every card is measured at the size it is laid out in. */
    private fun cardCellWidthPx(): Int = (resources.displayMetrics.widthPixels - dp(12f) - dp(CELL_GAP_DP) * (COLUMN_COUNT - 1)) / COLUMN_COUNT

    private fun gridCellParams(index: Int): GridLayout.LayoutParams {
        val params = GridLayout.LayoutParams()
        // A fixed width keeps every card the same size. A weighted column lets a card stretch to
        // the full row while it is the only child, which is the oversized card seen before the
        // category cards load.
        params.width = cardCellWidthPx()
        params.height = dp(CARD_HEIGHT_DP)
        params.columnSpec = GridLayout.spec(index % COLUMN_COUNT)
        params.rowSpec = GridLayout.spec(index / COLUMN_COUNT)
        val gap = dp(CELL_GAP_DP) / 2
        params.setMargins(gap, dp(6f), gap, dp(6f))
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
        private const val CELL_GAP_DP = 12f
        private const val GRID_SIDE_INSET_DP = 36f
        private const val TITLE_RESERVE_DP = 72f
        private const val CLOSE_DURATION_MS = 150L
        private const val OPEN_DURATION_MS = 180L

        /** Id used for the trailing "+ Add category" card; it is not a real category. */
        private const val ADD_CARD_ID = Int.MIN_VALUE

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
