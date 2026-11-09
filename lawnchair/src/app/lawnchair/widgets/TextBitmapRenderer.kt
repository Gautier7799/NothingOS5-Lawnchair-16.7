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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Renders a short string into a bitmap for the widgets that display a large figure.
 *
 * The reference Material You widgets draw their hero number into an `ImageView` rather than using a
 * `TextView`: a bitmap lets them apply a custom typeface and control the exact metrics inside a
 * `RemoteViews` layout, where an `ImageView` with a scaled bitmap is more predictable across
 * launchers than a stretched `TextView`. The same approach is used here so the ported layouts keep
 * the reference's proportions.
 *
 * The bitmap is transparent and tightly cropped to the text bounds, so it can be placed with plain
 * `wrap_content` sizing. Results are cached per (text, size, colour, typeface) because widget
 * updates are frequent and rasterising text is comparatively expensive.
 */
object TextBitmapRenderer {

    private const val CACHE_LIMIT = 8

    private val cache = object : LinkedHashMap<String, Bitmap>(CACHE_LIMIT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean = size > CACHE_LIMIT
    }

    /**
     * Draws [text] at [textSizePx] in [color] and returns the resulting bitmap.
     *
     * A `null` [typeface] falls back to the default sans-serif medium, which is what the
     * reference's plain text styles resolve to.
     */
    fun render(
        text: String,
        textSizePx: Float,
        color: Int,
        typeface: Typeface? = null,
    ): Bitmap {
        val key = "$text|$textSizePx|$color|${typeface?.hashCode() ?: 0}"
        cache[key]?.let { if (!it.isRecycled) return it }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = textSizePx
            this.typeface = typeface ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        val metrics = paint.fontMetrics
        val width = paint.measureText(text).toInt().coerceAtLeast(1)
        val height = (metrics.descent - metrics.ascent).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, 0f, -metrics.ascent, paint)

        cache[key] = bitmap
        return bitmap
    }

    /** Invalidates the cache, e.g. when the dynamic colour changes. */
    fun clear() = cache.clear()
}
