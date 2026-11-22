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

package app.lawnchair.predictions

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PredictionOrderingTest {

    @Test
    fun stableFallback_returnsSameOrderForSameInput() {
        val keys = listOf("b/a/0", "a/b/0", "c/d/0")
        assertThat(PredictionOrdering.stableFallback(keys))
            .isEqualTo(PredictionOrdering.stableFallback(keys))
    }

    @Test
    fun stableFallback_isOrderIndependent() {
        val first = listOf("b/a/0", "a/b/0", "c/d/0")
        val second = listOf("c/d/0", "b/a/0", "a/b/0")
        assertThat(PredictionOrdering.stableFallback(first))
            .isEqualTo(PredictionOrdering.stableFallback(second))
    }

    @Test
    fun stableFallback_sortsAscending() {
        assertThat(PredictionOrdering.stableFallback(listOf("z", "a", "m")))
            .containsExactly("a", "m", "z")
            .inOrder()
    }

    @Test
    fun stableFallback_emptyInputReturnsEmpty() {
        assertThat(PredictionOrdering.stableFallback(emptyList())).isEmpty()
    }

    @Test
    fun keysDiffer_sameOrderIsStable() {
        val keys = listOf("a", "b", "c")
        assertThat(PredictionOrdering.keysDiffer(keys, keys)).isFalse()
    }

    @Test
    fun keysDiffer_detectsReorder() {
        assertThat(
            PredictionOrdering.keysDiffer(listOf("a", "b", "c"), listOf("c", "b", "a")),
        ).isTrue()
    }

    @Test
    fun keysDiffer_detectsSizeChange() {
        assertThat(PredictionOrdering.keysDiffer(listOf("a", "b"), listOf("a", "b", "c")))
            .isTrue()
    }

    @Test
    fun keysDiffer_emptyListsAreEqual() {
        assertThat(PredictionOrdering.keysDiffer(emptyList(), emptyList())).isFalse()
    }

    @Test
    fun rankByScoreDescending_ordersByCountThenKey() {
        val scores = linkedMapOf("b" to 2, "a" to 2, "c" to 5)
        assertThat(PredictionOrdering.rankByScoreDescending(scores))
            .containsExactly("c", "a", "b")
            .inOrder()
    }

    @Test
    fun rankByScoreDescending_isOrderIndependentForTies() {
        val first = linkedMapOf("b" to 1, "a" to 1, "d" to 1)
        val second = linkedMapOf("d" to 1, "b" to 1, "a" to 1)
        assertThat(PredictionOrdering.rankByScoreDescending(first))
            .isEqualTo(PredictionOrdering.rankByScoreDescending(second))
    }

    @Test
    fun rankByScoreDescending_emptyInputReturnsEmpty() {
        assertThat(PredictionOrdering.rankByScoreDescending(emptyMap())).isEmpty()
    }
}
