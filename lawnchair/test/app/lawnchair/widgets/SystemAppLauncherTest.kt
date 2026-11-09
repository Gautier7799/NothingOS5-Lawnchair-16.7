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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SystemAppLauncherTest {

    @Test
    fun firstInstalled_picksTheFirstPackageThatIsPresent() {
        val present = setOf("b")

        val result = SystemAppLauncher.firstInstalled(listOf("a", "b", "c")) { it in present }

        assertThat(result).isEqualTo("b")
    }

    @Test
    fun firstInstalled_prefersTheEarlierPackageWhenSeveralArePresent() {
        val present = setOf("com.google.android.apps.fitness", "com.mi.health")

        val result = SystemAppLauncher.firstInstalled(
            listOf("com.google.android.apps.fitness", "com.samsung.android.app.shealth", "com.mi.health"),
        ) { it in present }

        assertThat(result).isEqualTo("com.google.android.apps.fitness")
    }

    @Test
    fun firstInstalled_returnsNullWhenNothingIsInstalled() {
        val result = SystemAppLauncher.firstInstalled(listOf("a", "b")) { false }

        assertThat(result).isNull()
    }

    @Test
    fun firstInstalled_returnsNullForAnEmptyList() {
        val result = SystemAppLauncher.firstInstalled(emptyList()) { true }

        assertThat(result).isNull()
    }
}
