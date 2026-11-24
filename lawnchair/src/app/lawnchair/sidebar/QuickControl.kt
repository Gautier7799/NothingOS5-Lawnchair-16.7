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
 * The quick-control tiles the sidebar can show above its app column.
 *
 * Ported from the standalone "Samart sidebar" app (MIT), which draws the same tiles in an
 * overlay window. Here they live inside the sidebar panel, so they need no overlay service.
 */
enum class QuickControl(@StringRes val labelRes: Int) {
    TORCH(R.string.sidebar_quick_control_torch),
    AUTO_ROTATE(R.string.sidebar_quick_control_auto_rotate),
    AUTO_BRIGHTNESS(R.string.sidebar_quick_control_auto_brightness),
    DND(R.string.sidebar_quick_control_dnd),
    RINGER(R.string.sidebar_quick_control_ringer),
    QUICK_SHARE(R.string.sidebar_quick_control_quick_share),
    QR_SCANNER(R.string.sidebar_quick_control_qr_scanner),
    POWER_MENU(R.string.sidebar_quick_control_power_menu),
    SCREENSHOT(R.string.sidebar_quick_control_screenshot),
    ;

    companion object {
        /** Tiles in display order, as a fresh list so callers cannot mutate the shared one. */
        val defaultOrder: List<QuickControl> get() = entries.toList()
    }
}

/**
 * State arithmetic for the quick-control tiles, free of Android types so it runs on the JVM.
 *
 * The ringer cycle is the only non-trivial one. Silencing the phone needs notification-policy
 * access, so the cycle degrades to normal/vibrate when that access is missing instead of
 * flipping to silent and silently failing.
 */
object QuickControlState {

    /** Mirrors `AudioManager.RINGER_MODE_NORMAL` without importing Android. */
    const val RINGER_NORMAL = 0

    /** Mirrors `AudioManager.RINGER_MODE_VIBRATE`. */
    const val RINGER_VIBRATE = 1

    /** Mirrors `AudioManager.RINGER_MODE_SILENT`. */
    const val RINGER_SILENT = 2

    /**
     * The ringer mode one tap should move to.
     *
     * Normal goes to vibrate; vibrate goes to silent only when [policyAccessGranted], because
     * `AudioManager.ringerMode` ignores a silent request without it. Any other mode returns to
     * normal, which always works.
     */
    fun nextRingerMode(current: Int, policyAccessGranted: Boolean): Int = when (current) {
        RINGER_NORMAL -> RINGER_VIBRATE
        RINGER_VIBRATE -> if (policyAccessGranted) RINGER_SILENT else RINGER_NORMAL
        else -> RINGER_NORMAL
    }

    /** Whether a ringer mode counts as "not normal", which is what tints the tile. */
    fun isRingerActive(mode: Int): Boolean = mode != RINGER_NORMAL

    /** Short subtitle for a ringer mode. */
    fun ringerSubtitle(mode: Int): String = when (mode) {
        RINGER_SILENT -> "Silent"
        RINGER_VIBRATE -> "Vibrate"
        else -> "Normal"
    }

    /** Short subtitle for an on/off tile. */
    fun toggleSubtitle(active: Boolean): String = if (active) "On" else "Off"

    /**
     * Whether a tile can be toggled without asking for a permission first.
     *
     * Torch needs no permission (`CameraManager.setTorchMode` is app-scoped). The two
     * `Settings.System` tiles need [android.provider.Settings.System.canWrite]. Do-not-disturb
     * needs notification-policy access.
     */
    fun needsWriteSettings(control: QuickControl): Boolean = control == QuickControl.AUTO_ROTATE || control == QuickControl.AUTO_BRIGHTNESS

    /** Whether the control needs notification-policy access to reach its full state. */
    fun needsPolicyAccess(control: QuickControl): Boolean = control == QuickControl.DND

    /**
     * Whether the control is a one-shot action rather than an on/off state.
     *
     * Share, QR, power and screenshot fire once and have no state to show, so they never render
     * as "active" and never keep a subtitle.
     */
    fun isMomentary(control: QuickControl): Boolean = when (control) {
        QuickControl.QUICK_SHARE, QuickControl.QR_SCANNER,
        QuickControl.POWER_MENU, QuickControl.SCREENSHOT,
        -> true

        else -> false
    }

    /**
     * Whether a tile should look enabled.
     *
     * Torch, DND and the ringer are "active when not in their default state"; the two
     * `Settings.System` toggles are active when the setting is on.
     */
    fun isActive(control: QuickControl, currentState: Int): Boolean = when (control) {
        QuickControl.RINGER -> isRingerActive(currentState)

        // A momentary action has no persistent state, so it must never look toggled on.
        in MOMENTARY -> false

        else -> currentState != 0
    }

    private val MOMENTARY = listOf(
        QuickControl.QUICK_SHARE,
        QuickControl.QR_SCANNER,
        QuickControl.POWER_MENU,
        QuickControl.SCREENSHOT,
    )
}
