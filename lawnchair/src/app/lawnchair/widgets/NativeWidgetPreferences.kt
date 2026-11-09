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

    /** Default daily step target for the pedometer widget. */
    const val DEFAULT_STEP_GOAL = 10000

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
    private const val KEY_WEATHER_LOCATION = "weather_location"
    private const val KEY_WEATHER_CACHE = "weather_cache"
    private const val KEY_HERO_CLOCK = "hero_clock"
    private const val KEY_DAILY_KIND = "daily_kind"
    private const val KEY_DAILY_OFFSET = "daily_offset"
    private const val KEY_STEP_GOAL = "step_goal"
    private const val KEY_STEP_BASELINE = "step_baseline"
    private const val KEY_STEP_BASELINE_DAY = "step_baseline_day"

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

    /**
     * The weather location as a `latitude,longitude,label` triple, or null when unset.
     *
     * Storing the resolved coordinates avoids geocoding (and therefore a network round-trip) on
     * every widget refresh.
     */
    fun getWeatherLocation(context: Context, appWidgetId: Int): String? = prefs(context).getString(key(KEY_WEATHER_LOCATION, appWidgetId), null)

    fun setWeatherLocation(context: Context, appWidgetId: Int, location: String?) {
        prefs(context).edit().putString(key(KEY_WEATHER_LOCATION, appWidgetId), location).apply()
    }

    /** Last successfully fetched weather snapshot, encoded for display, or null. */
    fun getWeatherCache(context: Context, appWidgetId: Int): String? = prefs(context).getString(key(KEY_WEATHER_CACHE, appWidgetId), null)

    fun setWeatherCache(context: Context, appWidgetId: Int, cache: String?) {
        prefs(context).edit().putString(key(KEY_WEATHER_CACHE, appWidgetId), cache).apply()
    }

    /**
     * Whether the clock uses the reference "hero" layout: oversized hour digits with smaller
     * minutes. Defaults to on, since it is the design the library is modelled on.
     */
    fun getHeroClock(context: Context, appWidgetId: Int): Boolean = prefs(context).getBoolean(key(KEY_HERO_CLOCK, appWidgetId), true)

    fun setHeroClock(context: Context, appWidgetId: Int, hero: Boolean) {
        prefs(context).edit().putBoolean(key(KEY_HERO_CLOCK, appWidgetId), hero).apply()
    }

    /** Which data set the daily-content widget shows (quote or fact). */
    fun getDailyKind(
        context: Context,
        appWidgetId: Int,
        default: DailyContentWidgetProvider.Kind = DailyContentWidgetProvider.Kind.QUOTE,
    ): DailyContentWidgetProvider.Kind {
        val stored = prefs(context).getString(key(KEY_DAILY_KIND, appWidgetId), null) ?: return default
        return runCatching { DailyContentWidgetProvider.Kind.valueOf(stored) }.getOrDefault(default)
    }

    fun setDailyKind(context: Context, appWidgetId: Int, kind: DailyContentWidgetProvider.Kind) {
        prefs(context).edit().putString(key(KEY_DAILY_KIND, appWidgetId), kind.name).apply()
    }

    /** How many days ahead of today the daily-content widget is currently showing. */
    fun getDailyOffset(context: Context, appWidgetId: Int): Int = prefs(context).getInt(key(KEY_DAILY_OFFSET, appWidgetId), 0)

    fun setDailyOffset(context: Context, appWidgetId: Int, offset: Int) {
        prefs(context).edit().putInt(key(KEY_DAILY_OFFSET, appWidgetId), offset.coerceAtLeast(0)).apply()
    }

    /** The step-count goal used by the pedometer widget. */
    fun getStepGoal(context: Context, appWidgetId: Int): Int = prefs(context).getInt(key(KEY_STEP_GOAL, appWidgetId), DEFAULT_STEP_GOAL)

    fun setStepGoal(context: Context, appWidgetId: Int, goal: Int) {
        prefs(context).edit().putInt(key(KEY_STEP_GOAL, appWidgetId), goal.coerceAtLeast(1)).apply()
    }

    /** The sensor's since-boot step total captured at the start of the current day, or null. */
    fun getStepBaseline(context: Context, appWidgetId: Int): Float? {
        val raw = prefs(context).getFloat(key(KEY_STEP_BASELINE, appWidgetId), Float.NaN)
        return if (raw.isNaN()) null else raw
    }

    fun setStepBaseline(context: Context, appWidgetId: Int, total: Float) {
        prefs(context).edit().putFloat(key(KEY_STEP_BASELINE, appWidgetId), total).apply()
    }

    /** Epoch day on which [getStepBaseline] was captured. */
    fun getStepBaselineDay(context: Context, appWidgetId: Int): Long = prefs(context).getLong(key(KEY_STEP_BASELINE_DAY, appWidgetId), -1L)

    fun setStepBaselineDay(context: Context, appWidgetId: Int, epochDay: Long) {
        prefs(context).edit().putLong(key(KEY_STEP_BASELINE_DAY, appWidgetId), epochDay).apply()
    }

    fun delete(context: Context, appWidgetId: Int) {
        prefs(context).edit()
            .remove(key(KEY_STYLE, appWidgetId))
            .remove(key(KEY_SHOW_SECONDS, appWidgetId))
            .remove(key(KEY_SHOW_HEADER, appWidgetId))
            .remove(key(KEY_SHOW_DETAIL, appWidgetId))
            .remove(key(KEY_WEATHER_LOCATION, appWidgetId))
            .remove(key(KEY_WEATHER_CACHE, appWidgetId))
            .remove(key(KEY_HERO_CLOCK, appWidgetId))
            .remove(key(KEY_DAILY_KIND, appWidgetId))
            .remove(key(KEY_DAILY_OFFSET, appWidgetId))
            .remove(key(KEY_STEP_GOAL, appWidgetId))
            .remove(key(KEY_STEP_BASELINE, appWidgetId))
            .remove(key(KEY_STEP_BASELINE_DAY, appWidgetId))
            .apply()
    }

    /** Applies the configured card background to a freshly built [RemoteViews]. */
    fun applyCard(context: Context, views: RemoteViews, appWidgetId: Int) {
        views.setInt(R.id.widget_root, "setBackgroundResource", cardBackgroundForStyle(getStyle(context, appWidgetId)))
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

    /** Text color inside the nested tonal panel. */
    private fun onInsetColor(context: Context, appWidgetId: Int): Int = context.getColor(
        if (isTonal(context, appWidgetId)) R.color.widget_on_tonal_surface_variant else R.color.widget_on_inset_surface_variant,
    )

    /**
     * Applies the nested tonal panel that holds a widget's secondary reading.
     *
     * [R.id.widget_inset] and [R.id.widget_inset_detail] must exist in the layout; every native
     * widget layout includes `@layout/widget_inset`. The panel follows the selected style so the
     * family stays coherent across filled, tonal and transparent.
     */
    fun applyInset(context: Context, views: RemoteViews, appWidgetId: Int) {
        val background = insetBackgroundForStyle(getStyle(context, appWidgetId))
        views.setInt(R.id.widget_inset, "setBackgroundResource", background)
        views.setTextColor(R.id.widget_inset_detail, onInsetColor(context, appWidgetId))
    }

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

    /** Shows or hides the nested tonal panel that holds a widget's secondary reading. */
    fun applyDetailVisibility(context: Context, views: RemoteViews, appWidgetId: Int) {
        val visible = if (getShowDetail(context, appWidgetId)) View.VISIBLE else View.GONE
        views.setViewVisibility(R.id.widget_inset, visible)
    }
}

/**
 * Maps a card style to the drawable used by its nested inset panel.
 *
 * Kept as a pure function so the style/panel pairing can be unit tested without a device;
 * [NativeWidgetPreferences.applyInset] is the only caller.
 */
internal fun insetBackgroundForStyle(style: Int): Int = when (style) {
    NativeWidgetPreferences.STYLE_TRANSPARENT -> R.drawable.widget_inset_background_transparent
    NativeWidgetPreferences.STYLE_TONAL -> R.drawable.widget_inset_background_tonal
    else -> R.drawable.widget_inset_background
}

/**
 * Maps a card style to the shared rounded-rectangle card drawable.
 *
 * Every native widget family shares this one card geometry: the reference app's own container
 * style resolves `appWidgetRadius` to the platform `system_app_widget_background_radius` token
 * rather than a per-widget silhouette, so a single rounded card is the faithful choice.
 */
internal fun cardBackgroundForStyle(style: Int): Int = when (style) {
    NativeWidgetPreferences.STYLE_TRANSPARENT -> R.drawable.widget_card_background_transparent
    NativeWidgetPreferences.STYLE_TONAL -> R.drawable.widget_card_background_tonal
    else -> R.drawable.widget_card_background
}
