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

class CaddyCategoryEngineTest {

    /** Stand-in for a launcher AppInfo: deliberately has no equals/hashCode. */
    private class PlainApp(val key: String)

    @Test
    fun sameByKey_identityIndependentContentsAreSame() {
        val first = listOf(PlainApp("a"), PlainApp("b"))
        val second = listOf(PlainApp("a"), PlainApp("b"))
        assertThat(CaddyCategoryEngine.sameByKey(first, second) { it.key }).isTrue()
    }

    @Test
    fun sameByKey_detectsDifferentKey() {
        val first = listOf(PlainApp("a"), PlainApp("b"))
        val second = listOf(PlainApp("a"), PlainApp("c"))
        assertThat(CaddyCategoryEngine.sameByKey(first, second) { it.key }).isFalse()
    }

    @Test
    fun sameByKey_detectsReorder() {
        val first = listOf(PlainApp("a"), PlainApp("b"))
        val second = listOf(PlainApp("b"), PlainApp("a"))
        assertThat(CaddyCategoryEngine.sameByKey(first, second) { it.key }).isFalse()
    }

    @Test
    fun sameByKey_detectsSizeChange() {
        val first = listOf(PlainApp("a"))
        val second = listOf(PlainApp("a"), PlainApp("b"))
        assertThat(CaddyCategoryEngine.sameByKey(first, second) { it.key }).isFalse()
    }

    @Test
    fun sameByKey_bothNullIsSame() {
        assertThat(CaddyCategoryEngine.sameByKey<PlainApp>(null, null) { it.key }).isTrue()
    }

    @Test
    fun sameByKey_oneNullIsDifferent() {
        assertThat(
            CaddyCategoryEngine.sameByKey(listOf(PlainApp("a")), null) { it.key },
        ).isFalse()
    }

    @Test
    fun sameByKey_emptyListsAreSame() {
        assertThat(
            CaddyCategoryEngine.sameByKey(emptyList<PlainApp>(), emptyList<PlainApp>()) { it.key },
        ).isTrue()
    }
}
