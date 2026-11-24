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

package app.lawnchair.sidebar

import androidx.annotation.StringRes
import com.android.launcher3.R

/**
 * Gestures that can be bound to the sidebar handle.
 *
 * Ported from the standalone "Samart sidebar" app, which let the user pick an action for each
 * direction. The set is deliberately limited to what a launcher can do without a permission the
 * user would have to grant: no status-bar expansion (needs accessibility) and no app-drawer
 * overlay (the launcher already owns its own drawer).
 */
enum class SidebarGestureAction {
    /** Nothing happens. The default for every direction. */
    NONE,

    /** Opens the sidebar panel. */
    OPEN_PANEL,

    /** Opens the launcher's all-apps drawer. */
    OPEN_ALL_APPS,

    /** Opens the launcher's search UI. */
    OPEN_SEARCH,

    /** Launches the default assistant. */
    OPEN_ASSISTANT,

    /** Opens the notification shade. */
    OPEN_NOTIFICATIONS,

    /** Opens quick settings. */
    OPEN_QUICK_SETTINGS,

    /** Opens the notification shade via the platform's own shade intent. */
    OPEN_POWER_MENU,

    ;

    companion object {
        /** Actions offered in the settings screen, in display order. */
        val selectable: List<SidebarGestureAction> get() = entries.toList()
    }
}

/**
 * Which gesture a drag on the handle turned into.
 *
 * Kept Android-free so the arithmetic is unit tested. A drag has to pass a distance threshold
 * before it counts at all, which is what stops a tap or a jitter from firing an action.
 *
 * [DOUBLE_TAP] is not produced by [classify]: it comes from [DoubleTapDetector], because a tap
 * has no direction and can only be recognised over time.
 */
enum class SidebarGesture(@get:StringRes val labelRes: Int) {
    NONE(0),
    SWIPE_IN(R.string.sidebar_gesture_swipe_in_label),
    SWIPE_UP(R.string.sidebar_gesture_swipe_up_label),
    SWIPE_DOWN(R.string.sidebar_gesture_swipe_down_label),

    /** A second tap inside the double-tap window. Not produced by [classify]. */
    DOUBLE_TAP(R.string.sidebar_gesture_double_tap_label),
    ;

    companion object {
        /**
         * Classifies a drag of [dx], [dy] pixels.
         *
         * The larger axis wins, so a diagonal drag resolves to the direction the user actually
         * meant rather than whichever check ran first. [thresholdPx] guards against taps: a
         * drag shorter than it is [NONE].
         *
         * [onRightEdge] flips the horizontal sense, because "in" means leftwards from the right
         * edge and rightwards from the left edge.
         */
        fun classify(dx: Float, dy: Float, thresholdPx: Float, onRightEdge: Boolean): SidebarGesture {
            val ax = kotlin.math.abs(dx)
            val ay = kotlin.math.abs(dy)
            if (ax < thresholdPx && ay < thresholdPx) return NONE
            if (ax >= ay) {
                val inward = if (onRightEdge) dx < 0f else dx > 0f
                return if (inward) SWIPE_IN else NONE
            }
            return if (dy < 0f) SWIPE_UP else SWIPE_DOWN
        }

        /**
         * Converts a 0-100 sensitivity into a drag threshold.
         *
         * Higher sensitivity means a shorter drag already counts, so the threshold falls. The
         * range is deliberately narrow at both ends: at 0 a threshold so large the handle feels
         * dead, at 100 one so small that a shaky finger fires gestures.
         */
        fun thresholdForSensitivity(sensitivity: Int, basePx: Float): Float {
            val clamped = sensitivity.coerceIn(0, 100)
            val factor = 2f - (clamped / 100f) * 1.65f
            return basePx * factor
        }
    }
}

/**
 * The four gesture bindings, stored as a preference string.
 *
 * The format is `direction:ACTION,direction:ACTION` — for example
 * `swipe_in:OPEN_PANEL,swipe_up:OPEN_SEARCH`. Unknown directions and unknown actions are
 * dropped rather than crashing, because a preference file outlives any single build.
 */
object SidebarGestureBindings {

    /** Directions in the order they appear in the settings screen. */
    val directions: List<SidebarGesture> = listOf(
        SidebarGesture.SWIPE_IN,
        SidebarGesture.SWIPE_UP,
        SidebarGesture.SWIPE_DOWN,
        SidebarGesture.DOUBLE_TAP,
    )

    private const val SEPARATOR = ","
    private const val PAIR = ":"

    /** Defaults: swiping in opens the panel, everything else does nothing. */
    val defaults: Map<SidebarGesture, SidebarGestureAction> = mapOf(
        SidebarGesture.SWIPE_IN to SidebarGestureAction.OPEN_PANEL,
        SidebarGesture.SWIPE_UP to SidebarGestureAction.NONE,
        SidebarGesture.SWIPE_DOWN to SidebarGestureAction.NONE,
        SidebarGesture.DOUBLE_TAP to SidebarGestureAction.NONE,
    )

    /** Parses [saved] into a binding for every direction, falling back to [defaults]. */
    fun resolve(saved: String?): Map<SidebarGesture, SidebarGestureAction> {
        if (saved.isNullOrBlank()) return defaults
        val parsed = mutableMapOf<SidebarGesture, SidebarGestureAction>()
        saved.split(SEPARATOR).forEach { entry ->
            val parts = entry.split(PAIR)
            if (parts.size != 2) return@forEach
            val direction = directions.firstOrNull {
                it.name.equals(parts[0].trim(), ignoreCase = true)
            } ?: return@forEach
            val action = SidebarGestureAction.entries.firstOrNull {
                it.name.equals(parts[1].trim(), ignoreCase = true)
            } ?: return@forEach
            parsed[direction] = action
        }
        // A direction missing from a saved string was never configured, so it keeps its default
        // rather than silently becoming NONE.
        return directions.associateWith { parsed[it] ?: defaults[it] ?: SidebarGestureAction.NONE }
    }

    /** Serialises [bindings] for storage, always in direction order so the file is stable. */
    fun serialise(bindings: Map<SidebarGesture, SidebarGestureAction>): String = directions.joinToString(SEPARATOR) { direction ->
        "${direction.name.lowercase()}${PAIR}${bindings[direction] ?: SidebarGestureAction.NONE}"
    }

    /** The action bound to [gesture], or [SidebarGestureAction.NONE] when unbound. */
    fun actionFor(bindings: Map<SidebarGesture, SidebarGestureAction>, gesture: SidebarGesture): SidebarGestureAction = if (gesture == SidebarGesture.NONE) SidebarGestureAction.NONE else bindings[gesture] ?: SidebarGestureAction.NONE
}
