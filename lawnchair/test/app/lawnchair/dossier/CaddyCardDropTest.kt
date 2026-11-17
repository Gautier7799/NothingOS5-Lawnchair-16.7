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

/**
 * JVM unit tests for the Caddy-card-to-Dossier drop decisions.
 *
 * These cover the pure span/eligibility rules only. They do not exercise the Android
 * workspace drop path or the Dossier view.
 */
class CaddyCardDropTest {

    /** A vacancy function over an explicit set of occupied cells. */
    private fun occupied(vararg cells: Pair<Int, Int>): DossierPlacement.RegionVacancy {
        val blocked = cells.toSet()
        return DossierPlacement.RegionVacancy { x, y, spanX, spanY ->
            (x until x + spanX).all { cx ->
                (y until y + spanY).none { cy -> blocked.contains(cx to cy) }
            }
        }
    }

    @Test
    fun canCreate_falseWithoutApps() {
        assertThat(CaddyCardDrop.canCreate(0)).isFalse()
    }

    @Test
    fun canCreate_trueWithAtLeastOneApp() {
        assertThat(CaddyCardDrop.canCreate(1)).isTrue()
        assertThat(CaddyCardDrop.canCreate(7)).isTrue()
    }

    @Test
    fun resolveSpan_prefersFullTwoByTwoWhenFree() {
        val span = CaddyCardDrop.resolveSpan(0, 0, 4, 4, occupied())
        assertThat(span).isNotNull()
        assertThat(span!![0]).isEqualTo(CaddyCardDrop.DEFAULT_SPAN_X)
        assertThat(span[1]).isEqualTo(CaddyCardDrop.DEFAULT_SPAN_Y)
    }

    @Test
    fun resolveSpan_shrinksToFitWhenTwoByTwoIsBlocked() {
        // (1,1) is occupied, so a 2x2 starting at (0,0) is impossible; the card must
        // shrink rather than refuse the drop.
        val span = CaddyCardDrop.resolveSpan(0, 0, 4, 4, occupied(1 to 1))
        assertThat(span).isNotNull()
        assertThat(span!![0] * span[1]).isLessThan(
            CaddyCardDrop.DEFAULT_SPAN_X * CaddyCardDrop.DEFAULT_SPAN_Y,
        )
    }

    @Test
    fun resolveSpan_clampsToGridBoundsAtBottomRight() {
        // Dropped in the last cell of a 4x4 grid: only 1x1 fits.
        val span = CaddyCardDrop.resolveSpan(3, 3, 4, 4, occupied())
        assertThat(span).isNotNull()
        assertThat(span!![0]).isEqualTo(1)
        assertThat(span[1]).isEqualTo(1)
    }

    @Test
    fun resolveSpan_nullWhenNoCellIsFree() {
        // Every cell occupied: no card can be placed, the caller must not create one.
        val blocked = (0 until 3).flatMap { x -> (0 until 3).map { y -> x to y } }
        val span = CaddyCardDrop.resolveSpan(0, 0, 3, 3, occupied(*blocked.toTypedArray()))
        assertThat(span).isNull()
    }
}
