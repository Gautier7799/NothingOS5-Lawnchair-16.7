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

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.android.launcher3.R

/**
 * Current conditions for a city the user chooses in the widget configuration.
 *
 * The weather itself comes from Open-Meteo (keyless), so no account or API key is involved and the
 * widget needs no location permission: the configuration screen geocodes the chosen city once and
 * the resolved coordinates are stored. The last successful reading is cached so the widget still
 * shows something useful when the device is offline.
 */
class WeatherWidgetProvider : LawnchairWidgetProvider() {

    override fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_weather)
        NativeWidgetPreferences.applyCard(context, views, appWidgetId)
        NativeWidgetPreferences.applyHeader(
            context,
            views,
            appWidgetId,
            R.drawable.ic_widget_weather,
            context.getString(R.string.widget_weather_label),
        )
        NativeWidgetPreferences.applyInset(context, views, appWidgetId)
        NativeWidgetPreferences.applyDetailVisibility(context, views, appWidgetId)
        views.setTextColor(R.id.widget_value, NativeWidgetPreferences.primaryColor(context, appWidgetId))

        val location = WidgetFormat.parseWeatherLocation(
            NativeWidgetPreferences.getWeatherLocation(context, appWidgetId),
        )
        if (location == null) {
            renderUnconfigured(context, views)
            return views
        }

        val cached = WidgetFormat.parseWeatherSnapshot(
            NativeWidgetPreferences.getWeatherCache(context, appWidgetId),
        )
        if (cached != null) {
            renderSnapshot(context, views, cached)
        } else {
            renderLoading(context, views)
        }
        refresh(context, appWidgetId, location)
        return views
    }

    private fun renderUnconfigured(context: Context, views: RemoteViews) {
        views.setImageViewResource(R.id.widget_weather_icon, R.drawable.ic_weather_unknown)
        views.setTextViewText(R.id.widget_value, context.getString(R.string.widget_weather_unset))
        views.setTextViewText(R.id.widget_inset_detail, context.getString(R.string.widget_weather_configure_hint))
        views.setViewVisibility(R.id.widget_weather_range, View.GONE)
    }

    private fun renderLoading(context: Context, views: RemoteViews) {
        views.setImageViewResource(R.id.widget_weather_icon, R.drawable.ic_weather_unknown)
        views.setTextViewText(R.id.widget_value, context.getString(R.string.widget_weather_unset))
        views.setTextViewText(R.id.widget_inset_detail, context.getString(R.string.widget_weather_loading))
        views.setViewVisibility(R.id.widget_weather_range, View.GONE)
    }

    private fun renderSnapshot(context: Context, views: RemoteViews, snapshot: WidgetFormat.WeatherSnapshot) {
        val kind = WeatherConditions.kindOf(snapshot.weatherCode)
        views.setImageViewResource(R.id.widget_weather_icon, iconFor(kind))
        views.setTextViewText(R.id.widget_value, WidgetFormat.formatTemperature(snapshot.temperatureC))
        views.setTextViewText(R.id.widget_inset_detail, conditionLabel(context, kind))
        views.setTextViewText(R.id.widget_weather_range, WidgetFormat.formatHighLow(snapshot.highC, snapshot.lowC))
        views.setViewVisibility(R.id.widget_weather_range, View.VISIBLE)
    }

    /** Fetches fresh data in the background and repaints the widget when it arrives. */
    private fun refresh(context: Context, appWidgetId: Int, location: WidgetFormat.WeatherLocation) {
        val appContext = context.applicationContext
        WeatherClient.fetchAsync {
            val result = WeatherClient.forecast(location.latitude, location.longitude)
            val snapshot = WidgetFormat.WeatherSnapshot(
                temperatureC = result.temperatureC,
                weatherCode = result.weatherCode,
                highC = result.highC,
                lowC = result.lowC,
            )
            NativeWidgetPreferences.setWeatherCache(appContext, appWidgetId, WidgetFormat.encodeWeatherSnapshot(snapshot))
            val manager = AppWidgetManager.getInstance(appContext) ?: return@fetchAsync
            manager.updateAppWidget(appWidgetId, buildRemoteViews(appContext, appWidgetId))
        }
    }

    private fun iconFor(kind: WeatherKind): Int = when (kind) {
        WeatherKind.CLEAR -> R.drawable.ic_weather_clear
        WeatherKind.PARTLY_CLOUDY -> R.drawable.ic_weather_partly_cloudy
        WeatherKind.CLOUDY -> R.drawable.ic_weather_cloudy
        WeatherKind.FOG -> R.drawable.ic_weather_fog
        WeatherKind.DRIZZLE -> R.drawable.ic_weather_drizzle
        WeatherKind.RAIN -> R.drawable.ic_weather_rain
        WeatherKind.SNOW -> R.drawable.ic_weather_snow
        WeatherKind.THUNDER -> R.drawable.ic_weather_thunder
        WeatherKind.UNKNOWN -> R.drawable.ic_weather_unknown
    }

    private fun conditionLabel(context: Context, kind: WeatherKind): String = context.getString(
        when (kind) {
            WeatherKind.CLEAR -> R.string.widget_weather_condition_clear
            WeatherKind.PARTLY_CLOUDY -> R.string.widget_weather_condition_partly_cloudy
            WeatherKind.CLOUDY -> R.string.widget_weather_condition_cloudy
            WeatherKind.FOG -> R.string.widget_weather_condition_fog
            WeatherKind.DRIZZLE -> R.string.widget_weather_condition_drizzle
            WeatherKind.RAIN -> R.string.widget_weather_condition_rain
            WeatherKind.SNOW -> R.string.widget_weather_condition_snow
            WeatherKind.THUNDER -> R.string.widget_weather_condition_thunder
            WeatherKind.UNKNOWN -> R.string.widget_weather_condition_unknown
        },
    )
}
