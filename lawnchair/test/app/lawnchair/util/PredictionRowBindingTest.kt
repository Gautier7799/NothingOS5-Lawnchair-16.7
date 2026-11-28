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

package app.lawnchair.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PredictionRowBindingTest {

    private val keys = listOf("a@0", "b@0", "c@0")

    @Test
    fun firstBindRebinds() {
        assertThat(PredictionRowBinding.shouldRebind(null, keys, 4, 4)).isTrue()
    }

    @Test
    fun repeatOfSameKeysWithRightChildCountIsSkipped() {
        assertThat(PredictionRowBinding.shouldRebind(keys, keys, 4, 4)).isFalse()
    }

    @Test
    fun sameKeysDifferentSizeIsSkipped() {
        // Rebuilt lists holding equal keys are the common case on every resume.
        assertThat(PredictionRowBinding.shouldRebind(listOf("a@0"), listOf("a@0"), 4, 4)).isFalse()
    }

    @Test
    fun missingIconViewsRebind() {
        // A device-profile change drops the views, so the row must be rebuilt.
        assertThat(PredictionRowBinding.shouldRebind(keys, keys, 0, 4)).isTrue()
    }

    @Test
    fun extraIconViewsRebind() {
        assertThat(PredictionRowBinding.shouldRebind(keys, keys, 6, 4)).isTrue()
    }

    @Test
    fun changedOrderRebinds() {
        val reordered = listOf("b@0", "a@0", "c@0")

        assertThat(PredictionRowBinding.shouldRebind(keys, reordered, 4, 4)).isTrue()
    }

    @Test
    fun changedMembershipRebinds() {
        assertThat(PredictionRowBinding.shouldRebind(keys, listOf("a@0", "b@0", "d@0"), 4, 4))
            .isTrue()
    }

    @Test
    fun emptyPredictionsAfterNonEmptyRebinds() {
        assertThat(PredictionRowBinding.shouldRebind(keys, emptyList(), 4, 4)).isTrue()
    }

    @Test
    fun emptyToEmptyIsSkipped() {
        assertThat(PredictionRowBinding.shouldRebind(emptyList(), emptyList(), 4, 4)).isFalse()
    }

    @Test
    fun keyIsStableForSameInputs() {
        val first = PredictionRowBinding.key("pkg/.Main", 0, 12345, "Mail")

        assertThat(first).isEqualTo(PredictionRowBinding.key("pkg/.Main", 0, 12345, "Mail"))
    }

    @Test
    fun keyChangesWhenIconBitmapChanges() {
        // Switching icon pack keeps the component but replaces the bitmap.
        val before = PredictionRowBinding.key("pkg/.Main", 0, 111, "Mail")
        val after = PredictionRowBinding.key("pkg/.Main", 0, 222, "Mail")

        assertThat(before).isNotEqualTo(after)
    }

    @Test
    fun keyChangesWhenTitleChanges() {
        val before = PredictionRowBinding.key("pkg/.Main", 0, 111, "Mail")
        val after = PredictionRowBinding.key("pkg/.Main", 0, 111, "Mail Pro")

        assertThat(before).isNotEqualTo(after)
    }

    @Test
    fun keyDistinguishesUsers() {
        val owner = PredictionRowBinding.key("pkg/.Main", 0, 111, "Mail")
        val work = PredictionRowBinding.key("pkg/.Main", 10, 111, "Mail")

        assertThat(owner).isNotEqualTo(work)
    }

    @Test
    fun separatorInComponentCannotCollideWithAnotherApp() {
        // Without a length prefix, "a|1" + bitmap 2 would equal "a" + bitmap 1 + title "2|b".
        val first = PredictionRowBinding.key("a|1", 0, 2, null)
        val second = PredictionRowBinding.key("a", 0, 1, "2|b")

        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun nullTitleIsHandled() {
        assertThat(PredictionRowBinding.key("pkg", 0, 1, null))
            .isEqualTo(PredictionRowBinding.key("pkg", 0, 1, null))
    }
}
