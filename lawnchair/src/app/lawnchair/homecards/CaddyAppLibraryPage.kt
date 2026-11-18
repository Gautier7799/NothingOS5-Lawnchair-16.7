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
import android.graphics.RenderEffect
import android.graphics.Shader
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.lawnchair.allapps.caddy.CaddyCardRenderer
import app.lawnchair.allapps.caddy.CaddyCategoryDialog
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.wallpaper.WallpaperManagerCompat
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

    /** The wallpaper layer drawn behind the cards, blurred and tinted with the Monet accent. */
    private val wallpaper = ImageView(context)

    /** A translucent Monet tint over the blurred wallpaper, so the page still reads as a surface. */
    private val scrim = View(context)

    /** Blur strength in px, scaled from the user's icon-size setting. */
    private var backgroundBlurRadiusPx = 0f

    private val rebind = Runnable {
        rebindPosted = false
        launcher?.let(::bind)
    }
    private var rebindPosted = false

    /** Batches the bursts of app-store updates the launcher emits during startup into one rebind. */
    private val appsListener = AllAppsStore.OnUpdateListener {
        if (rebindPosted) return@OnUpdateListener
        rebindPosted = true
        postDelayed(rebind, REBIND_DEBOUNCE_MS)
    }

    private var downY = 0f
    private var dragY = 0f
    private var dragging = false

    /** The scrolling content, kept as a field so drag-to-close can respect its scroll position. */
    private val scroll = ScrollView(context)

    init {
        orientation = VERTICAL
        isClickable = true
        isFocusable = true

        wallpaper.scaleType = ImageView.ScaleType.CENTER_CROP
        scroll.overScrollMode = OVER_SCROLL_NEVER
        column.orientation = VERTICAL
        scroll.addView(column)

        // The wallpaper and the scrolling content share one FrameLayout so the wallpaper sits
        // behind the cards. A vertical LinearLayout would stack them instead of overlaying them.
        val content = FrameLayout(context)
        content.addView(
            wallpaper,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        content.addView(
            scrim,
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
            applyBottomInset(insets)
            insets
        }
    }

    /**
     * The blurred wallpaper, tinted with the wallpaper's Monet accent and dimmed, so the page
     * reacts to the wallpaper instead of using a fixed colour. The blur radius follows the icon
     * size the user picks, so the same control tunes both.
     */
    private fun applyBackground() {
        loadWallpaper()
        if (!Utilities.ATLEAST_S) {
            // No blur available: a stronger Monet scrim keeps the cards readable over the wallpaper.
            wallpaper.alpha = 1f
            scrim.setBackgroundColor(tintedScrim())
            return
        }
        val accent = CaddyCardRenderer.resolveDynamicColor(context)
        val blur = RenderEffect.createBlurEffect(
            backgroundBlurRadiusPx,
            backgroundBlurRadiusPx,
            Shader.TileMode.CLAMP,
        )
        // Dim the wallpaper so the cards and text stay readable on top of it.
        val dim = RenderEffect.createColorFilterEffect(
            android.graphics.ColorMatrixColorFilter(
                android.graphics.ColorMatrix().apply { setScale(0.62f, 0.62f, 0.62f, 1f) },
            ),
        )
        wallpaper.alpha = 1f
        wallpaper.setRenderEffect(RenderEffect.createChainEffect(dim, blur))
        // The accent tint sits above the wallpaper and below the cards, so the page follows Monet
        // while the wallpaper stays visible through it.
        scrim.setBackgroundColor(
            Color.argb(120, Color.red(accent), Color.green(accent), Color.blue(accent)),
        )
    }

    /** A translucent Monet-tinted scrim, used on devices without a wallpaper blur effect. */
    private fun tintedScrim(): Int {
        val accent = CaddyCardRenderer.resolveDynamicColor(context)
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val base = if (night) 16 else 244
        val weight = 0.10f
        return Color.argb(
            if (night) 246 else 240,
            Math.round(base * (1f - weight) + Color.red(accent) * weight),
            Math.round(base * (1f - weight) + Color.green(accent) * weight),
            Math.round(base * (1f - weight) + Color.blue(accent) * weight),
        )
    }

    private var wallpaperLoaded = false

    private fun loadWallpaper() {
        if (wallpaperLoaded) return
        wallpaperLoaded = true
        val manager = WallpaperManagerCompat.INSTANCE.get(context)
        Executors.THREAD_POOL_EXECUTOR.execute {
            val drawable = runCatching { manager.wallpaperManager.drawable }.getOrNull()
            if (drawable == null) return@execute
            Executors.MAIN_EXECUTOR.execute {
                wallpaper.setImageDrawable(drawable)
            }
        }
    }

    /** Keeps the content above the navigation bar so the last row is never cut off. */
    private fun applyBottomInset(insets: WindowInsets) {
        val bottom = if (Utilities.ATLEAST_R) {
            insets.getInsets(WindowInsets.Type.navigationBars() or WindowInsets.Type.systemGestures()).bottom
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetBottom
        }
        val base = dp(32f)
        column.setPadding(0, 0, 0, Math.max(base, bottom + dp(12f)))
    }

    fun bind(launcher: Launcher) {
        this.launcher = launcher
        val prefs = PreferenceManager2.getInstance(context)
        val scale = prefs.appLibraryIconScale.firstCached() / 100f
        backgroundBlurRadiusPx = dp(BLUR_BASE_DP * scale).toFloat()
        applyBackground()
        column.removeAllViews()
        column.addView(buildHeader())
        column.addView(buildSuggestions(launcher))
        column.addView(buildCards(launcher))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        launcher?.appsView?.appsStore?.addUpdateListener(appsListener)
        requestApplyInsets()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(rebind)
        rebindPosted = false
        launcher?.appsView?.appsStore?.removeUpdateListener(appsListener)
        wallpaper.setRenderEffect(null)
        super.onDetachedFromWindow()
    }

    /** Marks the page open. Split out so the companion can set the protected flag. */
    fun markOpen() {
        mIsOpen = true
    }

    private fun buildHeader(): View {
        val row = LinearLayout(context)
        row.orientation = VERTICAL
        row.setPadding(dp(20f), dp(18f), dp(20f), dp(4f))

        val title = TextView(context)
        title.text = context.getString(R.string.caddy_app_library_title)
        title.setTextColor(Color.WHITE)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        title.typeface = Typeface.DEFAULT_BOLD
        row.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val hint = TextView(context)
        hint.text = context.getString(R.string.caddy_app_library_swipe_hint)
        hint.setTextColor(Color.argb(170, 255, 255, 255))
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        row.addView(
            hint,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
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
            columns = COLUMN_COUNT,
            maxIcons = geometry.maxIcons,
            cellSizePx = geometry.cellPx,
            iconFillRatio = ICON_FILL_RATIO_BASE * iconScale,
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

    /**
     * Starts the drag-to-close gesture from anywhere on the page, but only from the top of the
     * content and only for a clearly downward drag, so vertical scrolling of the cards is untouched.
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = ev.y
                dragY = 0f
                dragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = ev.y - downY
                if (!dragging && !scroll.canScrollVertically(-1) && dy > touchSlop * 2) {
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
     * Closes the page on a downward swipe, with the page following the finger. The close button was
     * removed in favour of this single gesture.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.y
                dragY = 0f
                dragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - downY
                if (!dragging && dy > touchSlop) dragging = true
                if (dragging) {
                    dragY = dy.coerceAtLeast(0f)
                    translationY = dragY
                }
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
            }
        }
        return true
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
        private const val COLUMN_COUNT = 2
        private const val ROWS_PER_CARD = 2
        private const val CARD_HEIGHT_DP = 168f
        private const val CELL_GAP_DP = 12f
        private const val GRID_SIDE_INSET_DP = 36f
        private const val TITLE_RESERVE_DP = 72f
        private const val CLOSE_DURATION_MS = 150L
        private const val OPEN_DURATION_MS = 180L

        /** Icon size relative to a cell at scale 1; the user's setting multiplies this. */
        private const val ICON_FILL_RATIO_BASE = 0.6f

        /** How much of the page height a downward drag must cover to dismiss the page. */
        private const val DISMISS_FRACTION = 0.22f

        /** Blur radius at icon scale 1.0, in dp. Larger icons read as a softer, deeper surface. */
        private const val BLUR_BASE_DP = 26f

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
