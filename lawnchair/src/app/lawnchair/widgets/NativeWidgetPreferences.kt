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

package app.lawnchair.widgets

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.widget.RemoteViews
import com.android.launcher3.R

/**
 * Per-widget configuration for the native Lawnchair widget library.
 *
 * Settings are stored in a dedicated [SharedPreferences] file keyed by the app widget id, so two
 * instances of the same widget can look different.
 */
object NativeWidgetPreferences {

    private const val FILE_NAME = "lawnchair_widgets"

    /** Filled card using the themed surface color. */
    const val STYLE_FILLED = 0

    /** Card with no background, letting the wallpaper show through. */
    const val STYLE_TRANSPARENT = 1

    /** Tonal card using the accent container color. */
    const val STYLE_TONAL = 2

    private const val KEY_STYLE = "style"
    private const val KEY_SHOW_SECONDS = "show_seconds"
    private const val KEY_SHOW_HEADER = "show_header"
    private const val KEY_SHOW_DETAIL = "show_detail"

    private fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private fun key(prefix: String, appWidgetId: Int) = "${prefix}_$appWidgetId"

    fun getStyle(context: Context, appWidgetId: Int): Int = prefs(context).getInt(key(KEY_STYLE, appWidgetId), STYLE_FILLED)

    fun setStyle(context: Context, appWidgetId: Int, style: Int) {
        prefs(context).edit().putInt(key(KEY_STYLE, appWidgetId), style).apply()
    }

    fun getShowSeconds(context: Context, appWidgetId: Int): Boolean = prefs(context).getBoolean(key(KEY_SHOW_SECONDS, appWidgetId), false)

    fun setShowSeconds(context: Context, appWidgetId: Int, show: Boolean) {
        prefs(context).edit().putBoolean(key(KEY_SHOW_SECONDS, appWidgetId), show).apply()
    }

    /** Whether the small icon + label header is shown above the widget content. */
    fun getShowHeader(context: Context, appWidgetId: Int): Boolean = prefs(context).getBoolean(key(KEY_SHOW_HEADER, appWidgetId), true)

    fun setShowHeader(context: Context, appWidgetId: Int, show: Boolean) {
        prefs(context).edit().putBoolean(key(KEY_SHOW_HEADER, appWidgetId), show).apply()
    }

    /** Whether the secondary detail line is shown (battery status, storage usage, ...). */
    fun getShowDetail(context: Context, appWidgetId: Int): Boolean = prefs(context).getBoolean(key(KEY_SHOW_DETAIL, appWidgetId), true)

    fun setShowDetail(context: Context, appWidgetId: Int, show: Boolean) {
        prefs(context).edit().putBoolean(key(KEY_SHOW_DETAIL, appWidgetId), show).apply()
    }

    fun delete(context: Context, appWidgetId: Int) {
        prefs(context).edit()
            .remove(key(KEY_STYLE, appWidgetId))
            .remove(key(KEY_SHOW_SECONDS, appWidgetId))
            .remove(key(KEY_SHOW_HEADER, appWidgetId))
            .remove(key(KEY_SHOW_DETAIL, appWidgetId))
            .apply()
    }

    /** Applies the configured card background to a freshly built [RemoteViews]. */
    fun applyCard(context: Context, views: RemoteViews, appWidgetId: Int) {
        val background = when (getStyle(context, appWidgetId)) {
            STYLE_TRANSPARENT -> R.drawable.widget_card_background_transparent
            STYLE_TONAL -> R.drawable.widget_card_background_tonal
            else -> R.drawable.widget_card_background
        }
        views.setInt(R.id.widget_root, "setBackgroundResource", background)
    }

    private fun isTonal(context: Context, appWidgetId: Int) = getStyle(context, appWidgetId) == STYLE_TONAL

    /** Primary text/icon color for the selected style. */
    fun primaryColor(context: Context, appWidgetId: Int): Int = context.getColor(
        if (isTonal(context, appWidgetId)) R.color.widget_on_tonal_surface else R.color.widget_on_surface,
    )

    /** Secondary/detail text color for the selected style. */
    fun variantColor(context: Context, appWidgetId: Int): Int = context.getColor(
        if (isTonal(context, appWidgetId)) R.color.widget_on_tonal_surface_variant else R.color.widget_on_surface_variant,
    )

    /** Accent color for the selected style (kept legible on the tonal container). */
    fun accentColor(context: Context, appWidgetId: Int): Int = context.getColor(
        if (isTonal(context, appWidgetId)) R.color.widget_on_tonal_surface else R.color.widget_accent,
    )

    /**
     * Applies the standard header (icon + label) of a widget.
     *
     * [R.id.widget_header], [R.id.widget_header_icon] and [R.id.widget_header_label] must exist in
     * the layout; every native widget layout includes `@layout/widget_header`.
     */
    fun applyHeader(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        iconRes: Int,
        label: CharSequence,
    ) {
        val visible = if (getShowHeader(context, appWidgetId)) View.VISIBLE else View.GONE
        views.setViewVisibility(R.id.widget_header, visible)
        if (visible == View.VISIBLE) {
            views.setImageViewResource(R.id.widget_header_icon, iconRes)
            views.setInt(R.id.widget_header_icon, "setColorFilter", accentColor(context, appWidgetId))
            views.setTextViewText(R.id.widget_header_label, label)
            views.setTextColor(R.id.widget_header_label, variantColor(context, appWidgetId))
        }
    }

    /** Shows or hides the standard secondary detail line, when the layout has one. */
    fun applyDetailVisibility(context: Context, views: RemoteViews, appWidgetId: Int) {
        val visible = if (getShowDetail(context, appWidgetId)) View.VISIBLE else View.GONE
        views.setViewVisibility(R.id.widget_detail, visible)
    }
}
