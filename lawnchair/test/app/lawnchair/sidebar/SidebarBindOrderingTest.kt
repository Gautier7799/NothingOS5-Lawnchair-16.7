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

package app.lawnchair.sidebar

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the ordering inside [SidebarView.bind], which is what made the panel render empty.
 *
 * `bind` starts with `column.removeAllViews()`. That detaches *every* child, including the
 * `appRows` container that `init` added. The app icons are then added to `appRows`, so unless
 * the container is attached again the icons live in a view with no parent and the panel draws
 * nothing but its background — the panel is open, not crashed, and simply looks broken.
 *
 * `extrasCount` has a similar ordering hazard: `bindAppRows()` folds it into `itemCount`, which
 * `onMeasure` reads. Computing it afterwards sizes the panel for the apps alone and clips the
 * quick controls, the volume row, the brightness row or the search field.
 *
 * Both are invisible to a unit test that only exercises the pure `SidebarMetrics` arithmetic, so
 * they are pinned here against the source.
 */
class SidebarBindOrderingTest {

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

    /**
     * Index of the real `bindAppRows()` call inside [body].
     *
     * A plain `indexOf("bindAppRows()")` also matches the call's name in the comment above it,
     * which would make the ordering assertions pass for the wrong reason. The call is matched
     * with its own indentation so only the statement matches.
     */
    private fun bindAppRowsCall(body: String): Int = body.indexOf("\n        bindAppRows()")

    /** The body of `bind`, from its signature up to the closing brace that ends it. */
    private fun bindBody(): String {
        val text = sourceFile("src/app/lawnchair/sidebar/SidebarView.kt").readText()
        val start = text.indexOf("fun bind(apps: List<AppInfo>)")
        assertTrue("SidebarView must declare bind(apps)", start >= 0)
        val end = text.indexOf("\n    }", start)
        assertTrue("could not delimit the body of bind", end > start)
        return text.substring(start, end)
    }

    @Test
    fun appRowsIsReattachedAfterRemoveAllViews() {
        val body = bindBody()
        val cleared = body.indexOf("column.removeAllViews()")
        assertTrue("bind must clear the column", cleared >= 0)

        // appRows must be added back, otherwise the icons go into a detached container.
        val reattached = body.indexOf("column.addView(\n            appRows,")
        assertTrue(
            "bind must re-attach appRows after column.removeAllViews(), or the panel renders empty",
            reattached > cleared,
        )
    }

    @Test
    fun appRowsIsAttachedBeforeIconsAreBound() {
        val body = bindBody()
        val reattached = body.indexOf("column.addView(\n            appRows,")
        val bound = bindAppRowsCall(body)
        assertTrue("bind must call bindAppRows()", bound >= 0)
        assertTrue(
            "appRows must be attached before bindAppRows() fills it",
            reattached in 0 until bound,
        )
    }

    @Test
    fun extrasCountIsSetBeforeAppRowsAreBound() {
        val body = bindBody()
        val extras = body.indexOf("extrasCount =")
        val bound = bindAppRowsCall(body)
        assertTrue("bind must compute extrasCount", extras >= 0)
        assertTrue("bind must call bindAppRows()", bound >= 0)
        assertTrue(
            "extrasCount must be set before bindAppRows(), which folds it into itemCount",
            extras < bound,
        )
    }

    @Test
    fun initStillAttachesAppRows() {
        // init attaches appRows once; bind detaches and re-attaches it on every rebind.
        val text = sourceFile("src/app/lawnchair/sidebar/SidebarView.kt").readText()
        val init = text.indexOf("init {")
        val attach = text.indexOf("column.addView(\n            appRows,", init)
        assertTrue("init must attach appRows to the column", attach > init)
    }
}
