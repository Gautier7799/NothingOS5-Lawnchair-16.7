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

class DossierCardCodecTest {

    @Test
    fun roundTrip_preservesEveryField() {
        val cards = listOf(
            DossierCardInfo(
                id = 7,
                title = "Games",
                appKeys = listOf("0:com.a/.A", "0:com.b/.B"),
                screenId = 2,
                cellX = 1,
                cellY = 3,
                spanX = 3,
                spanY = 4,
            ),
            DossierCardInfo(id = 9, title = "Other", appKeys = emptyList()),
        )

        val decoded = DossierCardCodec.decode(DossierCardCodec.encode(cards))

        assertThat(decoded).isEqualTo(cards)
    }

    @Test
    fun encodeEscapesTabAndNewlineInTitle() {
        val card = DossierCardInfo(id = 1, title = "a\tb\nc\\d", appKeys = listOf("0:x/.Y"))

        val decoded = DossierCardCodec.decode(DossierCardCodec.encode(listOf(card)))

        assertThat(decoded.single().title).isEqualTo("a\tb\nc\\d")
        assertThat(decoded.single().appKeys).containsExactly("0:x/.Y")
    }

    @Test
    fun decode_nullOrEmptyYieldsEmptyList() {
        assertThat(DossierCardCodec.decode(null)).isEmpty()
        assertThat(DossierCardCodec.decode("")).isEmpty()
    }

    @Test
    fun decode_skipsMalformedLinesWithoutThrowing() {
        val good = DossierCardCodec.encode(
            listOf(DossierCardInfo(id = 5, title = "Tools", appKeys = listOf("0:t/.T"))),
        )
        val corrupt = "not-a-card\n" + good + "\n1\t2\t3"

        val decoded = DossierCardCodec.decode(corrupt)

        assertThat(decoded).hasSize(1)
        assertThat(decoded.single().id).isEqualTo(5)
    }

    @Test
    fun decode_clampsOutOfRangeSpans() {
        // 99 columns / 0 rows are both outside the supported range.
        val line = "1\tBig\t0:a/.A\t0\t0\t0\t99\t0"

        val card = DossierCardCodec.decode(line).single()

        assertThat(card.spanX).isEqualTo(DossierCardInfo.MAX_SPAN)
        assertThat(card.spanY).isEqualTo(DossierCardInfo.MIN_SPAN)
    }
}
