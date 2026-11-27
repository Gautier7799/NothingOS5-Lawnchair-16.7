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

class PredictionBindingTest {

    private val a = Any()
    private val b = Any()

    @Test
    fun firstBindAlwaysApplies() {
        assertThat(PredictionBinding.shouldRebind(null, listOf(a))).isTrue()
    }

    @Test
    fun sameListInstanceIsSkipped() {
        val applied = listOf(a, b)

        assertThat(PredictionBinding.shouldRebind(applied, applied)).isFalse()
    }

    @Test
    fun snapshotDetectsInPlacePruning() {
        // The prediction container prunes its live list in place on uninstall. A snapshot taken at
        // bind time must notice, so the dock is rebound instead of keeping the stale icon.
        val live = mutableListOf(a, b)
        val snapshot = ArrayList(live)

        live.removeIf { it === a }

        assertThat(PredictionBinding.shouldRebind(snapshot, live)).isTrue()
    }

    @Test
    fun snapshotHidesNothingWhenListIsUnchanged() {
        val live = mutableListOf(a, b)
        val snapshot = ArrayList(live)

        assertThat(PredictionBinding.shouldRebind(snapshot, live)).isFalse()
    }

    @Test
    fun equalButRebuiltListIsSkipped() {
        // This is the resume path: the model hands back a fresh list with the same item objects.
        val applied = listOf(a, b)
        val next = ArrayList(applied)

        assertThat(applied).isNotSameInstanceAs(next)
        assertThat(PredictionBinding.shouldRebind(applied, next)).isFalse()
    }

    @Test
    fun newItemInstanceIsApplied() {
        // A rebuilt model means icons may have changed (icon pack, theme), so it must reach the dock.
        val applied = listOf<Any>(a, b)
        val next = listOf(Any(), b)

        assertThat(PredictionBinding.shouldRebind(applied, next)).isTrue()
    }

    @Test
    fun reorderedItemsAreApplied() {
        val applied = listOf(a, b)

        assertThat(PredictionBinding.shouldRebind(applied, listOf(b, a))).isTrue()
    }

    @Test
    fun differentSizeIsApplied() {
        val applied = listOf(a, b)

        assertThat(PredictionBinding.shouldRebind(applied, listOf(a))).isTrue()
        assertThat(PredictionBinding.shouldRebind(listOf(a), applied)).isTrue()
    }

    @Test
    fun emptyListIsSkippedOnlyOnceApplied() {
        assertThat(PredictionBinding.shouldRebind(null, emptyList<Any>())).isTrue()
        assertThat(PredictionBinding.shouldRebind(emptyList<Any>(), emptyList<Any>())).isFalse()
    }

    @Test
    fun missingNextListIsNeverApplied() {
        assertThat(PredictionBinding.shouldRebind(listOf(a), null)).isFalse()
    }
}
