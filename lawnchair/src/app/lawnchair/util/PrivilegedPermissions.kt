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

package app.lawnchair.util

/**
 * Guards for framework APIs that are only reachable with a privileged permission.
 *
 * Lawnchair is imported from AOSP, where the launcher ships with the platform signature and
 * holds permissions such as `ACCESS_CONTEXTUAL_SEARCH`. Installed as a normal app it holds
 * none of them, so those calls throw [SecurityException] or [NoSuchMethodError] at runtime.
 * Every such call site has to ask these questions first.
 *
 * The decisions are pure so they can be tested on the JVM, where `Build.VERSION.SDK_INT`
 * cannot be varied.
 */
object PrivilegedPermissions {

    /** API level that introduced `AppWidgetHostView.resetColorResources`. */
    const val RESET_COLOR_RESOURCES_API = 31

    /**
     * Whether `AppWidgetHostView.resetColorResources` may be called.
     *
     * The method does not exist before API 31, so invoking it on an older device throws
     * NoSuchMethodError. minSdk is 26, so this is reachable on real devices.
     */
    fun canResetColorResources(sdkInt: Int): Boolean = sdkInt >= RESET_COLOR_RESOURCES_API

    /**
     * Whether a contextual search call may proceed.
     *
     * Both conditions matter: the feature flag says the device supports the surface, the
     * permission says this build is allowed to start it. AOSP's launcher passes both;
     * Lawnchair passes the first and fails the second.
     */
    fun canStartContextualSearch(
        hasSystemFeature: Boolean,
        permissionGranted: Boolean,
    ): Boolean = hasSystemFeature && permissionGranted
}
