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

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure grid geometry for resizing a Caddy folder on the workspace.
 *
 * A Caddy folder is a native [com.android.launcher3.model.data.FolderInfo], so resizing
 * reuses the workspace grid: the user drags an edge handle and the folder's span
 * changes by whole cells, exactly like the launcher's own resize frames. The maths
 * lives here, free of Android types, so it can be unit tested on the JVM.
 */
object CaddyFolderResize {

    /** A grid placement: origin cell and span. */
    data class Placement(val cellX: Int, val cellY: Int, val spanX: Int, val spanY: Int)

    /**
     * Returns the placement after dragging the active borders by [deltaCellsX] /
     * [deltaCellsY] whole cells. The result is always clamped so the folder stays
     * inside the grid and never shrinks below [minSpan].
     *
     * The two edges are modelled independently: dragging the right edge moves the right
     * edge and leaves the left one fixed, dragging the left edge does the reverse. So
     * pulling the left edge further left grows the folder, and pulling it right shrinks
     * it, which is what the gesture looks like on screen.
     *
     * @param left/right/top/bottom which border the user grabbed.
     */
    fun resize(
        start: Placement,
        deltaCellsX: Float,
        deltaCellsY: Float,
        left: Boolean,
        right: Boolean,
        top: Boolean,
        bottom: Boolean,
        countX: Int,
        countY: Int,
        minSpan: Int = MIN_SPAN,
        maxSpanX: Int = countX,
        maxSpanY: Int = countY,
    ): Placement {
        val x = resolveAxis(
            startEdge = start.cellX,
            span = start.spanX,
            increment = increment(deltaCellsX),
            startEdgeDragged = left,
            endEdgeDragged = right,
            count = countX,
            minSpan = minSpan,
            maxSpan = maxSpanX,
        )
        val y = resolveAxis(
            startEdge = start.cellY,
            span = start.spanY,
            increment = increment(deltaCellsY),
            startEdgeDragged = top,
            endEdgeDragged = bottom,
            count = countY,
            minSpan = minSpan,
            maxSpan = maxSpanY,
        )
        return Placement(x.first, y.first, x.second, y.second)
    }

    /**
     * Resolves one axis: the dragged edge moves and the opposite edge stays put.
     * Returns the new start edge and span.
     */
    private fun resolveAxis(
        startEdge: Int,
        span: Int,
        increment: Int,
        startEdgeDragged: Boolean,
        endEdgeDragged: Boolean,
        count: Int,
        minSpan: Int,
        maxSpan: Int,
    ): Pair<Int, Int> {
        val maxAllowed = maxSpan.coerceIn(minSpan, count)
        if (startEdgeDragged) {
            // Moving the start edge leaves the end edge fixed.
            val endEdge = startEdge + span
            val newStart = (startEdge + increment).coerceIn(0, endEdge - minSpan)
            return newStart to (endEdge - newStart)
        }
        if (endEdgeDragged) {
            // Moving the end edge leaves the start edge fixed. The end edge may not leave
            // the grid, so the span is capped by the space left after the start edge.
            val maxEnd = minOf(startEdge + maxAllowed, count)
            val newEnd = (startEdge + span + increment).coerceIn(startEdge + minSpan, maxEnd)
            return startEdge to (newEnd - startEdge)
        }
        return startEdge to span
    }

    /** Converts a pixel distance to whole cells, using a threshold to avoid jitter. */
    private fun increment(deltaCells: Float): Int = if (abs(deltaCells) > STEP_THRESHOLD) deltaCells.roundToInt() else 0

    /** Minimum span, in cells, a folder may be resized to. */
    const val MIN_SPAN = 1

    /** Fraction of a cell the finger must travel before the span changes by one. */
    const val STEP_THRESHOLD = 0.66f
}
