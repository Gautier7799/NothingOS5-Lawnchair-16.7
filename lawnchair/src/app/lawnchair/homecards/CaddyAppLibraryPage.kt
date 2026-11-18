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
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.lawnchair.allapps.caddy.CaddyCardRenderer
import app.lawnchair.allapps.caddy.CaddyCategoryDialog
import app.lawnchair.allapps.caddy.CaddyMaterialYou
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

    /** Backdrop opacity, in percent, from the App Library settings. */
    private var backgroundOpacityPercent = 78

    /** Background style from the App Library settings: blurred, opaque or tinted. */
    private var backgroundStyle = PreferenceManager2.BACKGROUND_STYLE_BLURRED

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

    /** The live search text, kept so the card grid can be rebuilt without recreating the field. */
    private var query = ""

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
            applyContentInsets(insets)
            insets
        }
    }

    /**
     * The page backdrop, in one of three styles:
     * - `blurred`: the wallpaper, softly blurred, under a themed translucent scrim so the blur and
     *   the tint blend into one cohesive surface (the iOS look).
     * - `opaque`: a solid Material You surface, so nothing of the wallpaper shows through.
     * - `tinted`: the wallpaper at full strength with a light tint, no blur.
     *
     * The blur radius follows the icon-size setting, and the scrim alpha follows the background
     * opacity setting, so the two controls together cover from "barely visible" to "solid".
     */
    private fun applyBackground() {
        loadWallpaper()
        val canBlur = Utilities.ATLEAST_S
        val surface = surfaceColor()
        val scrimAlpha = Math.round(backgroundOpacityPercent / 100f * MAX_BACKDROP_ALPHA)
        wallpaper.alpha = 1f
        when {
            !canBlur || backgroundStyle == PreferenceManager2.BACKGROUND_STYLE_OPAQUE -> {
                wallpaper.setRenderEffect(null)
                scrim.setBackgroundColor(opaque(surface))
            }

            backgroundStyle == PreferenceManager2.BACKGROUND_STYLE_TINTED -> {
                wallpaper.setRenderEffect(null)
                scrim.setBackgroundColor(withAlpha(surface, Math.round(scrimAlpha * TINTED_SCRIM_FACTOR)))
            }

            else -> {
                // iOS-style frost: a strong blur with a saturated, themed scrim on top. The tint is
                // the page's own Material You surface colour, so the blur and the cards share one
                // palette instead of the blur reading as raw, unrelated wallpaper.
                val blur = RenderEffect.createBlurEffect(
                    backgroundBlurRadiusPx,
                    backgroundBlurRadiusPx,
                    Shader.TileMode.CLAMP,
                )
                val saturation = RenderEffect.createColorFilterEffect(
                    android.graphics.ColorMatrixColorFilter(saturationMatrix(BLUR_SATURATION)),
                )
                wallpaper.setRenderEffect(RenderEffect.createChainEffect(saturation, blur))
                scrim.setBackgroundColor(withAlpha(surface, scrimAlpha))
            }
        }
    }

    private fun opaque(color: Int): Int = Color.argb(255, Color.red(color), Color.green(color), Color.blue(color))

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    /** A colour matrix that scales saturation about mid-grey, so a blur keeps its hues alive. */
    private fun saturationMatrix(saturation: Float): android.graphics.ColorMatrix {
        val s = saturation
        val inv = 1f - s
        val r = 0.213f * inv
        val g = 0.715f * inv
        val b = 0.072f * inv
        return android.graphics.ColorMatrix(
            floatArrayOf(
                r + s, g, b, 0f, 0f,
                r, g + s, b, 0f, 0f,
                r, g, b + s, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }

    /**
     * The page's own surface colour: the framework Material You "surface" role when the platform
     * provides it, so the backdrop matches the system theme exactly. Falls back to a light/dark
     * neutral blended with the wallpaper accent when the role is unavailable.
     */
    private fun surfaceColor(): Int {
        val role = resources.getIdentifier("system_surface", "color", "android")
        if (role != 0) {
            runCatching { resources.getColor(role, context.theme) }.getOrNull()?.let { return it }
        }
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val base = if (night) 18 else 246
        val (accent1, accent2, accent3) = accentPalette()
        val weight = if (night) 0.26f else 0.20f
        val accentR = (Color.red(accent1) + Color.red(accent2) + Color.red(accent3)) / 3
        val accentG = (Color.green(accent1) + Color.green(accent2) + Color.green(accent3)) / 3
        val accentB = (Color.blue(accent1) + Color.blue(accent2) + Color.blue(accent3)) / 3
        return Color.rgb(
            Math.round(base * (1f - weight) + accentR * weight),
            Math.round(base * (1f - weight) + accentG * weight),
            Math.round(base * (1f - weight) + accentB * weight),
        )
    }

    /** The wallpaper's accent1/2/3 roles, falling back to the single themed accent. */
    private fun accentPalette(): Triple<Int, Int, Int> {
        val fallback = CaddyCardRenderer.resolveDynamicColor(context)
        fun role(name: String): Int {
            val id = resources.getIdentifier(name, "color", "android")
            if (id == 0) return fallback
            return runCatching { resources.getColor(id, context.theme) }.getOrDefault(fallback)
        }
        return Triple(role("system_accent1_600"), role("system_accent2_600"), role("system_accent3_600"))
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

    /**
     * Keeps the content clear of the system bars. The page itself fills the whole screen, so only
     * the content column is inset: the title is pushed below the status bar and the last row of
     * cards is lifted just above the navigation bar, leaving the page flush with the screen edges.
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
        column.setPadding(0, top, 0, bottom + dp(BOTTOM_CLEARANCE_DP))
    }

    fun bind(launcher: Launcher) {
        this.launcher = launcher
        readSettings()
        applyBackground()
        column.removeAllViews()
        column.addView(buildHeader())
        column.addView(buildSearch())
        column.addView(buildSuggestions(launcher))
        cardsView = buildCards(launcher)
        column.addView(cardsView)
    }

    /** An iOS-style rounded search field that filters the category cards as the user types. */
    private fun buildSearch(): View {
        val wrapper = FrameLayout(context)
        wrapper.setPadding(dp(20f), dp(10f), dp(20f), dp(2f))

        val field = EditText(context)
        field.hint = context.getString(R.string.caddy_app_library_search_hint)
        field.setSingleLine(true)
        field.imeOptions = EditorInfo.IME_ACTION_SEARCH
        field.setTextColor(CaddyMaterialYou.onSurface(context))
        field.setHintTextColor(CaddyMaterialYou.onSurfaceVariant(context))
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        field.setPadding(dp(42f), dp(11f), dp(16f), dp(11f))
        field.background = GradientDrawable().apply {
            cornerRadius = dp(22f).toFloat()
            setColor(withAlpha(CaddyMaterialYou.surfaceContainer(context), SEARCH_FIELD_ALPHA))
        }
        field.setText(query)
        field.setSelection(query.length)
        field.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    val next = s?.toString().orEmpty()
                    if (next == query) return
                    query = next
                    launcher?.let(::rebuildCards)
                }
            },
        )
        wrapper.addView(
            field,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val glyph = TextView(context)
        glyph.text = "\uD83D\uDD0D"
        glyph.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        val glyphLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.START or Gravity.CENTER_VERTICAL,
        )
        glyphLp.leftMargin = dp(16f)
        wrapper.addView(glyph, glyphLp)
        return wrapper
    }

    /**
     * Replaces only the card grid when the app list changes. Rebuilding the whole column on every
     * app-store snapshot made the header and suggestion row flicker during startup, so the update
     * path touches the cards alone.
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
        val scale = prefs.appLibraryIconScale.firstCached() / 100f
        backgroundBlurRadiusPx = dp(BLUR_BASE_DP * scale).toFloat()
        backgroundOpacityPercent = prefs.appLibraryBackgroundOpacity.firstCached().coerceIn(0, 100)
        backgroundStyle = prefs.appLibraryBackgroundStyle.firstCached()
        columnCount = prefs.appLibraryColumns.firstCached().coerceIn(1, 4)
        cardHeightDp = prefs.appLibraryCardHeight.firstCached().coerceIn(96, 280).toFloat()
        gridSpacingDp = prefs.appLibraryGridSpacing.firstCached().coerceIn(0, 32).toFloat()
        showTitles = prefs.appLibraryShowTitles.firstCached()
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
        cardsView = null
        super.onDetachedFromWindow()
    }

    /** Marks the page open. Split out so the companion can set the protected flag. */
    fun markOpen() {
        mIsOpen = true
    }

    private fun buildHeader(): View {
        val row = LinearLayout(context)
        row.orientation = VERTICAL
        row.setPadding(dp(24f), dp(20f), dp(24f), dp(2f))

        val title = TextView(context)
        title.text = context.getString(R.string.caddy_app_library_title)
        title.setTextColor(CaddyMaterialYou.onSurface(context))
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
        title.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        row.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val hint = TextView(context)
        hint.text = context.getString(R.string.caddy_app_library_swipe_hint)
        hint.setTextColor(CaddyMaterialYou.onSurfaceVariant(context))
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        val hintLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        hintLp.topMargin = dp(2f)
        row.addView(hint, hintLp)
        return row
    }

    private fun buildSuggestions(launcher: Launcher): View {
        val heading = TextView(context)
        heading.text = context.getString(R.string.caddy_app_library_suggestions)
        heading.setTextColor(CaddyMaterialYou.onSurface(context))
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        heading.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        heading.setPadding(dp(24f), dp(16f), dp(24f), dp(6f))

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
        grid.columnCount = columnCount
        grid.useDefaultMargins = false
        grid.setPadding(dp(18f), dp(10f), dp(18f), dp(24f))

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
            .filter { card ->
                CaddyAppLibraryFilter.matches(
                    query = query,
                    title = card.title,
                    appTitles = card.appKeys.mapNotNull { byKey[it]?.title?.toString() },
                )
            }
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

    /** The width of one card cell, used so every card is measured at the size it is laid out in. */
    private fun cardCellWidthPx(): Int = (resources.displayMetrics.widthPixels - dp(12f) - dp(gridSpacingDp) * (columnCount - 1)) / columnCount

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
        private const val DEFAULT_COLUMN_COUNT = 2
        private const val ROWS_PER_CARD = 2
        private const val DEFAULT_CARD_HEIGHT_DP = 168f
        private const val DEFAULT_CELL_GAP_DP = 12f
        private const val GRID_SIDE_INSET_DP = 36f
        private const val TITLE_RESERVE_DP = 52f
        private const val CLOSE_DURATION_MS = 150L
        private const val OPEN_DURATION_MS = 180L

        /**
         * Icon size relative to a cell at scale 1; the user's setting multiplies this. Kept near the
         * cell so the preview icons read as full-size, matching the iOS App Library look.
         */
        private const val ICON_FILL_RATIO_BASE = 0.92f

        /** The alpha the backdrop reaches at 100% opacity. */
        private const val MAX_BACKDROP_ALPHA = 255

        /** How much of the scrim alpha the tinted (non-blurred) style keeps. */
        private const val TINTED_SCRIM_FACTOR = 0.45f

        /** Saturation multiplier applied to the blurred wallpaper, so its hues stay alive. */
        private const val BLUR_SATURATION = 1.35f

        /** Alpha of the search field's fill, so it reads as a soft inset pill over the backdrop. */
        private const val SEARCH_FIELD_ALPHA = 200

        /** Clearance below the navigation bar for the last card row. */
        private const val BOTTOM_CLEARANCE_DP = 12f

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
