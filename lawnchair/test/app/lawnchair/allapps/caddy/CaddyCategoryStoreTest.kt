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

class CaddyCategoryStoreTest {

    @Test
    fun encodeDecode_roundTripsCardsInOrder() {
        val cards = listOf(
            CaddyCardSpec("Social", listOf("a", "b")),
            CaddyCardSpec("Tools", listOf("c")),
        )
        val decoded = CaddyCategoryStore.decode(CaddyCategoryStore.encode(cards))
        assertThat(decoded.map { it.title }).containsExactly("Social", "Tools").inOrder()
        assertThat(decoded[0].appKeys).containsExactly("a", "b").inOrder()
        assertThat(decoded[1].appKeys).containsExactly("c").inOrder()
    }

    @Test
    fun decode_emptyOrBlankReturnsEmpty() {
        assertThat(CaddyCategoryStore.decode(null)).isEmpty()
        assertThat(CaddyCategoryStore.decode("")).isEmpty()
        assertThat(CaddyCategoryStore.decode("[]")).isEmpty()
    }

    @Test
    fun decode_skipsMalformedEntriesButKeepsValidOnes() {
        val json = """
            [
              {"title": "Social", "apps": ["a", "b"]},
              {"title": "   ", "apps": ["c"]},
              {"apps": ["d"]},
              {"title": "Tools", "apps": []}
            ]
        """.trimIndent()
        val decoded = CaddyCategoryStore.decode(json)
        assertThat(decoded.map { it.title }).containsExactly("Social")
    }

    @Test
    fun decode_invalidJsonReturnsEmptyWithoutThrowing() {
        assertThat(CaddyCategoryStore.decode("not json at all")).isEmpty()
    }

    @Test
    fun decode_entryWithNonPrimitiveTitleIsSkippedWithoutThrowing() {
        // A hand-edited/older value could store an object where a string is expected.
        // decode must not throw: it runs on the main thread whenever the drawer opens.
        val json = """
            [
              {"title": {"nested": 1}, "apps": ["a"]},
              {"title": "Social", "apps": ["b"]}
            ]
        """.trimIndent()
        val decoded = CaddyCategoryStore.decode(json)
        assertThat(decoded.map { it.title }).containsExactly("Social")
    }

    @Test
    fun decode_entryWithNonPrimitiveAppKeyIsSkippedWithoutThrowing() {
        val json = """
            [
              {"title": "Social", "apps": [{"bad": true}, "b"]},
              {"title": "Tools", "apps": ["c"]}
            ]
        """.trimIndent()
        val decoded = CaddyCategoryStore.decode(json)
        assertThat(decoded.map { it.title }).containsExactly("Social", "Tools").inOrder()
        assertThat(decoded.first().appKeys).containsExactly("b")
    }

    @Test
    fun decode_entryWhoseAppsIsNotAnArrayIsSkipped() {
        val json = """
            [
              {"title": "Social", "apps": "oops"},
              {"title": "Tools", "apps": ["c"]}
            ]
        """.trimIndent()
        val decoded = CaddyCategoryStore.decode(json)
        assertThat(decoded.map { it.title }).containsExactly("Tools")
    }

    @Test
    fun decode_nonArrayRootReturnsEmptyWithoutThrowing() {
        assertThat(CaddyCategoryStore.decode("{\"title\":\"x\"}")).isEmpty()
        assertThat(CaddyCategoryStore.decode("42")).isEmpty()
    }

    @Test
    fun encode_producesJsonReadableByTheLoaderContract() {
        val encoded = CaddyCategoryStore.encode(listOf(CaddyCardSpec("Social", listOf("a"))))
        assertThat(encoded).contains("\"title\"")
        assertThat(encoded).contains("\"apps\"")
    }
}
