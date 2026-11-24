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

/**
 * Recognises a double tap on the sidebar handle.
 *
 * A tap carries no direction, so it cannot come from [SidebarGesture.classify]; it can only be
 * recognised by comparing two touch-up times. The clock is passed in rather than read from
 * `SystemClock` so the window arithmetic is testable on the JVM.
 *
 * The detector is stateful: feed it every tap-up and it reports whether that tap completed a
 * double tap. A tap that arrives too late starts a new pair instead of completing the old one,
 * which is what stops a user's two lazy taps minutes apart from firing a bound action.
 */
class DoubleTapDetector(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
) {

    private var lastTapAt: Long? = null

    /** Records a tap at [nowMillis] and returns whether it formed a double tap. */
    fun onTap(nowMillis: Long): Boolean {
        val previous = lastTapAt
        lastTapAt = nowMillis
        if (previous == null) return false
        val delta = nowMillis - previous
        // A negative delta means the clock went backwards; treat it as a fresh pair rather than
        // letting a stale value fire an action.
        if (delta < 0 || delta > windowMillis) return false
        lastTapAt = null
        return true
    }

    /** Clears the pending tap, so the next tap starts a new pair. */
    fun reset() {
        lastTapAt = null
    }

    companion object {
        /** Longest gap, in ms, between two taps that still counts as a double tap. */
        const val DEFAULT_WINDOW_MILLIS = 300L
    }
}
