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

    /**
     * The factor that scales a [nativeSizePx] square icon down so it renders at
     * [targetSizePx], further clamped to a [cellWidthPx] x [cellHeightPx] cell.
     *
     * The preview icon is scaled as a View rather than by overriding the drawable bounds:
     * `BubbleTextView.applyCompoundDrawables()` sets the bounds, then calls `updateIcon()` ->
     * `TextView.setCompoundDrawables()`, and a compound drawable's bounds are reset to its
     * intrinsic size. A bounds override is therefore discarded and the drawable is drawn at its
     * native size, which is wider than a narrow drawer cell, so the cell crops both sides and the
     * icon reads as squeezed and non-circular. A View scale is applied at draw time and nothing in
     * TextView resets it.
     *
     * [targetSizePx] is the size the card wants (a fraction of the cell); the result never
     * exceeds it, so a caller's fill ratio is preserved, and never exceeds the cell on either
     * axis, so the icon cannot be cropped.
     */
    fun iconScale(
        cellWidthPx: Int,
        cellHeightPx: Int,
        nativeSizePx: Int,
        targetSizePx: Int = nativeSizePx,
    ): Float {
        if (nativeSizePx <= 0) return 1f
        if (cellWidthPx <= 0 || cellHeightPx <= 0) return 1f
        val target = if (targetSizePx > 0) targetSizePx else nativeSizePx
        val fitted = minOf(target, cellWidthPx, cellHeightPx)
        return (fitted.toFloat() / nativeSizePx).coerceIn(0f, 1f)
    }
}
