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
 * Pure geometry for the Caddy folder resize frame: which edge the finger is grabbing.
 *
 * Kept free of Android types so the decision can be unit tested on the JVM.
 */
object CaddyFolderResizeHandles {

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

    /**
     * Whether the deferred resize frame may be revealed now.
     *
     * A Caddy folder dropped in from the drawer lands as a single cell; its resize frame
     * is revealed once the launcher has settled, because the frame snapshots the folder's
     * window position. Creating it during a state or page transition leaves it at a skewed
     * position, and a page transition (the empty-screen strip that follows a drop) tears
     * `TYPE_WIDGET_RESIZE_FRAME` down, so the handles flash and vanish. Every gate must be
     * clear before the frame is shown.
     */
    @JvmStatic
    fun shouldRevealFrame(
        hasPendingFolder: Boolean,
        inNormalState: Boolean,
        inStateTransition: Boolean,
        inPageTransition: Boolean,
    ): Boolean = hasPendingFolder && inNormalState && !inStateTransition && !inPageTransition
}
