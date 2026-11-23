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

package app.lawnchair.ui.dock

import com.android.launcher3.views.BaseDragLayer
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the layout-params type contract for views hosted directly in the DragLayer.
 *
 * `BaseDragLayer` extends `InsettableFrameLayout`, whose `setFrameLayoutChildInsets` casts every
 * child's params to `InsettableFrameLayout.LayoutParams`. A child added with a plain
 * `FrameLayout.LayoutParams` therefore crashes the launcher during inset dispatch:
 *
 * ```
 * java.lang.ClassCastException: android.widget.FrameLayout$LayoutParams cannot be cast to
 *     com.android.launcher3.InsettableFrameLayout$LayoutParams
 *   at com.android.launcher3.InsettableFrameLayout.setFrameLayoutChildInsets
 * ```
 *
 * `DragLayer.addView(view)` without explicit params is safe, because `ViewGroup` then asks
 * `generateDefaultLayoutParams()`, which returns the DragLayer's own type. Assigning
 * `view.layoutParams = FrameLayout.LayoutParams(...)` is not safe: the explicit object is used
 * as-is and never converted.
 *
 * These tests pin that the source does not assign a plain FrameLayout params object to a view
 * that is hosted by the DragLayer.
 */
class DragLayerChildParamsContractTest {

    /**
     * Resolves a module-relative source path.
     *
     * Gradle runs unit tests from the root project directory, while an IDE may run them from the
     * module directory, so both layouts are tried while walking up from the working directory.
     */
    private fun sourceFile(relativePath: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "lawnchair/")) {
                val candidate = File(dir, prefix + relativePath)
                if (candidate.isFile) return candidate
            }
            dir = dir.parentFile
        }
        throw AssertionError("could not locate $relativePath from ${File(".").absolutePath}")
    }

    @Test
    fun dragLayerParamsTypeIsInsettable() {
        // The DragLayer's own params must satisfy the cast performed on every child.
        assertTrue(
            "BaseDragLayer.LayoutParams must extend InsettableFrameLayout.LayoutParams",
            com.android.launcher3.InsettableFrameLayout.LayoutParams::class.java
                .isAssignableFrom(BaseDragLayer.LayoutParams::class.java),
        )
    }

    @Test
    fun plainFrameLayoutParamsIsNotInsettable() {
        // Documents why the crash happens: a plain FrameLayout params object fails the cast.
        assertTrue(
            "FrameLayout.LayoutParams must NOT be an InsettableFrameLayout.LayoutParams",
            !com.android.launcher3.InsettableFrameLayout.LayoutParams::class.java
                .isAssignableFrom(android.widget.FrameLayout.LayoutParams::class.java),
        )
    }

    @Test
    fun dockSearchPillUsesDragLayerParams() {
        val source = sourceFile("src/app/lawnchair/ui/dock/DockSearchPill.kt")
        val text = source.readText()

        // The pill is added to the DragLayer, so its params must be the DragLayer's type.
        assertTrue(
            "the search pill must be added to the DragLayer",
            text.contains("dragLayer.addView(pill)"),
        )
        assertTrue(
            "the search pill must use BaseDragLayer.LayoutParams, not FrameLayout.LayoutParams",
            text.contains("pill.layoutParams = BaseDragLayer.LayoutParams("),
        )
        assertTrue(
            "the search pill must not assign plain FrameLayout.LayoutParams",
            !text.contains("pill.layoutParams = FrameLayout.LayoutParams("),
        )
    }

    @Test
    fun sidebarHandleUsesDragLayerParams() {
        val source = sourceFile("src/app/lawnchair/sidebar/SidebarHandle.kt")
        val text = source.readText()

        // The handle is added straight into the DragLayer with layoutParamsFor(...).
        assertTrue(
            "the sidebar handle must be added to the DragLayer",
            text.contains("dragLayer.addView(created, created.layoutParamsFor(launcher))"),
        )
        assertTrue(
            "layoutParamsFor must return BaseDragLayer.LayoutParams",
            text.contains("fun layoutParamsFor(launcher: Launcher): BaseDragLayer.LayoutParams"),
        )
        assertTrue(
            "layoutParamsFor must not build FrameLayout.LayoutParams",
            !text.contains("return FrameLayout.LayoutParams("),
        )
    }

    @Test
    fun sidebarViewUsesDragLayerParams() {
        val source = sourceFile("src/app/lawnchair/sidebar/SidebarView.kt")
        val text = source.readText()

        // The panel itself is added straight into the DragLayer, so its params must match the
        // type the inset dispatch casts to. This was the second instance of the same crash:
        // the tab was fixed first, then the panel it opens reproduced it.
        assertTrue(
            "the sidebar panel must use BaseDragLayer.LayoutParams",
            text.contains("BaseDragLayer.LayoutParams("),
        )
        assertTrue(
            "the sidebar panel must not be added with a plain ViewGroup.LayoutParams",
            !text.contains("dragLayer.addView(\n                view,\n                ViewGroup.LayoutParams("),
        )
    }

    @Test
    fun caddyCategoryPanelUsesDragLayerParams() {
        val source = sourceFile("src/app/lawnchair/allapps/caddy/CaddyCategoryPanel.kt")
        val text = source.readText()

        assertTrue(
            "the Caddy category panel must use BaseDragLayer.LayoutParams",
            text.contains("BaseDragLayer.LayoutParams("),
        )
        assertTrue(
            "the Caddy category panel must not be added with a plain ViewGroup.LayoutParams",
            !text.contains("dragLayer.addView(\n                view,\n                ViewGroup.LayoutParams("),
        )
    }
}
