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
     * Resolves the active borders for a touch expressed in the DragLayer's coordinate space.
     *
     * The two dispatch paths a floating view can receive touches on do not agree on the
     * space the point arrives in: the TouchController callbacks
     * ([com.android.launcher3.AbstractFloatingView.onControllerInterceptTouchEvent] and
     * [com.android.launcher3.AbstractFloatingView.onControllerTouchEvent]) are handed
     * DragLayer coordinates, whereas plain view dispatch delivers local ones. Routing both
     * through this one function — with the frame's [frameLeft]/[frameTop] as the origin, or
     * `0, 0` for the already-local case — means the two paths can never disagree about which
     * handle a touch grabbed.
     */
    fun activeBordersForDragLayerPoint(
        dragLayerX: Int,
        dragLayerY: Int,
        frameLeft: Int,
        frameTop: Int,
        frameWidth: Int,
        frameHeight: Int,
        touchTargetWidth: Int,
    ): Borders = activeBorders(
        x = dragLayerX - frameLeft,
        y = dragLayerY - frameTop,
        width = frameWidth,
        height = frameHeight,
        touchTargetWidth = touchTargetWidth,
    )

    /**
     * Clamps the edge touch target so a small frame keeps a usable centre.
     *
     * The native frame uses a fixed 2 x backgroundPadding (48dp) target, which is fine for a
     * large widget. A Caddy folder can be a single cell (~100dp), where 48dp per side would
     * leave almost no free centre and every tap would read as a resize. The target is capped
     * at one third of the shorter side so the middle always stays free, while never dropping
     * below [minTouchTarget] so the handles remain reachable.
     */
    fun effectiveTouchTarget(
        width: Int,
        height: Int,
        desired: Int,
        minTouchTarget: Int,
    ): Int {
        if (width <= 0 || height <= 0) return minTouchTarget
        val cap = minOf(width, height) / 3
        return desired.coerceIn(minTouchTarget, maxOf(minTouchTarget, cap))
    }

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
