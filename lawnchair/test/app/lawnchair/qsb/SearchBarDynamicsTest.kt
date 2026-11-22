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

package app.lawnchair.qsb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchBarDynamicsTest {

    @Test
    fun `shows on the main page when dynamic is on`() {
        assertTrue(
            SearchBarDynamics.shouldShow(
                SearchBarState(dynamicEnabled = true, workspaceVisible = true, onDefaultPage = true),
            ),
        )
    }

    @Test
    fun `hides on a side page when dynamic is on`() {
        assertFalse(
            SearchBarDynamics.shouldShow(
                SearchBarState(dynamicEnabled = true, workspaceVisible = true, onDefaultPage = false),
            ),
        )
    }

    @Test
    fun `shows everywhere when dynamic is off`() {
        assertTrue(
            SearchBarDynamics.shouldShow(
                SearchBarState(dynamicEnabled = false, workspaceVisible = true, onDefaultPage = false),
            ),
        )
    }

    @Test
    fun `leaves non-workspace states untouched when dynamic is on`() {
        assertTrue(
            SearchBarDynamics.shouldShow(
                SearchBarState(dynamicEnabled = true, workspaceVisible = false, onDefaultPage = false),
            ),
        )
    }
}
