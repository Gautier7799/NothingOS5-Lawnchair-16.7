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

package app.lawnchair.widgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Builds the pending intents that open a system app when a widget is tapped.
 *
 * Each widget maps to the system surface that shows the same data, so a tap takes the user to the
 * app that can act on what they just read. A specific package is preferred, but every entry falls
 * back to a settings page so the tap still does something sensible when the preferred app is not
 * installed.
 */
object SystemAppLauncher {

    /** Packages that show a step/activity history, in preference order. */
    private val HEALTH_PACKAGES = listOf(
        "com.google.android.apps.fitness",
        "com.samsung.android.app.shealth",
        "com.mi.health",
        "com.huawei.health",
        "com.garmin.android.apps.connectmobile",
    )

    /**
     * A pending intent that opens the user's activity/health app.
     *
     * Falls back to the system settings app so the tap never becomes a no-op on a device without a
     * known health app installed.
     */
    fun health(context: Context, requestCode: Int): PendingIntent = firstInstalled(HEALTH_PACKAGES) { isInstalled(context, it) }?.let {
        activity(context, launchIntent(context, it)!!, requestCode)
    } ?: activity(context, Intent(android.provider.Settings.ACTION_SETTINGS), requestCode)

    /**
     * The first package from [packages] that [isInstalled] accepts, or null when none is present.
     *
     * Split out from [health] so the preference order can be unit tested without a [Context].
     */
    fun firstInstalled(packages: List<String>, isInstalled: (String) -> Boolean): String? = packages.firstOrNull(isInstalled)

    private fun isInstalled(context: Context, packageName: String): Boolean = launchIntent(context, packageName) != null

    private fun launchIntent(context: Context, packageName: String): Intent? = context.packageManager.getLaunchIntentForPackage(packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun activity(context: Context, intent: Intent, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
