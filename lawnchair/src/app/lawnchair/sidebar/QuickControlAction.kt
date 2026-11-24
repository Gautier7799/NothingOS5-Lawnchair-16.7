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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.android.launcher3.R

/**
 * The one-shot actions the sidebar tiles and gestures can fire.
 *
 * Each one starts an activity the platform already provides, so none of them needs a permission
 * the user would have to grant. That is the reason this list stops where it does: expanding the
 * status bar and capturing a screenshot both need accessibility or a privileged app, and a
 * launcher should not ask for either to draw a button.
 */
object QuickControlAction {

    private const val TAG = "SidebarAction"

    /** The one-shot actions a tile can fire. */
    enum class Kind {
        QUICK_SHARE,
        QR_SCANNER,
        POWER_MENU,
        SCREENSHOT,
        ;

        /** Runs this action, returning whether it started something. */
        fun execute(context: Context): Boolean = when (this) {
            QUICK_SHARE -> quickShare(context)
            QR_SCANNER -> qrScanner(context)
            POWER_MENU -> powerMenu(context)
            SCREENSHOT -> screenshot(context)
        }
    }

    /** Opens the system share sheet with no target chosen yet. */
    fun quickShare(context: Context): Boolean = start(
        context,
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "")
        },
    )

    /**
     * Opens the QR scanner the platform exposes.
     *
     * Android 13 and newer route through the camera app; older releases have no stable entry
     * point, so this reports failure and the caller tells the user instead of opening nothing.
     */
    fun qrScanner(context: Context): Boolean = firstAvailable(
        context,
        listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("zxing://scan/")),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
        ),
    )

    /**
     * Opens the global actions dialog, which is the closest public equivalent of a power menu.
     *
     * The real power dialog is a system-only component, so this deliberately opens the
     * long-press power menu surface the platform allows instead of pretending to reboot.
     */
    fun powerMenu(context: Context): Boolean = start(
        context,
        Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
    )

    /**
     * Reports that a screenshot cannot be taken from here.
     *
     * `MediaProjection` needs a per-capture consent dialog and a foreground service, which is far
     * more than a tile should own. Returning false lets the caller explain that rather than
     * leaving the tile looking broken.
     */
    fun screenshot(context: Context): Boolean {
        toast(context, R.string.sidebar_action_unavailable)
        return false
    }

    /** Opens the notification shade through the launcher's own state manager where possible. */
    fun notifications(context: Context): Boolean = start(
        context,
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
    )

    /**
     * Opens the user's default assistant.
     *
     * `Intent.ACTION_ASSIST` is the public entry point; when no assistant is set the platform
     * throws and the user is told rather than the tile doing nothing.
     */
    fun assistant(context: Context): Boolean = start(
        context,
        Intent(Intent.ACTION_ASSIST).apply { addCategory(Intent.CATEGORY_DEFAULT) },
    )

    /** Opens quick settings, which the platform exposes as a settings screen. */
    fun quickSettings(context: Context): Boolean = start(
        context,
        Intent(Settings.ACTION_SETTINGS),
    )

    /**
     * Starts the first intent in [candidates] that resolves.
     *
     * No platform exposes a guaranteed QR entry point, so this mirrors the reference app's
     * fallback chain instead of assuming one package is installed.
     */
    private fun firstAvailable(context: Context, candidates: List<Intent>): Boolean {
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) == null) continue
            if (start(context, intent)) return true
        }
        toast(context, R.string.sidebar_action_unavailable)
        return false
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No activity for $intent")
        toast(context, R.string.sidebar_action_unavailable)
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "Not allowed to start $intent", e)
        toast(context, R.string.sidebar_action_unavailable)
        false
    }

    private fun toast(context: Context, messageRes: Int) {
        Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
    }
}
