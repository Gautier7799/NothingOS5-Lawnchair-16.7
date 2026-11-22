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

package app.lawnchair.allapps.caddy

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * JVM tests for the Caddy card → native folder translation.
 *
 * These cover the pure part: which apps become the folder's contents, in what order,
 * and under what title. The Android folder creation itself is exercised on device.
 */
class CaddyFolderFactoryTest {

    @Test
    fun buildSpec_keepsEveryAppInOrder() {
        val spec = CaddyFolderFactory.buildSpec(
            "Games",
            listOf("0:com.a/.A", "0:com.b/.B", "0:com.c/.C"),
        )

        assertThat(spec).isNotNull()
        assertThat(spec!!.title).isEqualTo("Games")
        assertThat(spec.appKeys).containsExactly("0:com.a/.A", "0:com.b/.B", "0:com.c/.C")
            .inOrder()
    }

    @Test
    fun buildSpec_removesDuplicatesKeepingFirstSeenOrder() {
        val spec = CaddyFolderFactory.buildSpec(
            "Games",
            listOf("0:com.a/.A", "0:com.b/.B", "0:com.a/.A"),
        )

        assertThat(spec!!.appKeys).containsExactly("0:com.a/.A", "0:com.b/.B").inOrder()
    }

    @Test
    fun buildSpec_dropsBlankKeys() {
        val spec = CaddyFolderFactory.buildSpec(
            "Games",
            listOf("  ", "0:com.a/.A", "", "  0:com.b/.B  "),
        )

        assertThat(spec!!.appKeys).containsExactly("0:com.a/.A", "0:com.b/.B").inOrder()
    }

    @Test
    fun buildSpec_nullWhenNoUsableApp() {
        assertThat(CaddyFolderFactory.buildSpec("Games", emptyList())).isNull()
        assertThat(CaddyFolderFactory.buildSpec("Games", listOf("", "   "))).isNull()
        assertThat(CaddyFolderFactory.buildSpec("Games", null)).isNull()
    }

    @Test
    fun buildSpec_trimsCategoryTitle() {
        val spec = CaddyFolderFactory.buildSpec("  Games  ", listOf("0:com.a/.A"))

        assertThat(spec!!.title).isEqualTo("Games")
    }

    @Test
    fun buildSpec_fallsBackToEmptyTitleWhenCategoryUnnamed() {
        val spec = CaddyFolderFactory.buildSpec(null, listOf("0:com.a/.A"))

        assertThat(spec!!.title).isEmpty()
    }

    @Test
    fun normalizeTitle_truncatesOverlongTitle() {
        val long = "x".repeat(CaddyFolderFactory.MAX_TITLE_LENGTH + 20)

        val title = CaddyFolderFactory.normalizeTitle(long)

        assertThat(title.length).isEqualTo(CaddyFolderFactory.MAX_TITLE_LENGTH)
    }

    @Test
    fun normalizeTitle_usesFallbackWhenPrimaryBlank() {
        val title = CaddyFolderFactory.normalizeTitle("   ", "First App")

        assertThat(title).isEqualTo("First App")
    }
}
