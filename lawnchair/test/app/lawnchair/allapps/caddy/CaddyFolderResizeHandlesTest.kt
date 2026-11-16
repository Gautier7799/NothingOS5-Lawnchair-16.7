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

/**
 * JVM tests for which edge of the Caddy resize frame a touch is grabbing.
 */
class CaddyFolderResizeHandlesTest {

    private val width = 400
    private val height = 300
    private val target = 40

    @Test
    fun middleOfTheFrame_grabsNothing() {
        val borders = CaddyFolderResizeHandles.activeBorders(
            x = 200, y = 150, width = width, height = height, touchTargetWidth = target,
        )

        assertThat(borders.any).isFalse()
    }

    @Test
    fun nearLeftEdge_grabsLeftOnly() {
        val borders = CaddyFolderResizeHandles.activeBorders(
            x = 10, y = 150, width = width, height = height, touchTargetWidth = target,
        )

        assertThat(borders.left).isTrue()
        assertThat(borders.right).isFalse()
        assertThat(borders.top).isFalse()
        assertThat(borders.bottom).isFalse()
    }

    @Test
    fun nearBottomRightCorner_grabsBothEdges() {
        val borders = CaddyFolderResizeHandles.activeBorders(
            x = width - 5, y = height - 5, width = width, height = height, touchTargetWidth = target,
        )

        assertThat(borders.right).isTrue()
        assertThat(borders.bottom).isTrue()
        assertThat(borders.left).isFalse()
        assertThat(borders.top).isFalse()
    }

    @Test
    fun nearTopLeftCorner_grabsBothEdges() {
        val borders = CaddyFolderResizeHandles.activeBorders(
            x = 1, y = 1, width = width, height = height, touchTargetWidth = target,
        )

        assertThat(borders.left).isTrue()
        assertThat(borders.top).isTrue()
    }
}
