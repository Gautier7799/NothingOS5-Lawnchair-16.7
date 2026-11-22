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

class DossierCreationTest {

    @Test
    fun shouldGroup_trueForAppOnApp() {
        assertThat(
            DossierCreation.shouldGroup(
                draggedIsApp = true,
                targetIsApp = true,
                targetIsDossier = false,
            ),
        ).isTrue()
    }

    @Test
    fun shouldGroup_falseWhenTargetIsAlreadyACard() {
        // Dropping an app onto an existing card is the add-to-card path, not a new card.
        assertThat(
            DossierCreation.shouldGroup(
                draggedIsApp = true,
                targetIsApp = false,
                targetIsDossier = true,
            ),
        ).isFalse()
    }

    @Test
    fun shouldGroup_falseWhenEitherSideIsNotAnApp() {
        assertThat(DossierCreation.shouldGroup(false, true, false)).isFalse()
        assertThat(DossierCreation.shouldGroup(true, false, false)).isFalse()
    }

    @Test
    fun shouldGroup_falseForInternalDropOnEmptyFloor() {
        // Dropping an app on an empty cell has no target app, so it is not a grouping
        // gesture and must fall through to the ordinary internal-drop path.
        assertThat(
            DossierCreation.shouldGroup(
                draggedIsApp = true,
                targetIsApp = false,
                targetIsDossier = false,
            ),
        ).isFalse()
    }

    @Test
    fun defaultTitle_usesFirstNonBlankLabel() {
        val title = DossierCreation.defaultTitle(listOf(null, "  ", "Maps", "Camera"))
        assertThat(title).isEqualTo("Maps")
    }

    @Test
    fun defaultTitle_fallsBackWhenNoLabelAvailable() {
        assertThat(DossierCreation.defaultTitle(listOf(null, ""))).isEqualTo(DossierCreation.DEFAULT_TITLE)
        assertThat(DossierCreation.defaultTitle(emptyList())).isEqualTo(DossierCreation.DEFAULT_TITLE)
    }
}
