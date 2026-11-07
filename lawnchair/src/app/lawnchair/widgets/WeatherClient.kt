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

import android.util.Log
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal, keyless client for Open-Meteo (open-meteo.com), used as the weather source for the
 * native Lawnchair weather widget.
 *
 * Open-Meteo needs no API key or account, which keeps the widget self-contained. The user picks a
 * city in the widget configuration; the city is geocoded once and the coordinates are stored, so
 * the widget never needs a location permission.
 */
object WeatherClient {

    private const val TAG = "LawnchairWeather"
    private const val GEOCODE_URL = "https://geocoding-api.open-meteo.com/v1/search"
    private const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"

    /** A resolved place the widget can show weather for. */
    data class Place(val name: String, val latitude: Double, val longitude: Double)

    /** Current conditions plus today's range. */
    data class Snapshot(
        val temperatureC: Int,
        val weatherCode: Int,
        val highC: Int,
        val lowC: Int,
    )

    /** Resolves a free-text city name to a place, or null when nothing matched. */
    @Throws(IOException::class)
    fun geocode(city: String): Place? {
        val query = "?name=${URLEncoder.encode(city, "UTF-8")}&count=1&language=en&format=json"
        val root = JSONObject(get(GEOCODE_URL + query))
        val results = root.optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val first = results.getJSONObject(0)
        return Place(
            name = first.optString("name"),
            latitude = first.getDouble("latitude"),
            longitude = first.getDouble("longitude"),
        )
    }

    /** Fetches the current conditions for [latitude]/[longitude]. */
    @Throws(IOException::class)
    fun forecast(latitude: Double, longitude: Double): Snapshot {
        val query = "?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,weather_code" +
            "&daily=temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=1"
        val root = JSONObject(get(FORECAST_URL + query))
        val current = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        return Snapshot(
            temperatureC = Math.round(current.getDouble("temperature_2m")).toInt(),
            weatherCode = current.optInt("weather_code", -1),
            highC = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
            lowC = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
        )
    }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** Runs [block] off the main thread, logging and swallowing failures. */
    fun fetchAsync(block: () -> Unit) {
        Thread {
            runCatching { block() }.onFailure { Log.w(TAG, "weather fetch failed", it) }
        }.start()
    }
}
