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

package app.lawnchair.icons

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * Pins the themed-icon preference to a single writer: the settings screen.
 *
 * A stale 12 Alpha 4 -> 5 migration in `LawnchairLauncher#onCreate` reset the preference to
 * `false` whenever `queryIntentActivityOptions` found no icon pack advertising
 * `app.lawnchair.icons.THEMED_ICON`. Lawnchair never declares that activity, so the query was
 * always empty and the reset fired on every launch, which looked like themed icons reverting to
 * the original app colours after a restart. The guard lives inside an Android activity and
 * cannot run on the JVM, so this test asserts on the source shape instead.
 */
class ThemedIconsPreferenceTest {

    private fun sourceFile(relativePath: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "lawnchair/")) {
                val candidate = File(dir, prefix + relativePath)
                if (candidate.isFile) return candidate
            }
            dir = dir.parentFile
        }
        throw AssertionError("could not locate $relativePath from ${File(".").absolutePath}")
    }

    private fun text(relativePath: String) = sourceFile(relativePath).readText()

    @Test
    fun launcherDoesNotResetThemedIconsOnStartup() {
        val source = text("lawnchair/src/app/lawnchair/LawnchairLauncher.kt")
        assertThat(source).doesNotContain("themedIcons.set(")
        assertThat(source).doesNotContain("getThemedIconPacksInstalled")
    }

    @Test
    fun iconPackQueryIsGone() {
        // The helper only ever resolved an activity Lawnchair does not declare, so keeping it
        // around invites another launch-time reset.
        val source = text("lawnchair/src/app/lawnchair/util/PackageManagerExtensions.kt")
        assertThat(source).doesNotContain("getThemedIconPacksInstalled")
    }

    @Test
    fun settingsScreenRemainsTheOnlyWriter() {
        val settings =
            text("lawnchair/src/app/lawnchair/ui/preferences/destinations/icons/ThemedIconsPreferences.kt")
        assertThat(settings).contains("themedIconsAdapter.onChange(newValue = state.themedIcons)")
    }
}
