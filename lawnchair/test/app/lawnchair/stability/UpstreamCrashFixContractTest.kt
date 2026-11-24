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

package app.lawnchair.stability

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins crash fixes backported from upstream `LawnchairLauncher/lawnchair` (`16-dev`).
 *
 * Each guard below protects a path that threw on real devices, and each is a single defensive
 * branch that is trivial to drop again in a refactor or a bad merge. The guards live inside
 * Android views and services, so they cannot run on the JVM; what is checked here is that the
 * branch still exists where it belongs.
 */
class UpstreamCrashFixContractTest {

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

    private fun text(relativePath: String) = sourceFile(relativePath).readText()

    @Test
    fun folderDropClampsInsertRank() {
        // The rank is captured at drag start and goes stale when the folder changes mid-drag.
        // views.add(rank, icon) then throws IndexOutOfBoundsException on drop.
        val source = text("src/com/android/launcher3/folder/FolderPagedView.java")
        val clamp = source.indexOf("rank = Utilities.boundToRange(rank, 0, views.size());")
        val insert = source.indexOf("views.add(rank, icon);")
        assertTrue("dropping an icon into a folder must clamp the insert rank", clamp >= 0)
        assertTrue("the clamp must run before views.add(rank, icon)", clamp < insert)
    }

    @Test
    fun notificationListenerToleratesDisconnectBeforeConnect() {
        // The framework calls onListenerDisconnected() from onDestroy(), which can happen before
        // onListenerConnected() initialised mSettingsCache. Unregistering then is an NPE.
        val source = text("src/com/android/launcher3/notification/NotificationListener.java")
        assertTrue(
            "onListenerDisconnected must null-check mSettingsCache",
            source.contains("if (mSettingsCache != null) {"),
        )
    }

    @Test
    fun workspaceDropExternalSkipsAnimationWhenViewNotAttached() {
        // addInScreen fails silently on an unknown screen id or a missing hotseat page. Animating
        // the detached view then NPEs in DragLayer#animateViewIntoPosition.
        val source = text("src/com/android/launcher3/Workspace.java")
        assertTrue(
            "onDropExternal must bail out when the view was not attached",
            source.contains("if (!(view.getParent() instanceof ShortcutAndWidgetContainer)) {"),
        )
        assertTrue(
            "the bail-out must release the drag view",
            source.contains("d.deferDragViewCleanupPostAnimation = false;"),
        )
    }

    @Test
    fun headlessWidgetManagerRebindsInsteadOfThrowing() {
        // A preference key can be re-pointed at a different provider. check() threw
        // IllegalStateException; the widget is now rebuilt for the new provider.
        val source = text("lawnchair/src/app/lawnchair/HeadlessWidgetsManager.kt")
        assertTrue(
            "getWidget must not assert the provider with check()",
            !source.contains("was created with a different provider"),
        )
        assertTrue(
            "getWidget must be synchronized, as it mutates widgetsMap",
            source.contains("@Synchronized"),
        )
        assertTrue(
            "an unbound widget id must be reported as not bound",
            source.contains("widgetId > -1 &&"),
        )
    }

    @Test
    fun floatingSurfaceViewRemovesUnconditionally() {
        // isAttachedToWindow can be false while the view still has a parent. Skipping the removal
        // leaves it parented, and the next show() crashes with "already has a parent".
        val source = text("lawnchair/src/app/lawnchair/views/LawnchairFloatingSurfaceView.kt")
        val body = source.substring(source.indexOf("private fun removeViewImmediate()"))
        val call = body.indexOf("removeViewFromParent()")
        assertTrue("removeViewImmediate must call removeViewFromParent()", call >= 0)
        val guard = body.indexOf("if (isAttachedToWindow)")
        assertTrue(
            "removeViewImmediate must not gate the removal on isAttachedToWindow",
            guard < 0,
        )
    }

    @Test
    fun activityCachingLogicToleratesDisappearingComponent() {
        // A component can vanish between the package-update callback and the icon refresh.
        val source = text("lawnchair/src/app/lawnchair/LawnchairActivityCachingLogic.kt")
        assertTrue(
            "getActivityInfo must catch NameNotFoundException",
            source.contains("catch (_: PackageManager.NameNotFoundException)"),
        )
        assertTrue(
            "a missing component must fall back to the default icon",
            source.contains("return cache.getDefaultIcon(info.user)"),
        )
    }
}
