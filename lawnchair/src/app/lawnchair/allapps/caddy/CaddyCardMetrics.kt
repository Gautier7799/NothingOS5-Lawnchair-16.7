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

/**
 * Pure geometry for a Caddy card's preview icons.
 *
 * Kept free of Android types so the sizing rules can be unit-tested on the JVM: the caller passes
 * a pixel cell size and the ratio it wants the icon to occupy, and this returns the icon's pixel
 * size, clamped so a card never draws an icon that is too small to tap or that spills past its cell.
 */
object CaddyCardMetrics {

    /** A preview icon never fills more than this fraction of its cell, so it cannot overlap a peer. */
    const val MAX_FILL_RATIO = 0.92f

    /** A preview icon never shrinks below this fraction of its cell, so it stays visible and tappable. */
    const val MIN_FILL_RATIO = 0.30f

    /**
     * The icon's edge length in pixels for a [cellSizePx] square cell, given the requested
     * [fillRatio]. Returns 0 when the cell has not been measured yet.
     */
    fun iconSizePx(cellSizePx: Int, fillRatio: Float): Int {
        if (cellSizePx <= 0) return 0
        val ratio = fillRatio.coerceIn(MIN_FILL_RATIO, MAX_FILL_RATIO)
        return (cellSizePx * ratio).toInt()
    }
}
