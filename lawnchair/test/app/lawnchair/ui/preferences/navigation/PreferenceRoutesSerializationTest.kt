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

package app.lawnchair.ui.preferences.navigation

import app.lawnchair.ui.preferences.components.search.SearchProviderId
import kotlinx.serialization.serializer
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards against a missing `@Serializable` on a preference route.
 *
 * `navDeepLink<T>(basePath = ...)` calls `serializer<T>()` while the navigation graph is built, so
 * a route without the annotation crashes the whole Settings screen with
 * `SerializationException: Serializer for class '...' is not found`. That is not hypothetical:
 * inserting a new route between the `@Serializable` annotation and the route it belonged to
 * stripped the annotation off `AppDrawerFolder` and made Settings unusable.
 *
 * Resolving the serializer for every route here turns that class of mistake into a unit test
 * failure instead of a crash on the device.
 */
class PreferenceRoutesSerializationTest {

    @Test
    fun everyDeepLinkRouteHasASerializer() {
        val routes: List<PreferenceDeepLink> = listOf(
            General,
            HomeScreen,
            Dock,
            AppDrawer,
            Folders,
            Quickstep,
            BackupAndRestore,
            Gestures,
            Smartspace,
            About,
            ExperimentalFeatures,
            Predictions,
            AppDrawerHiddenApps,
            AppDrawerFolder,
            GeneralIconPack,
            GeneralThemedIcons,
            GeneralAdvancedIcons,
            DockSearchProvider,
            SearchProviderPreference(SearchProviderId.entries.first()),
        )

        val missing = routes.filter { route ->
            runCatching { serializer(route::class.java).descriptor.serialName }.isFailure
        }

        assertTrue(
            "these routes are used as deep links but have no serializer: " +
                missing.joinToString { it::class.java.simpleName },
            missing.isEmpty(),
        )
    }

    @Test
    fun appDrawerFolderIsSerializable() {
        val name = serializer(AppDrawerFolder::class.java).descriptor.serialName
        assertTrue("AppDrawerFolder must stay serializable", name.isNotEmpty())
    }
}
