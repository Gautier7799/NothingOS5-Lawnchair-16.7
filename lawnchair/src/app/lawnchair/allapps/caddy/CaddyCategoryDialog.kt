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

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.R
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.touch.ItemClickHandler
import com.android.launcher3.touch.ItemLongClickListener

/**
 * The panel shown when a Caddy category card is opened.
 *
 * This is the same presentation the app drawer uses for its category cards, extracted
 * so the workspace Dossier card opens the identical panel. It is intentionally a plain
 * [Dialog] rather than a folder: the apps stay real launcher shortcuts and keep the
 * launcher's own click and long-click handlers.
 */
object CaddyCategoryDialog {

    private const val COLUMN_COUNT = 4

    /** Shows [apps] under [title]. Does nothing when [apps] is empty. */
    @JvmStatic
    fun show(context: Context, title: String?, apps: List<AppInfo>) {
        if (apps.isEmpty()) return

        val dialog = Dialog(context)
        val panel = LinearLayout(context)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(dp(context, 20f), dp(context, 18f), dp(context, 20f), dp(context, 16f))

        val dynamicAccent = CaddyCardRenderer.resolveDynamicColor(context)
        val glassBase = Color.rgb(27, 35, 37)
        val glassTop = Color.rgb(39, 49, 51)
        val glassBottom = Color.rgb(25, 33, 35)
        val tintedTop = Color.rgb(
            (Color.red(glassTop) * 3 + Color.red(dynamicAccent)) / 4,
            (Color.green(glassTop) * 3 + Color.green(dynamicAccent)) / 4,
            (Color.blue(glassTop) * 3 + Color.blue(dynamicAccent)) / 4,
        )
        panel.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.argb(246, Color.red(tintedTop), Color.green(tintedTop), Color.blue(tintedTop)),
                Color.argb(250, Color.red(glassBase), Color.green(glassBase), Color.blue(glassBase)),
                Color.argb(252, Color.red(glassBottom), Color.green(glassBottom), Color.blue(glassBottom)),
            ),
        ).apply {
            cornerRadius = dp(context, 30f).toFloat()
            setStroke(dp(context, 1f), Color.argb(48, 255, 255, 255))
        }
        panel.clipToOutline = true
        panel.elevation = dp(context, 12f).toFloat()

        val heading = TextView(context)
        heading.text = title
        heading.setTextColor(Color.WHITE)
        heading.setTextSize(20f)
        heading.typeface = Typeface.DEFAULT_BOLD
        heading.setPadding(0, 0, 0, dp(context, 14f))
        panel.addView(
            heading,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val scrollView = ScrollView(context)
        scrollView.isFillViewport = false
        panel.addView(
            scrollView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        val appGrid = GridLayout(context)
        appGrid.columnCount = COLUMN_COUNT
        appGrid.useDefaultMargins = false
        scrollView.addView(
            appGrid,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        apps.forEach { app ->
            val appCell = LinearLayout(context)
            appCell.orientation = LinearLayout.VERTICAL
            appCell.gravity = Gravity.CENTER
            appCell.setPadding(dp(context, 3f), dp(context, 8f), dp(context, 3f), dp(context, 8f))

            val iconFrame = FrameLayout(context)
            appCell.addView(
                iconFrame,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 68f)),
            )

            val appIcon = LayoutInflater.from(context)
                .inflate(R.layout.all_apps_icon, iconFrame, false) as BubbleTextView
            appIcon.applyFromApplicationInfo(app)
            appIcon.setTextVisibility(false)
            appIcon.setLongPressTimeoutFactor(1f)
            appIcon.contentDescription = app.title
            appIcon.setOnClickListener { view ->
                // Let the launcher handle the click while the icon is still attached to
                // its dialog hierarchy; dismissing first can detach the popup anchor
                // before the handler resolves the app launch.
                ItemClickHandler.INSTANCE.onClick(view)
                dialog.dismiss()
            }
            appIcon.setOnLongClickListener(ItemLongClickListener.INSTANCE_ALL_APPS)
            appIcon.setCenterVertically(false)
            val inset = Math.max(0, (dp(context, 68f) - appIcon.iconSize) / 2)
            appIcon.setPadding(appIcon.paddingLeft, inset, appIcon.paddingRight, 0)
            appIcon.gravity = Gravity.CENTER
            iconFrame.clipChildren = false
            iconFrame.clipToPadding = false
            appIcon.clipToOutline = false
            iconFrame.addView(
                appIcon,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER,
                ),
            )

            val appLabel = TextView(context)
            appLabel.text = app.title
            appLabel.setTextColor(Color.WHITE)
            appLabel.setTextSize(11f)
            appLabel.gravity = Gravity.CENTER
            appLabel.maxLines = 2
            appLabel.ellipsize = TextUtils.TruncateAt.END
            val labelParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 34f),
            )
            labelParams.topMargin = dp(context, 5f)
            appCell.addView(appLabel, labelParams)

            val cellParams = GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(context, 2f), dp(context, 2f), dp(context, 2f), dp(context, 2f))
            }
            appGrid.addView(appCell, cellParams)
        }

        val screenHeight = context.resources.displayMetrics.heightPixels
        val maxPanelHeight = Math.max(dp(context, 300f), screenHeight - dp(context, 176f))
        val contentRows = (apps.size + COLUMN_COUNT - 1) / COLUMN_COUNT
        val desiredPanelHeight = dp(context, 76f) + contentRows * dp(context, 96f) + dp(context, 34f)
        val panelHeight = Math.min(desiredPanelHeight, maxPanelHeight)

        dialog.setContentView(panel)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setCancelable(true)
        dialog.show()

        dialog.window?.let { window: Window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            val windowParams = window.attributes
            windowParams.width = Math.min(
                context.resources.displayMetrics.widthPixels - dp(context, 32f),
                dp(context, 560f),
            )
            windowParams.height = panelHeight
            windowParams.gravity = Gravity.CENTER
            windowParams.dimAmount = 0.34f
            window.attributes = windowParams
            window.setDimAmount(0.34f)
        }

        panel.alpha = 0f
        panel.scaleX = 0.965f
        panel.scaleY = 0.965f
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(190L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun dp(context: Context, value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        context.resources.displayMetrics,
    ).toInt()
}
