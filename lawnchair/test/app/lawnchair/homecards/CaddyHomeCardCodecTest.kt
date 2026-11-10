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

package app.lawnchair.homecards

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CaddyHomeCardCodecTest {

    private val sample = CaddyHomeCardInfo(
        id = 42,
        title = "Games",
        appKeys = listOf("com.a#0", "com.b#0"),
        screenId = 1,
        cellX = 2,
        cellY = 3,
        spanX = 3,
        spanY = 4,
    )

    @Test
    fun roundTrip_preservesEveryField() {
        val decoded = CaddyHomeCardCodec.decode(CaddyHomeCardCodec.encode(listOf(sample)))
        assertThat(decoded).hasSize(1)
        assertThat(decoded[0]).isEqualTo(sample)
    }

    @Test
    fun roundTrip_preservesOrder() {
        val second = sample.copy(id = 7, title = "Work")
        val decoded = CaddyHomeCardCodec.decode(
            CaddyHomeCardCodec.encode(listOf(sample, second)),
        )
        assertThat(decoded.map { it.id }).containsExactly(42, 7).inOrder()
    }

    @Test
    fun decode_legacySixFieldFormat_usesDefaultSpan() {
        val legacy = "42\tGames\tcom.a#0,com.b#0\t1\t2\t3"
        val decoded = CaddyHomeCardCodec.decode(legacy)
        assertThat(decoded).hasSize(1)
        assertThat(decoded[0].spanX).isEqualTo(2)
        assertThat(decoded[0].spanY).isEqualTo(2)
    }

    @Test
    fun decode_clampsInvalidSpans() {
        val invalid = "42\tGames\tcom.a#0\t1\t2\t3\t99\t0"
        val decoded = CaddyHomeCardCodec.decode(invalid)
        assertThat(decoded).hasSize(1)
        assertThat(decoded[0].spanX).isEqualTo(8)
        assertThat(decoded[0].spanY).isEqualTo(1)
    }

    @Test
    fun decode_nullOrEmpty_returnsEmptyList() {
        assertThat(CaddyHomeCardCodec.decode(null)).isEmpty()
        assertThat(CaddyHomeCardCodec.decode("")).isEmpty()
    }

    @Test
    fun decode_skipsMalformedLines() {
        val encoded = CaddyHomeCardCodec.encode(listOf(sample))
        val corrupted = "not-a-card\n$encoded\n"
        val decoded = CaddyHomeCardCodec.decode(corrupted)
        assertThat(decoded).hasSize(1)
        assertThat(decoded[0]).isEqualTo(sample)
    }

    @Test
    fun encode_escapesTabsAndNewlinesInTitle() {
        val tricky = sample.copy(title = "Tab\there\nnewline")
        val decoded = CaddyHomeCardCodec.decode(CaddyHomeCardCodec.encode(listOf(tricky)))
        assertThat(decoded).hasSize(1)
        assertThat(decoded[0].title).isEqualTo("Tab\there\nnewline")
    }

    @Test
    fun encode_escapesBackslashInTitle() {
        val tricky = sample.copy(title = "a\\b")
        val decoded = CaddyHomeCardCodec.decode(CaddyHomeCardCodec.encode(listOf(tricky)))
        assertThat(decoded[0].title).isEqualTo("a\\b")
    }

    @Test
    fun encode_emptyAppList_roundTrips() {
        val empty = sample.copy(appKeys = emptyList())
        val decoded = CaddyHomeCardCodec.decode(CaddyHomeCardCodec.encode(listOf(empty)))
        assertThat(decoded[0].appKeys).isEmpty()
    }

    @Test
    fun appCount_reflectsAssignedApps() {
        assertThat(sample.appCount).isEqualTo(2)
    }
}
