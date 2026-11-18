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
import android.content.res.Configuration
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
        title: String,
        apps: List<AppInfo>,
        isAddCategory: Boolean = false,
        cardClick: (() -> Unit)? = null,
        cardLongClick: (() -> Boolean)? = null,
        iconBinder: (BubbleTextView, AppInfo) -> Unit,
        fitGridToHost: Boolean = false,
        columns: Int = 2,
        maxIcons: Int = MAX_PREVIEW_ICONS,
        cellSizePx: Int = 0,
        iconFillRatio: Float = ICON_FILL_RATIO,
        showTitle: Boolean = true,
        titleAtBottom: Boolean = false,
    ) {
        val context = host.context
        host.removeAllViews()
        host.clipToPadding = false
        host.clipChildren = false
        // Add a small gutter between neighboring Caddy cards without changing
        // the RecyclerView's grid sizing or the normal app drawer.
        host.setPadding(dp(context, 4f), dp(context, 4f), dp(context, 4f), dp(context, 4f))

        val densityPadding = dp(context, 14f)
        // A solid Material You tonal surface: the same "surface container high" role the system
        // uses for sheets and cards, so Caddy reads as part of the theme rather than a glassy
        // overlay. The accent outline and the "+ Add" tint keep it from looking flat.
        val surface = CaddyMaterialYou.surfaceContainer(context)
        val accent = CaddyMaterialYou.accent(context)
        val foreground = CaddyMaterialYou.onSurface(context)
        val outline = CaddyMaterialYou.outlineVariant(context)
        // Standard cards get a hairline outline; the "+ Add" card gets an accent outline so it
        // reads as an action rather than another category.
        val strokeColor = if (isAddCategory) {
            Color.argb(200, Color.red(accent), Color.green(accent), Color.blue(accent))
        } else {
            Color.argb(90, Color.red(outline), Color.green(outline), Color.blue(outline))
        }
        // The "+ Add" card leans toward the accent so it reads as an action, not a category.
        val cardColor = if (isAddCategory) blend(surface, accent, 0.18f) else surface

        val card = LinearLayout(context)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = if (fitGridToHost) Gravity.TOP else Gravity.CENTER_VERTICAL
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
            intArrayOf(cardColor, cardColor),
        )
        background.cornerRadius = dp(context, if (fitGridToHost) 30f else 28f).toFloat()
        background.setStroke(dp(context, if (fitGridToHost) 1f else 1f), strokeColor)
        card.background = background
        // Drawer previews stay clipped to their rounded outline. Workspace previews use
        // native-size BubbleTextView children scaled into smaller cells; clipping those
        // transformed children to the rounded outline crops adaptive-icon edges. Keep the
        // rounded background, but do not use it as a content clip on workspace cards.
        card.clipToOutline = !fitGridToHost
        card.elevation = dp(context, if (fitGridToHost) 3f else 1f).toFloat()
        host.addView(
            card,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        val makeTitle = {
            val titleView = TextView(context)
            titleView.text = if (isAddCategory) "+  Add category" else title
            titleView.setTextColor(foreground)
            titleView.textSize = if (fitGridToHost) 17f else 14f
            titleView.typeface = Typeface.DEFAULT_BOLD
            titleView.maxLines = 1
            titleView.ellipsize = TextUtils.TruncateAt.END
            titleView
        }
        // The "+ Add" card is an action, not a category preview, so its label always sits on top.
        if ((showTitle || isAddCategory) && (!titleAtBottom || isAddCategory)) {
            card.addView(
                makeTitle(),
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

        if (fitGridToHost && cellSizePx <= 0) {
            // The workspace card has not been measured yet, or has no usable icon area.
            // Never fall back to fixed-size drawer icons: they overflow compact cards.
            host.contentDescription = title
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
        // Drawer cards have a known preview height. Workspace cards are measured
        // from a 2x2 CellLayout span, which varies with the active grid/device profile.
        // Let the workspace grid consume the remaining measured height instead of
        // forcing 128dp and clipping the second icon row on compact home-screen cells.
        // A workspace card is measured from a CellLayout span whose cell height varies with
        // the active grid. Size the rows from the real cell size the host reports so icons
        // never overflow their row (which made them overlap and look distorted), and let the
        // grid wrap its content instead of being stretched by a layout weight.
        val rows = if (maxIcons <= 0) 2 else (maxIcons + columns - 1) / columns
        val explicitCell = fitGridToHost && cellSizePx > 0
        val gridHeight = when {
            explicitCell -> cellSizePx * rows
            fitGridToHost -> 0
            else -> dp(context, 128f)
        }
        val gridLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            gridHeight,
            if (fitGridToHost && !explicitCell) 1f else 0f,
        )
        gridLp.topMargin = dp(context, if (fitGridToHost) 6f else 8f)
        card.addView(grid, gridLp)

        // Keep the category preview visually balanced: equal icon slots in a
        // consistent grid. Remaining apps are available after opening.
        for (index in 0 until minOf(apps.size, maxIcons)) {
            val app = apps[index]
            val cell = FrameLayout(context)
            val cellLp = GridLayout.LayoutParams()
            cellLp.width = 0
            cellLp.height = when {
                explicitCell -> cellSizePx
                fitGridToHost -> 0
                else -> dp(context, 64f)
            }
            cellLp.columnSpec = GridLayout.spec(index % columns, 1f)
            cellLp.rowSpec = if (fitGridToHost && !explicitCell) {
                GridLayout.spec(index / columns, 1f)
            } else {
                GridLayout.spec(index / columns)
            }
            grid.addView(cell, cellLp)
            // Keep the icon strictly inside its cell so it can never spill into the
            // neighbouring row.
            val iconSize = if (explicitCell) {
                CaddyCardMetrics.iconSizePx(cellSizePx, iconFillRatio)
            } else {
                dp(context, if (fitGridToHost) 54f else 56f)
            }
            addCaddyAppIcon(
                context,
                cell,
                app,
                iconSize,
                iconBinder,
                fitGridToHost,
                if (explicitCell) cellSizePx else 0,
            )
        }
        // iOS App Library places the category name under its icons; the drawer and workspace keep
        // the name on top.
        if ((showTitle || isAddCategory) && titleAtBottom && !isAddCategory) {
            card.addView(
                makeTitle(),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        host.contentDescription = title
    }

    private fun addCaddyAppIcon(
        context: Context,
        cell: FrameLayout,
        app: AppInfo,
        iconSize: Int,
        iconBinder: (BubbleTextView, AppInfo) -> Unit,
        fitGridToHost: Boolean,
        cellSizePx: Int,
    ) {
        // Use the same launcher icon widget as the default drawer. Its tag,
        // popup anchor, and long-press lifecycle are understood by Lawnchair's
        // native app action/drag handlers.
        val icon = LayoutInflater.from(context)
            .inflate(R.layout.all_apps_icon, cell, false) as BubbleTextView
        icon.applyFromApplicationInfo(app)
        icon.setTextVisibility(false)
        icon.setLongPressTimeoutFactor(1f)
        icon.contentDescription = app.title
        iconBinder(icon, app)
        // all_apps_icon.xml enables centerVertically, which reserves a text line
        // even when setTextVisibility(false) only makes that text transparent.
        // In Caddy's compact preview cell that produces negative top padding and
        // clips adaptive icons into a flat-bottom shape. Disable that mode and
        // center only the actual drawable using BubbleTextView's native icon size.
        icon.setCenterVertically(false)
        icon.gravity = Gravity.CENTER
        cell.clipChildren = false
        cell.clipToPadding = false
        icon.clipToOutline = false

        if (fitGridToHost && cellSizePx > 0) {
            // Shrink the drawable itself with setIconSizeOverride and give the icon a viewport
            // exactly its own size, centered in the cell. Scaling the whole view instead left it
            // laid out at the native (larger) size, so neighbouring cells' icons overlapped in the
            // middle of the card; overriding the drawable bounds keeps every icon inside its cell.
            icon.setIconSizeOverride(iconSize)
            icon.setPadding(0, 0, 0, 0)
            cell.addView(
                icon,
                FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER),
            )
            return
        }

        // Drawer card: keep the original full-cell layout with a vertical inset.
        val topInset = Math.max(0, (cell.layoutParams?.height ?: 0) - icon.iconSize) / 2
        icon.setPadding(icon.paddingLeft, topInset, icon.paddingRight, 0)
        cell.addView(
            icon,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            ),
        )
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

    /** Blends [amount] of [accent] into [base]; used to tint the "+ Add" card. */
    private fun blend(base: Int, accent: Int, amount: Float): Int = Color.rgb(
        Math.round(Color.red(base) * (1f - amount) + Color.red(accent) * amount),
        Math.round(Color.green(base) * (1f - amount) + Color.green(accent) * amount),
        Math.round(Color.blue(base) * (1f - amount) + Color.blue(accent) * amount),
    )

    private const val MAX_PREVIEW_ICONS = 4
    private const val ICON_FILL_RATIO = 0.86f
}
