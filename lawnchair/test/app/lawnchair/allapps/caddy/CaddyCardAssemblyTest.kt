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

class CaddyCardAssemblyTest {

    @Test
    fun assemble_trimsTitleAndKeepsKeys() {
        val spec = CaddyCardAssembly.assemble("  Social  ", listOf("a", "b"))
        assertThat(spec).isNotNull()
        assertThat(spec!!.title).isEqualTo("Social")
        assertThat(spec.appKeys).containsExactly("a", "b").inOrder()
    }

    @Test
    fun assemble_rejectsBlankTitle() {
        assertThat(CaddyCardAssembly.assemble("   ", listOf("a"))).isNull()
        assertThat(CaddyCardAssembly.assemble(null, listOf("a"))).isNull()
    }

    @Test
    fun assemble_rejectsEmptyAppList() {
        assertThat(CaddyCardAssembly.assemble("Social", emptyList())).isNull()
        assertThat(CaddyCardAssembly.assemble("Social", null)).isNull()
    }

    @Test
    fun assemble_removesBlankAndDuplicateKeysKeepingOrder() {
        val spec = CaddyCardAssembly.assemble("Social", listOf("a", "b", "a", "  ", "b", "c"))
        assertThat(spec!!.appKeys).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun mergeInto_appendsNewCard() {
        val existing = listOf(CaddyCardSpec("Social", listOf("a", "b")))
        val merged = CaddyCardAssembly.mergeInto(existing, CaddyCardSpec("Tools", listOf("c")))
        assertThat(merged.map { it.title }).containsExactly("Social", "Tools").inOrder()
    }

    @Test
    fun mergeInto_replacesSameTitleInPlaceWithoutDuplicating() {
        val existing = listOf(
            CaddyCardSpec("Social", listOf("a", "b")),
            CaddyCardSpec("Tools", listOf("c")),
        )
        val merged = CaddyCardAssembly.mergeInto(existing, CaddyCardSpec("Social", listOf("x", "y")))
        assertThat(merged.map { it.title }).containsExactly("Social", "Tools").inOrder()
        assertThat(merged.first().appKeys).containsExactly("x", "y").inOrder()
    }

    @Test
    fun assembleAndMerge_buildsCardFromScratch() {
        // A card built from a raw app pick: title + arbitrary apps.
        val merged = CaddyCardAssembly.assembleAndMerge(
            existing = emptyList(),
            title = "Productivity",
            appKeys = listOf("com.x", "com.y", "com.x"),
        )
        assertThat(merged).isNotNull()
        assertThat(merged!!.single().title).isEqualTo("Productivity")
        assertThat(merged.single().appKeys).containsExactly("com.x", "com.y").inOrder()
    }

    @Test
    fun assembleAndMerge_invalidInputLeavesExistingUntouched() {
        val existing = listOf(CaddyCardSpec("Social", listOf("a")))
        assertThat(CaddyCardAssembly.assembleAndMerge(existing, "  ", listOf("b"))).isNull()
        assertThat(existing).hasSize(1)
    }
}
