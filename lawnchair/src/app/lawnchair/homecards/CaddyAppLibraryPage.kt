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
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.lawnchair.allapps.caddy.CaddyCardRenderer
import app.lawnchair.allapps.caddy.CaddyCategoryDialog
import app.lawnchair.allapps.caddy.CaddyMaterialYou
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS_PREDICTION
import com.android.launcher3.R
import com.android.launcher3.Utilities
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

    /** The opaque Material You surface that fills the page behind the cards. */
    private val background = View(context)

    /** Card columns, from the App Library settings. */
    private var columnCount = DEFAULT_COLUMN_COUNT

    /** Card height and grid spacing, in dp, from the App Library settings. */
    private var cardHeightDp = DEFAULT_CARD_HEIGHT_DP
    private var gridSpacingDp = DEFAULT_CELL_GAP_DP

    /** Whether the card titles are shown, from the App Library settings. */
    private var showTitles = true

    private val rebind = Runnable {
        rebindPosted = false
        launcher?.let(::rebuildCards)
    }
    private var rebindPosted = false

    /** Batches the bursts of app-store updates the launcher emits during startup into one rebind. */
    private val appsListener = AllAppsStore.OnUpdateListener {
        if (rebindPosted) return@OnUpdateListener
        rebindPosted = true
        postDelayed(rebind, REBIND_DEBOUNCE_MS)
    }

    /** The current grid of category cards, replaced in place when the app list changes. */
    private var cardsView: View? = null

    private var downY = 0f
    private var dragY = 0f
    private var dragging = false

    /** Whether the current gesture started inside the system's edge-swipe band. */
    private var downInEdgeZone = false

    /** The scrolling content, kept as a field so drag-to-close can respect its scroll position. */
    private val scroll = ScrollView(context)

    init {
        orientation = VERTICAL
        isClickable = true
        isFocusable = true

        scroll.overScrollMode = OVER_SCROLL_NEVER
        scroll.clipToPadding = false
        column.orientation = VERTICAL
        scroll.addView(column)

        // The opaque surface and the scrolling content share one FrameLayout so the surface fills
        // the page behind the cards. A vertical LinearLayout would stack them instead of overlaying.
        val content = FrameLayout(context)
        content.addView(
            background,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        content.addView(
            scroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        addView(
            content,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        applyBackground()
        setOnApplyWindowInsetsListener { _, insets ->
            applyContentInsets(insets)
            insets
        }
    }

    /**
     * The page backdrop: a solid Material You surface. There is no blur and no wallpaper layer, so
     * the page matches the system theme exactly and never fights the launcher behind it.
     */
    private fun applyBackground() {
        background.setBackgroundColor(CaddyMaterialYou.surface(context))
    }

    /**
     * Keeps the content clear of the system bars without wasting screen space. The page surface
     * already fills the screen; only the card column is inset by the status bar at the top and the
     * navigation bar at the bottom, so the first row is never under the clock and the last row sits
     * just above the navigation bar with no empty gap below it.
     */
    private fun applyContentInsets(insets: WindowInsets) {
        val top: Int
        val bottom: Int
        if (Utilities.ATLEAST_R) {
            top = insets.getInsets(WindowInsets.Type.statusBars()).top
            bottom = insets.getInsets(
                WindowInsets.Type.navigationBars() or WindowInsets.Type.systemGestures(),
            ).bottom
        } else {
            @Suppress("DEPRECATION")
            top = insets.systemWindowInsetTop
            @Suppress("DEPRECATION")
            bottom = insets.systemWindowInsetBottom
        }
        column.setPadding(0, top, 0, bottom)
    }

    fun bind(launcher: Launcher) {
        this.launcher = launcher
        readSettings()
        applyBackground()
        column.removeAllViews()
        column.addView(buildSuggestions(launcher), fillWidthParams())
        cardsView = buildCards(launcher)
        column.addView(cardsView, fillWidthParams())
    }

    private fun fillWidthParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    /** Marks the page open. Split out so the companion can set the protected flag. */
    fun markOpen() {
        mIsOpen = true
    }

    private fun buildSuggestions(launcher: Launcher): View {
        val heading = TextView(context)
        heading.text = context.getString(R.string.caddy_app_library_suggestions)
        heading.setTextColor(CaddyMaterialYou.onSurface(context))
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        heading.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        heading.setPadding(dp(SIDE_PADDING_DP), dp(14f), dp(SIDE_PADDING_DP), dp(6f))

        val strip = LinearLayout(context)
        strip.orientation = HORIZONTAL
        strip.setPadding(dp(SIDE_PADDING_DP), 0, dp(SIDE_PADDING_DP), dp(4f))
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
        grid.columnCount = columnCount
        grid.useDefaultMargins = false
        // A small, symmetric outer margin keeps the outer cards off the screen edge. The bottom
        // padding is only half the spacing, because each cell already carries a spacing/2 bottom
        // margin; that makes the gap below the last row match the gaps between rows exactly.
        grid.setPadding(dp(SIDE_PADDING_DP), dp(SIDE_PADDING_DP), dp(SIDE_PADDING_DP), 0)

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
        val cardHeightPx = dp(cardHeightDp)
        // A renderer handed no cell size draws no icons, so derive the slot from the fixed card
        // size here and pass it on.
        val geometry = CaddyHomeCardLayout.previewGeometry(
            widthPx = cardCellWidthPx(),
            heightPx = cardHeightPx,
            columns = columnCount,
            requestedRows = ROWS_PER_CARD,
            sideInsetPx = dp(GRID_SIDE_INSET_DP),
            titleReservePx = dp(TITLE_RESERVE_DP),
        )
        val iconScale = PreferenceManager2.getInstance(context).appLibraryIconScale.firstCached()
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
            columns = columnCount,
            maxIcons = geometry.maxIcons,
            cellSizePx = geometry.cellPx,
            iconFillRatio = ICON_FILL_RATIO_BASE * iconScale,
            showTitle = showTitles || isAddCategory,
            titleAtBottom = true,
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

    /**
     * Replaces only the card grid when the app list changes. Rebuilding the whole column on every
     * app-store snapshot made the suggestion row flicker during startup, so the update path touches
     * the cards alone.
     */
    private fun rebuildCards(launcher: Launcher) {
        if (!isAttachedToWindow) return
        val fresh = buildCards(launcher)
        val old = cardsView
        if (old != null && old.parent === column) {
            column.removeView(old)
        }
        cardsView = fresh
        column.addView(fresh)
    }

    /** Reads the App Library settings into the cached fields. Called before every rebuild. */
    private fun readSettings() {
        val prefs = PreferenceManager2.getInstance(context)
        columnCount = prefs.appLibraryColumns.firstCached().coerceIn(1, 4)
        cardHeightDp = prefs.appLibraryCardHeight.firstCached().coerceIn(96, 280).toFloat()
        gridSpacingDp = prefs.appLibraryGridSpacing.firstCached().coerceIn(0, 32).toFloat()
        showTitles = prefs.appLibraryShowTitles.firstCached()
    }

    /** The width of one card cell, so every card is measured at the size it is laid out in. */
    private fun cardCellWidthPx(): Int = CaddyHomeCardLayout.cardWidthPx(
        screenWidthPx = resources.displayMetrics.widthPixels,
        sidePaddingPx = dp(SIDE_PADDING_DP),
        spacingPx = dp(gridSpacingDp),
        columns = columnCount,
    )

    private fun gridCellParams(index: Int): GridLayout.LayoutParams {
        val params = GridLayout.LayoutParams()
        // A fixed width keeps every card the same size. A weighted column lets a card stretch to
        // the full row while it is the only child, which is the oversized card seen before the
        // category cards load.
        params.width = cardCellWidthPx()
        params.height = dp(cardHeightDp)
        params.columnSpec = GridLayout.spec(index % columnCount)
        params.rowSpec = GridLayout.spec(index / columnCount)
        val gap = dp(gridSpacingDp) / 2
        params.setMargins(gap, gap, gap, gap)
        return params
    }

    override fun handleClose(animate: Boolean) {
        animate().cancel()
        animate().alpha(0f).setDuration(if (animate) CLOSE_DURATION_MS else 0L)
            .withEndAction { (parent as? ViewGroup)?.removeView(this) }
            .start()
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean = false

    /**
     * Starts the drag-to-close gesture, but only from the top of the content, only for a clearly
     * vertical downward drag, and never from the system's edge-swipe zone. Staying out of the edge
     * zone is what keeps the page's own gesture from fighting the system swipe-to-go-back gesture.
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = ev.y
                dragY = 0f
                dragging = false
                downInEdgeZone = isInSystemGestureZone(ev.x)
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = ev.y - downY
                if (!downInEdgeZone && !dragging && !scroll.canScrollVertically(-1) &&
                    dy > touchSlop * 2
                ) {
                    dragging = true
                    dragY = dy
                    translationY = dy
                    return true
                }
            }
        }
        return false
    }

    /**
     * Closes the page on a downward swipe, with the page following the finger. Touches that do not
     * start a drag are not consumed, so taps on the cards and the system's edge-swipe gesture keep
     * working.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.y
                dragY = 0f
                dragging = false
                downInEdgeZone = isInSystemGestureZone(event.x)
                // Only claim the gesture stream when a drag may begin here.
                return !downInEdgeZone
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - downY
                if (!downInEdgeZone && !dragging && dy > touchSlop) dragging = true
                if (dragging) {
                    dragY = dy.coerceAtLeast(0f)
                    translationY = dragY
                }
                return dragging
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    if (dragY > height * DISMISS_FRACTION || event.y - downY > dp(180f)) {
                        dismissByDrag()
                    } else {
                        animate().translationY(0f).setDuration(120L).start()
                    }
                }
                dragging = false
                return true
            }
        }
        return dragging
    }

    /** True when [x] falls in the left or right system gesture band used for swipe-to-go-back. */
    private fun isInSystemGestureZone(x: Float): Boolean {
        val band = systemGestureBandPx()
        return x <= band || x >= width - band
    }

    private fun systemGestureBandPx(): Int = dp(GESTURE_BAND_FALLBACK_DP)

    /** Handles the system back gesture by closing the page, so back and drag-to-close agree. */
    override fun onBackInvoked() {
        close(true)
    }

    private fun dismissByDrag() {
        animate().translationY(height.toFloat()).alpha(0f).setDuration(CLOSE_DURATION_MS)
            .withEndAction { (parent as? ViewGroup)?.removeView(this) }
            .start()
    }

    override fun isOfType(type: Int): Boolean = type and TYPE_APP_LIBRARY != 0

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private val touchSlop: Int
        get() = ViewConfiguration.get(context).scaledTouchSlop

    companion object {
        private const val DEFAULT_COLUMN_COUNT = 2
        private const val ROWS_PER_CARD = 2
        private const val DEFAULT_CARD_HEIGHT_DP = 168f
        private const val DEFAULT_CELL_GAP_DP = 12f
        private const val GRID_SIDE_INSET_DP = 36f
        private const val TITLE_RESERVE_DP = 52f
        private const val CLOSE_DURATION_MS = 150L
        private const val OPEN_DURATION_MS = 180L

        /** Symmetric outer margin of the card grid, so the outer cards never touch the screen edge. */
        private const val SIDE_PADDING_DP = 12f

        /** Fallback width, in dp, of the system's left/right edge-swipe band. */
        private const val GESTURE_BAND_FALLBACK_DP = 20f

        /**
         * Icon size relative to a cell at scale 1; the user's setting multiplies this. Kept near the
         * cell so the preview icons read as full-size, matching the iOS App Library look.
         */
        private const val ICON_FILL_RATIO_BASE = 0.92f

        /** How much of the page height a downward drag must cover to dismiss the page. */
        private const val DISMISS_FRACTION = 0.22f

        /** Coalescing window for the app-store update bursts the launcher emits during startup. */
        private const val REBIND_DEBOUNCE_MS = 120L

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
