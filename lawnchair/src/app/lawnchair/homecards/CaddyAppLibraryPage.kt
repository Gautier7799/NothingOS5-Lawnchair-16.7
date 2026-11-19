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
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.lawnchair.allapps.caddy.CaddyCategoryDialog
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.touch.ItemClickHandler

/**
 * Full-screen iOS-inspired App Library.
 *
 * Uses the launcher's original app icon views and the same Caddy categories,
 * presented as a two-column grid of translucent glass-style category cards.
 */
class CaddyAppLibraryPage @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractFloatingView(context, attrs, defStyleAttr) {

    private val content = LinearLayout(context)

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        isFocusable = true
        addView(
            content,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    fun bind(launcher: Launcher) {
        content.removeAllViews()
        val apps = launcher.appsView?.appsStore?.apps?.toList().orEmpty()
        val categories = CaddyHomeCardSource.build(context, apps)

        content.addView(buildSearchHeader())
        if (categories.isEmpty()) return

        val scroll = ScrollView(context)
        scroll.isFillViewport = true
        scroll.clipToPadding = false
        val grid = GridLayout(context).apply {
            columnCount = 2
            useDefaultMargins = false
            setPadding(dp(16f), dp(8f), dp(16f), dp(28f))
        }
        scroll.addView(grid)
        content.addView(
            scroll,
            LayoutParams(LayoutParams.MATCH_PARENT, 0).apply { weight = 1f },
        )

        val resolveApp = appResolver(apps)
        categories.forEachIndexed { index, category ->
            val categoryApps = category.appKeys.mapNotNull(resolveApp)
            if (categoryApps.isEmpty()) return@forEachIndexed
            val card = buildCategoryCard(category.title, categoryApps)
            val params = GridLayout.LayoutParams(
                GridLayout.spec(index / 2, 1f),
                GridLayout.spec(index % 2, 1f),
            ).apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                setMargins(dp(5f), dp(5f), dp(5f), dp(10f))
            }
            grid.addView(card, params)
        }
    }

    /** Marks the page open. Split out so the companion can set the protected flag. */
    fun markOpen() {
        mIsOpen = true
    }

    private fun buildSearchHeader(): View {
        val header = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(20f), dp(18f), dp(20f), dp(12f))
        }
        val search = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            background = roundedSurface(
                fill = Color.argb(48, 255, 255, 255),
                stroke = Color.argb(42, 255, 255, 255),
                radius = 20f,
            )
            setPadding(dp(14f), 0, dp(14f), 0)
            minimumHeight = dp(54f)
        }
        val magnifier = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            setColorFilter(Color.WHITE)
            alpha = 0.82f
            contentDescription = null
        }
        search.addView(magnifier, LinearLayout.LayoutParams(dp(22f), dp(22f)))
        val label = TextView(context).apply {
            text = context.getString(R.string.caddy_app_library_title)
            setTextColor(Color.WHITE)
            alpha = 0.88f
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setPadding(dp(8f), 0, 0, 0)
        }
        search.addView(label)
        header.addView(
            search,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54f),
            ),
        )
        return header
    }

    private fun buildCategoryCard(title: String, apps: List<AppInfo>): View {
        val wrapper = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val surface = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            background = roundedSurface(
                fill = Color.argb(42, 255, 255, 255),
                stroke = Color.argb(64, 255, 255, 255),
                radius = 25f,
            )
            setPadding(dp(8f), dp(9f), dp(8f), dp(9f))
            minimumHeight = dp(154f)
        }

        val featured = apps.take(FEATURED_APP_COUNT)
        featured.chunked(2).forEach { rowApps ->
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
            }
            rowApps.forEach { app ->
                val cell = FrameLayout(context)
                cell.addView(createAppIcon(app), FrameLayout.LayoutParams(dp(62f), dp(62f), Gravity.CENTER))
                row.addView(
                    cell,
                    LinearLayout.LayoutParams(0, dp(66f), 1f),
                )
            }
            if (rowApps.size == 1) {
                row.addView(View(context), LinearLayout.LayoutParams(0, dp(66f), 1f))
            }
            surface.addView(row)
        }

        val extraApps = apps.drop(FEATURED_APP_COUNT)
        if (extraApps.isNotEmpty()) {
            val extras = GridLayout(context).apply {
                columnCount = 4
                setPadding(0, dp(5f), 0, 0)
            }
            extraApps.forEachIndexed { index, app ->
                val cell = FrameLayout(context)
                cell.addView(
                    createAppIcon(app),
                    FrameLayout.LayoutParams(dp(31f), dp(31f), Gravity.CENTER),
                )
                val params = GridLayout.LayoutParams(
                    GridLayout.spec(index / 4),
                    GridLayout.spec(index % 4, 1f),
                ).apply {
                    width = 0
                    height = dp(36f)
                }
                extras.addView(cell, params)
            }
            surface.addView(extras)
        }

        wrapper.addView(
            surface,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        val caption = TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(2f), dp(5f), dp(2f), 0)
            isClickable = true
            setOnClickListener { CaddyCategoryDialog.show(context, title, apps) }
        }
        wrapper.addView(
            caption,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        return wrapper
    }

    private fun createAppIcon(app: AppInfo): BubbleTextView {
        val icon = LayoutInflater.from(context)
            .inflate(R.layout.all_apps_icon, this, false) as BubbleTextView
        icon.applyFromApplicationInfo(app)
        icon.setTextVisibility(false)
        icon.setLongPressTimeoutFactor(1f)
        icon.contentDescription = app.title
        icon.setOnClickListener(ItemClickHandler.INSTANCE)
        icon.setOnLongClickListener(CaddyCardIconLongClick.LISTENER)
        icon.gravity = Gravity.CENTER
        return icon
    }

    private fun roundedSurface(fill: Int, stroke: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius)
            setColor(fill)
            setStroke(dp(1f), stroke)
        }

    private fun appResolver(apps: List<AppInfo>): (String) -> AppInfo? {
        val byKey = apps.associateBy { CaddyHomeCardSource.appKeyOf(it) }
        return { key -> byKey[key] }
    }

    override fun handleClose(animate: Boolean) {
        animate().cancel()
        animate().alpha(0f).setDuration(if (animate) CLOSE_DURATION_MS else 0L)
            .withEndAction { (parent as? ViewGroup)?.removeView(this) }
            .start()
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean = false

    override fun isOfType(type: Int): Boolean = type and AbstractFloatingView.TYPE_APP_LIBRARY != 0

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val FEATURED_APP_COUNT = 4
        private const val CLOSE_DURATION_MS = 150L
        private const val OPEN_DURATION_MS = 180L

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
