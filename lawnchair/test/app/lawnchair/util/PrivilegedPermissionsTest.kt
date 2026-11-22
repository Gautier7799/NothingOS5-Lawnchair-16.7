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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Covers the guards that keep AOSP launcher code from calling framework APIs this build
 * cannot reach.
 *
 * Both guarded APIs are imported from AOSP, where the launcher carries the platform
 * signature. Lawnchair is a normal app, so the same code paths are reachable but the
 * precondition is not. These tests pin the boundary values where each guard flips.
 */
class PrivilegedPermissionsTest {

    @Test
    fun canResetColorResources_isFalseBelowApi31() {
        // minSdk is 26; on these levels AppWidgetHostView has no resetColorResources and
        // calling it throws NoSuchMethodError.
        assertThat(PrivilegedPermissions.canResetColorResources(26)).isFalse()
        assertThat(PrivilegedPermissions.canResetColorResources(30)).isFalse()
    }

    @Test
    fun canResetColorResources_isTrueFromApi31() {
        assertThat(PrivilegedPermissions.canResetColorResources(31)).isTrue()
        assertThat(PrivilegedPermissions.canResetColorResources(34)).isTrue()
    }

    @Test
    fun canResetColorResources_matchesTheDeclaredApiLevel() {
        val boundary = PrivilegedPermissions.RESET_COLOR_RESOURCES_API
        assertThat(PrivilegedPermissions.canResetColorResources(boundary - 1)).isFalse()
        assertThat(PrivilegedPermissions.canResetColorResources(boundary)).isTrue()
    }

    @Test
    fun canStartContextualSearch_requiresBothFeatureAndPermission() {
        assertThat(
            PrivilegedPermissions.canStartContextualSearch(
                hasSystemFeature = true,
                permissionGranted = true,
            ),
        ).isTrue()
    }

    @Test
    fun canStartContextualSearch_failsWithoutThePrivilegedPermission() {
        // This is the Lawnchair case: the device advertises contextual search, but a
        // non-platform build never holds ACCESS_CONTEXTUAL_SEARCH.
        assertThat(
            PrivilegedPermissions.canStartContextualSearch(
                hasSystemFeature = true,
                permissionGranted = false,
            ),
        ).isFalse()
    }

    @Test
    fun canStartContextualSearch_failsWithoutTheSystemFeature() {
        assertThat(
            PrivilegedPermissions.canStartContextualSearch(
                hasSystemFeature = false,
                permissionGranted = true,
            ),
        ).isFalse()
    }

    @Test
    fun canStartContextualSearch_failsWhenNeitherIsPresent() {
        assertThat(
            PrivilegedPermissions.canStartContextualSearch(
                hasSystemFeature = false,
                permissionGranted = false,
            ),
        ).isFalse()
    }
}
