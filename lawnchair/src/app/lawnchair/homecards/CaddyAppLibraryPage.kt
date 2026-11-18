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
 * Full-screen Caddy App Library.
 *
 * Presents every app on the device grouped into the same categories the drawer's
 * Caddy cards use, but in one scrollable page so the whole library is reachable
 * without opening the drawer. Tapping a category title opens the shared category
 * panel; tapping an icon launches the app exactly like the drawer does.
 */
class CaddyAppLibraryPage @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractFloatingView(context, attrs, defStyleAttr) {

    private val content = LinearLayout(context)

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.argb(236, 12, 14, 16))
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

        content.addView(buildHeader(launcher))
        if (categories.isEmpty()) return

        val scroll = ScrollView(context)
        val column = LinearLayout(context)
        column.orientation = VERTICAL
        column.setPadding(dp(16f), 0, dp(16f), dp(24f))
        scroll.addView(column)
        content.addView(
            scroll,
            LayoutParams(LayoutParams.MATCH_PARENT, 0).apply { weight = 1f },
        )

        val resolveApp = appResolver(apps)
        categories.forEach { category ->
            column.addView(buildCategoryTitle(category.title, category.appKeys.mapNotNull(resolveApp)))
            column.addView(buildCategoryGrid(category.appKeys.mapNotNull(resolveApp)))
        }
    }

    /** Marks the page open. Split out so the companion can set the protected flag. */
    fun markOpen() {
        mIsOpen = true
    }

    private fun buildHeader(launcher: Launcher): View {
        val header = LinearLayout(context)
        header.orientation = HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(20f), dp(18f), dp(12f), dp(12f))

        val title = TextView(context)
        title.text = context.getString(R.string.caddy_app_library_title)
        title.setTextColor(Color.WHITE)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        title.typeface = Typeface.DEFAULT_BOLD
        header.addView(
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
        header.addView(close, LinearLayout.LayoutParams(dp(48f), dp(48f)))
        return header
    }

    private fun buildCategoryTitle(title: String, apps: List<AppInfo>): View {
        val view = TextView(context)
        view.text = title
        view.setTextColor(Color.WHITE)
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        view.typeface = Typeface.DEFAULT_BOLD
        view.setPadding(dp(4f), dp(16f), dp(4f), dp(4f))
        view.isClickable = true
        view.setOnClickListener { CaddyCategoryDialog.show(context, title, apps) }
        return view
    }

    private fun buildCategoryGrid(apps: List<AppInfo>): View {
        val grid = GridLayout(context)
        grid.columnCount = COLUMN_COUNT
        grid.useDefaultMargins = false
        apps.forEachIndexed { index, app ->
            val cell = FrameLayout(context)
            val icon = LayoutInflater.from(context)
                .inflate(R.layout.all_apps_icon, cell, false) as BubbleTextView
            icon.applyFromApplicationInfo(app)
            icon.setTextVisibility(false)
            icon.setLongPressTimeoutFactor(1f)
            icon.contentDescription = app.title
            icon.setOnClickListener(ItemClickHandler.INSTANCE)
            icon.setOnLongClickListener(CaddyCardIconLongClick.LISTENER)
            icon.gravity = Gravity.CENTER
            cell.addView(icon)
            val params = GridLayout.LayoutParams()
            params.width = 0
            params.height = dp(64f)
            params.columnSpec = GridLayout.spec(index % COLUMN_COUNT, 1f)
            grid.addView(cell, params)
        }
        return grid
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
        private const val COLUMN_COUNT = 4
        private const val CLOSE_DURATION_MS = 150L

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

        private const val OPEN_DURATION_MS = 180L
    }
}
