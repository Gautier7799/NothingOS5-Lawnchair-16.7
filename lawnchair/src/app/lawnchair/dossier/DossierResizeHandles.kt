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

package app.lawnchair.dossier

/**
 * Pure geometry for the Dossier resize frame: which border the finger is grabbing.
 *
 * Kept free of Android types so the decision can be unit tested on the JVM. The frame
 * asks for the active borders on every touch-down, and this object decides which of the
 * four edges the point falls on, given the frame size and the touch target width.
 */
object DossierResizeHandles {

    /** The four edges of the frame; [any] is true when the point grabbed at least one. */
    data class Borders(
        val left: Boolean,
        val top: Boolean,
        val right: Boolean,
        val bottom: Boolean,
    ) {
        val any: Boolean get() = left || right || top || bottom
    }

    /**
     * Resolves the active borders for a point at ([x], [y]) inside a frame of
     * [width] x [height], where [touchTargetWidth] is the thickness of each edge target.
     */
    fun activeBorders(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        touchTargetWidth: Int,
    ): Borders = Borders(
        left = x < touchTargetWidth,
        top = y < touchTargetWidth,
        right = x > width - touchTargetWidth,
        bottom = y > height - touchTargetWidth,
    )
}
