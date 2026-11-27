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
import java.io.File
import org.junit.Test

/**
 * Guards the icon-fit contract of the Caddy category panel.
 *
 * The panel drew each icon into a fixed 68dp box while the drawable inside it was rendered at
 * its own intrinsic size (the all-apps bitmap, ~168px), and `BaseIcon.AllApps` added 8dp of
 * horizontal padding that `TextView` treats as the compound drawable's start. The drawable
 * therefore began 8dp inside the box and ran far past its right edge, so the box cropped both
 * sides and a circular icon read as a square/angular blob. The card preview hit the same defect
 * and was fixed in #79; the panel was missed.
 *
 * The panel cannot be instantiated on the JVM (it needs a real `Context`, `DeviceProfile` and
 * the DragLayer), so these tests pin the source contract that keeps the fit in place. The
 * numeric part of the contract, `CaddyCardMetrics.iconScale`, is covered by
 * [CaddyCardMetricsTest].
 */
class CaddyCategoryPanelIconFitTest {

    private fun panelSource(): String {
        val candidates = listOf(
            File("lawnchair/src/app/lawnchair/allapps/caddy/CaddyCategoryPanel.kt"),
            File("src/app/lawnchair/allapps/caddy/CaddyCategoryPanel.kt"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("CaddyCategoryPanel.kt not found from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun panel_scalesTheIconViewInsteadOfOnlySizingIt() {
        val source = panelSource()
        assertThat(source).contains("icon.scaleX = scale")
        assertThat(source).contains("icon.scaleY = scale")
    }

    @Test
    fun panel_fitsTheIconThroughTheSharedMetrics() {
        val source = panelSource()
        assertThat(source).contains("CaddyCardMetrics.iconScale(")
    }

    @Test
    fun panel_givesTheIconViewTheDrawablesOwnSize() {
        val source = panelSource()
        // A hard-coded box cannot match the drawable, which is the whole defect.
        assertThat(source).doesNotContain("FrameLayout.LayoutParams(dp(68f), dp(68f), Gravity.CENTER)")
        assertThat(source).contains("drawableSizePx(icon)")
    }

    @Test
    fun panel_clearsTheIconPaddingThatShiftsTheDrawable() {
        val source = panelSource()
        assertThat(source).contains("icon.setPadding(0, 0, 0, 0)")
    }

    @Test
    fun panel_disablesTheScaleAnimatorThatWouldWipeTheFit() {
        val source = panelSource()
        assertThat(source).contains("icon.stateListAnimator = null")
    }

    @Test
    fun panel_releasesTheOutlineClipThatCutTheIconEdges() {
        val source = panelSource()
        assertThat(source).contains("icon.clipToOutline = false")
        assertThat(source).contains("iconFrame.clipChildren = false")
        assertThat(source).contains("iconFrame.clipToPadding = false")
    }
}
