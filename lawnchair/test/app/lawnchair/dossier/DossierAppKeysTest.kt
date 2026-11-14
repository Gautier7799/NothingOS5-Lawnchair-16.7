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

package app.lawnchair.dossier

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DossierAppKeysTest {

    @Test
    fun add_appendsNewKeyAndReportsChange() {
        val keys = mutableListOf("0:com.a/.A")
        val changed = DossierAppKeys.add(keys, "0:com.b/.B")
        assertThat(changed).isTrue()
        assertThat(keys).containsExactly("0:com.a/.A", "0:com.b/.B").inOrder()
    }

    @Test
    fun add_ignoresDuplicateKey() {
        val keys = mutableListOf("0:com.a/.A")
        val changed = DossierAppKeys.add(keys, "0:com.a/.A")
        assertThat(changed).isFalse()
        assertThat(keys).containsExactly("0:com.a/.A")
    }

    @Test
    fun add_trimsSurroundingWhitespace() {
        val keys = mutableListOf<String>()
        val changed = DossierAppKeys.add(keys, "  0:com.a/.A  ")
        assertThat(changed).isTrue()
        assertThat(keys).containsExactly("0:com.a/.A")
    }

    @Test
    fun add_rejectsBlankAndNull() {
        val keys = mutableListOf("0:com.a/.A")
        assertThat(DossierAppKeys.add(keys, "")).isFalse()
        assertThat(DossierAppKeys.add(keys, "   ")).isFalse()
        assertThat(DossierAppKeys.add(keys, null)).isFalse()
        assertThat(keys).containsExactly("0:com.a/.A")
    }
}
