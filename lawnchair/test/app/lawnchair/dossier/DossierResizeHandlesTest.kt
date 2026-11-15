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

class DossierResizeHandlesTest {

    // 200x200 frame, 20px touch target on each edge.
    private val size = 200
    private val target = 20

    private fun borders(x: Int, y: Int) =
        DossierResizeHandles.activeBorders(x, y, size, size, target)

    @Test
    fun centerPointGrabsNoBorder() {
        val result = borders(100, 100)
        assertThat(result.any).isFalse()
        assertThat(result.left).isFalse()
        assertThat(result.right).isFalse()
        assertThat(result.top).isFalse()
        assertThat(result.bottom).isFalse()
    }

    @Test
    fun leftEdgeGrabsLeftOnly() {
        val result = borders(5, 100)
        assertThat(result.left).isTrue()
        assertThat(result.right).isFalse()
        assertThat(result.any).isTrue()
    }

    @Test
    fun rightEdgeGrabsRightOnly() {
        val result = borders(195, 100)
        assertThat(result.right).isTrue()
        assertThat(result.left).isFalse()
    }

    @Test
    fun topEdgeGrabsTopOnly() {
        val result = borders(100, 5)
        assertThat(result.top).isTrue()
        assertThat(result.bottom).isFalse()
    }

    @Test
    fun bottomEdgeGrabsBottomOnly() {
        val result = borders(100, 195)
        assertThat(result.bottom).isTrue()
        assertThat(result.top).isFalse()
    }

    @Test
    fun cornerGrabsBothEdges() {
        val result = borders(5, 5)
        assertThat(result.left).isTrue()
        assertThat(result.top).isTrue()
        assertThat(result.any).isTrue()
    }

    @Test
    fun boundaryIsExclusiveAtTouchTargetWidth() {
        // The comparison is strict, so exactly touchTargetWidth is already the inner region.
        val result = borders(target, target)
        assertThat(result.any).isFalse()
    }
}
