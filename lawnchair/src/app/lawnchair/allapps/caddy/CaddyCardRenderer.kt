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

package app.lawnchair.allapps.caddy

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.lawnchair.util.resolveFolderPreviewColor
import com.android.launcher3.BubbleTextView
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.model.data.AppInfo

/**
 * Draws a Caddy category card.
 *
 * This is the single implementation of the card's appearance, shared by the app
 * drawer and the home screen so both render exactly the same thing. It is a direct
 * extraction of the drawer's previous `bindIndependentCaddyCard` / `addCaddyAppIcon`
 * / `resolveCaddyDynamicColor`, moved here verbatim so there is one source of truth.
 *
 * The caller supplies [iconBinder] to attach click/long-click/focus behaviour, because
 * the drawer and the home screen route those events differently. Everything visual is
 * owned by this object.
 */
object CaddyCardRenderer {

    /** Fills [host] with the card surface, title and up to [maxIcons] app icons. */
    @JvmOverloads
    fun renderCategoryCard(
        host: FrameLayout,
        title: String?,
        apps: List<AppInfo>,
        isAddCategory: Boolean = false,
        cardClick: (() -> Unit)? = null,
        cardLongClick: (() -> Boolean)? = null,
        iconBinder: (BubbleTextView, AppInfo) -> Unit,
        fitGridToHost: Boolean = false,
        columns: Int = 2,
        maxIcons: Int = MAX_PREVIEW_ICONS,
        cellSizePx: Int = 0,
        showTitle: Boolean = true,
    ) {
        val context = host.context
        host.removeAllViews()
        host.clipToPadding = false
        host.clipChildren = false
        // Small gutter so neighboring cards stay visually distinct without wasting
        // the workspace cell on empty margin.
        host.setPadding(dp(context, 2f), dp(context, 2f), dp(context, 2f), dp(context, 2f))

        // The drawer follows the compact iOS App Library composition: a soft
        // translucent icon tile with its category label beneath it. Workspace
        // cards keep their existing renderer and sizing.
        if (!fitGridToHost) {
            renderDrawerCategoryCard(
                host, title, apps, isAddCategory, cardClick, cardLongClick,
                iconBinder, columns, maxIcons,
            )
            return
        }

        val densityPadding = dp(context, 14f)
        val themeColor = resolveDynamicColor(context)
        // Translucent Nothing-style glass, tinted by Lawnchair's existing folder /
        // preview color so the user's selected and dynamic colors remain visible.
        val glassRed = Math.round(Color.red(themeColor) * 0.30f + 28 * 0.70f)
        val glassGreen = Math.round(Color.green(themeColor) * 0.30f + 31 * 0.70f)
        val glassBlue = Math.round(Color.blue(themeColor) * 0.30f + 38 * 0.70f)
        val topGlass = Color.argb(184, glassRed, glassGreen, glassBlue)
        val bottomGlass = Color.argb(
            148,
            Math.max(12, glassRed - 10),
            Math.max(14, glassGreen - 10),
            Math.max(18, glassBlue - 10),
        )
        val foreground = Color.WHITE
        val accentStroke = Color.argb(
            116,
            Color.red(themeColor),
            Color.green(themeColor),
            Color.blue(themeColor),
        )

        val card = LinearLayout(context)
        card.orientation = LinearLayout.VERTICAL
        // The title is the first child and must always stay inside the card. With a
        // vertically centred column, any content taller than the card is split evenly
        // above and below, which pushed the title out of the card and clipped it.
        // Anchor the column to the top so the title keeps its own row and the icon
        // grid only takes what is left.
        card.gravity = Gravity.TOP
        card.setPadding(
            densityPadding,
            dp(context, if (fitGridToHost) 8f else 12f),
            densityPadding,
            dp(context, if (fitGridToHost) 8f else 12f),
        )
        card.isClickable = true
        card.isFocusable = true
        card.setOnClickListener { cardClick?.invoke() }
        // The card surface owns the long press so a card drag starts even when the press
        // lands on one of the preview icons, which are themselves long-press targets.
        card.setOnLongClickListener { cardLongClick?.invoke() ?: false }
        val background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(topGlass, bottomGlass),
        )
        background.cornerRadius = dp(context, 28f).toFloat()
        background.setStroke(dp(context, 1f), accentStroke)
        card.background = background
        // Drawer previews stay clipped to their rounded outline. Workspace previews use
        // native-size BubbleTextView children scaled into smaller cells; clipping those
        // transformed children to the rounded outline crops adaptive-icon edges. Keep the
        // rounded background, but do not use it as a content clip on workspace cards.
        card.clipToOutline = !fitGridToHost
        card.elevation = dp(context, 1f).toFloat()
        host.addView(
            card,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        if (showTitle || isAddCategory) {
            val titleView = TextView(context)
            titleView.text = if (isAddCategory) "+  Add category" else title
            titleView.setTextColor(foreground)
            titleView.textSize = 14f
            titleView.typeface = Typeface.DEFAULT_BOLD
            titleView.maxLines = 1
            titleView.ellipsize = TextUtils.TruncateAt.END
            card.addView(
                titleView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        if (isAddCategory) {
            val addHint = TextView(context)
            addHint.text = "＋"
            addHint.setTextColor(foreground)
            addHint.textSize = 38f
            addHint.gravity = Gravity.CENTER
            card.addView(
                addHint,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                ),
            )
            host.contentDescription = "Add a custom app category"
            return
        }

        val grid = GridLayout(context)
        grid.columnCount = columns
        grid.rowCount = if (maxIcons <= 0) 2 else (maxIcons + columns - 1) / columns
        grid.useDefaultMargins = false
        // Icons are laid out at their native size and scaled down, so their view bounds may
        // exceed the cell; never let the grid or the card crop them.
        grid.clipChildren = false
        grid.clipToPadding = false
        val explicitCell = fitGridToHost && cellSizePx > 0
        // Workspace cards: the title owns its own row and the grid takes only the
        // space that is left (weighted), so it can never push the title out of the
        // card. This also lets the card render immediately on boot instead of
        // waiting for a measured cell size (which left the card blank until the
        // first layout pass).
        val gridHeight = when {
            fitGridToHost -> 0
            else -> dp(context, 128f)
        }
        val gridLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            gridHeight,
            if (fitGridToHost) 1f else 0f,
        )
        gridLp.topMargin = dp(context, if (fitGridToHost) 2f else 8f)
        card.addView(grid, gridLp)

        // Keep the category preview visually balanced: equal icon slots in a
        // consistent grid. Remaining apps are available after opening.
        for (index in 0 until minOf(apps.size, maxIcons)) {
            val app = apps[index]
            val cell = FrameLayout(context)
            val cellLp = GridLayout.LayoutParams()
            cellLp.width = 0
            // Workspace rows fill the grid's real (remaining) height equally, so a card
            // can never overflow its own bounds no matter the span. The drawer keeps its
            // fixed preview height.
            cellLp.height = when {
                fitGridToHost -> 0
                else -> dp(context, 64f)
            }
            cellLp.columnSpec = GridLayout.spec(index % columns, 1f)
            cellLp.rowSpec = if (fitGridToHost) {
                GridLayout.spec(index / columns, 1f)
            } else {
                GridLayout.spec(index / columns)
            }
            grid.addView(cell, cellLp)
            // Keep the icon strictly inside its cell so it can never spill into the
            // neighbouring row. For workspace cards the final size is set from the real
            // measured cell in addCaddyAppIcon's layout listener.
            val iconSize = if (explicitCell) {
                (cellSizePx * ICON_FILL_RATIO).toInt()
            } else {
                dp(context, if (fitGridToHost) 54f else 56f)
            }
            addCaddyAppIcon(context, cell, app, iconSize, iconBinder, fitGridToHost)
        }
        host.contentDescription = title
    }

    private fun renderDrawerCategoryCard(
        host: FrameLayout,
        title: String?,
        apps: List<AppInfo>,
        isAddCategory: Boolean,
        cardClick: (() -> Unit)?,
        cardLongClick: (() -> Boolean)?,
        iconBinder: (BubbleTextView, AppInfo) -> Unit,
        columns: Int,
        maxIcons: Int,
    ) {
        val context = host.context
        val themeColor = resolveDynamicColor(context)

        // iOS App Library-inspired translucent category tile. Android's ordinary
        // drawable background cannot blur the wallpaper behind it, so use a
        // genuinely translucent Monet-tinted surface and a fine outline instead
        // of pretending that a solid opaque fill is backdrop blur.
        val red = Math.round(Color.red(themeColor) * 0.18f + 232f * 0.82f)
        val green = Math.round(Color.green(themeColor) * 0.18f + 236f * 0.82f)
        val blue = Math.round(Color.blue(themeColor) * 0.18f + 244f * 0.82f)
        val card = FrameLayout(context)
        card.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.argb(154, red, green, blue),
                Color.argb(128, Math.max(0, red - 12), Math.max(0, green - 12), Math.max(0, blue - 12)),
            ),
        ).apply {
            cornerRadius = dp(context, 22f).toFloat()
            setStroke(dp(context, 1f), Color.argb(92, 255, 255, 255))
        }
        card.clipToOutline = true
        card.isClickable = true
        card.isFocusable = true
        card.setOnClickListener { cardClick?.invoke() }
        card.setOnLongClickListener { cardLongClick?.invoke() ?: false }
        card.elevation = dp(context, 1f).toFloat()
        host.addView(
            card,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 168f),
                Gravity.TOP,
            ),
        )

        if (isAddCategory) {
            val plus = TextView(context).apply {
                text = "+"
                setTextColor(Color.rgb(35, 42, 54))
                textSize = 42f
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setOnClickListener { cardClick?.invoke() }
                setOnLongClickListener { cardLongClick?.invoke() ?: false }
            }
            card.addView(
                plus,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        } else {
            val visibleApps = apps.take(maxOf(4, maxIcons) + 4)
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(context, 7f), dp(context, 7f), dp(context, 7f), dp(context, 5f))
                clipChildren = false
                clipToPadding = false
            }
            card.addView(
                content,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )

            // Four featured native launcher icons form the main 2x2 grid.
            val grid = GridLayout(context).apply {
                columnCount = 2
                rowCount = 2
                useDefaultMargins = false
                clipChildren = false
                clipToPadding = false
            }
            content.addView(
                grid,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    if (visibleApps.size > 4) 1.0f else 1.0f,
                ),
            )
            for (index in 0 until minOf(4, visibleApps.size)) {
                val cell = FrameLayout(context).apply {
                    clipChildren = false
                    clipToPadding = false
                }
                grid.addView(
                    cell,
                    GridLayout.LayoutParams().apply {
                        width = 0
                        height = 0
                        columnSpec = GridLayout.spec(index % 2, 1f)
                        rowSpec = GridLayout.spec(index / 2, 1f)
                    },
                )
                addCaddyAppIcon(
                    context,
                    cell,
                    visibleApps[index],
                    dp(context, 46f),
                    iconBinder,
                    fitGridToHost = false,
                )
            }

            // Additional category members remain visible as a compact native-icon
            // strip, matching the small secondary icons used by App Library groups.
            val extraApps = visibleApps.drop(4)
            if (extraApps.isNotEmpty()) {
                val extras = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    clipChildren = false
                    clipToPadding = false
                }
                content.addView(
                    extras,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(context, 34f),
                    ),
                )
                extraApps.forEach { app ->
                    val cell = FrameLayout(context).apply {
                        clipChildren = false
                        clipToPadding = false
                    }
                    extras.addView(
                        cell,
                        LinearLayout.LayoutParams(dp(context, 34f), dp(context, 32f)),
                    )
                    addCaddyAppIcon(
                        context,
                        cell,
                        app,
                        dp(context, 24f),
                        iconBinder,
                        fitGridToHost = false,
                    )
                }
            }
        }

        val label = TextView(context).apply {
            text = if (isAddCategory) "Add category" else title
            setTextColor(Color.rgb(28, 33, 42))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 4f), 0, dp(context, 4f), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { cardClick?.invoke() }
            setOnLongClickListener { cardLongClick?.invoke() ?: false }
        }
        host.addView(
            label,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 28f),
                Gravity.BOTTOM,
            ),
        )
        host.contentDescription = title ?: if (isAddCategory) "Add category" else null
    }

    private fun addCaddyAppIcon(
        context: Context,
        cell: FrameLayout,
        app: AppInfo,
        iconSize: Int,
        iconBinder: (BubbleTextView, AppInfo) -> Unit,
        fitGridToHost: Boolean,
    ) {
        // Use the same launcher icon widget as the default drawer. Its tag,
        // popup anchor, and long-press lifecycle are understood by Lawnchair's
        // native app action/drag handlers.
        val icon = LayoutInflater.from(context)
            .inflate(R.layout.all_apps_icon, cell, false) as BubbleTextView
        icon.applyFromApplicationInfo(app)
        icon.setTextVisibility(false)
        // Home cards are icon-only. Removing the invisible label lets TextView's
        // CENTER gravity center the actual drawable in the available cell.
        if (fitGridToHost) icon.text = ""
        icon.setLongPressTimeoutFactor(1f)
        icon.contentDescription = app.title
        iconBinder(icon, app)
        // all_apps_icon.xml enables centerVertically, which reserves a text line
        // even when setTextVisibility(false) only makes that text transparent.
        // In Caddy's compact preview cell that produces negative top padding and
        // clips adaptive icons into a flat-bottom shape. Disable that mode and
        // center only the actual drawable using BubbleTextView's native icon size.
        icon.setCenterVertically(false)
        // A workspace card's preview row is dynamically measured. Give its
        // BubbleTextView a square, centered viewport so its drawable cannot be
        // laid out against a full-height cell and then clipped by the card edge.
        // The drawer retains its original full-cell layout.
        if (fitGridToHost) {
            icon.setPadding(0, 0, 0, 0)
        } else {
            // Drawer preview cells have a fixed square viewport. Override the
            // launcher-wide icon size for this compact grid; otherwise each
            // BubbleTextView keeps the full drawer icon bounds and overlaps its
            // neighboring cell. The supplied size is the actual drawable size.
            icon.setIconSizeOverride(iconSize)
            icon.setPadding(0, 0, 0, 0)
        }
        icon.gravity = Gravity.CENTER
        // Laying the icon out at a smaller view size clips the drawable, because
        // BubbleTextView sizes its compound drawable from the device profile at inflation.
        // Instead change the drawable's real bounds to fit the measured cell: this measures
        // and draws the icon completely inside its viewport without cropping adaptive edges.
        val iconParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER,
        )
        cell.clipChildren = false
        cell.clipToPadding = false
        icon.clipToOutline = false
        cell.addView(icon, iconParams)
        if (fitGridToHost) {
            cell.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                val available = (minOf(view.width, view.height) - dp(context, 8f))
                    .coerceAtLeast(dp(context, 16f))
                icon.setIconSizeOverride(available)
                icon.scaleX = 1f
                icon.scaleY = 1f
            }
        }
    }

    /**
     * Resolve Android's themed accent so Caddy follows the system Monet palette
     * (including wallpaper-based colors on Android 12+). Keep Lawnchair's folder
     * tint as a compatibility fallback for themes that do not expose colorAccent.
     */
    fun resolveDynamicColor(context: Context): Int {
        // Prefer Android's Monet palette directly. On Pixel / Android 12+ these
        // framework colors are supplied by the active wallpaper or system theme.
        // Use the themed accent next so custom ROMs and app themes remain supported.
        val resources: Resources = context.resources
        val systemPaletteColors = arrayOf(
            "system_accent1_600",
            "system_accent2_600",
            "system_accent3_600",
        )
        for (colorName in systemPaletteColors) {
            val colorId = resources.getIdentifier(colorName, "color", "android")
            if (colorId != 0) {
                try {
                    return resources.getColor(colorId, context.theme)
                } catch (ignored: RuntimeException) {
                    // Try the next system palette entry or themed accent.
                }
            }
        }

        val value = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.colorAccent, value, true)) {
            if (value.resourceId != 0) {
                try {
                    return resources.getColor(value.resourceId, context.theme)
                } catch (ignored: RuntimeException) {
                    // Fall through to the resolved inline color or legacy fallback.
                }
            }
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
                value.type <= TypedValue.TYPE_LAST_COLOR_INT
            ) {
                return value.data
            }
        }
        return resolveFolderPreviewColor(context)
    }

    private fun dp(context: Context, value: Float): Int = Utilities.dpToPx(value, context)

    private const val MAX_PREVIEW_ICONS = 4
    private const val ICON_FILL_RATIO = 0.82f
}
