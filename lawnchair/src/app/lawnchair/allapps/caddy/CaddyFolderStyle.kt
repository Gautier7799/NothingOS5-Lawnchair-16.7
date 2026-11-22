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

import android.content.Context
import com.android.launcher3.graphics.ShapeDelegate
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.model.data.FolderInfo

/**
 * Visual identity of a folder materialised from a Caddy category card.
 *
 * A Caddy folder is a real, native [FolderInfo]; only its closed-icon presentation
 * differs. Instead of the theme's folder shape (a circle by default) it draws a
 * square with rounded corners, matching the card the user dragged out of the drawer.
 *
 * Everything here is a pure decision so it can be unit tested on the JVM; the actual
 * drawing stays in the native [com.android.launcher3.folder.PreviewBackground] and
 * [com.android.launcher3.folder.FolderAnimationManager] paths.
 */
object CaddyFolderStyle {

    /**
     * Corner radius of the rounded square as a ratio of the preview's half-edge.
     * `0f` is a sharp square, `1f` is a full circle; this sits clearly in the
     * "rounded square" range so the result never reads as a circular folder.
     */
    const val CORNER_RADIUS_RATIO = 0.28f

    /** The rounded-square shape used for Caddy folders. */
    @JvmStatic
    fun getShape(): ShapeDelegate = ShapeDelegate.RoundedSquare(CORNER_RADIUS_RATIO)

    /**
     * Shape to use for [info]'s folder presentation: the Caddy rounded square when the
     * folder carries the Caddy flag, otherwise the user's configured folder shape.
     */
    @JvmStatic
    fun getShape(context: Context, info: FolderInfo?): ShapeDelegate {
        if (isCaddyFolder(info)) return getShape()
        return ThemeManager.INSTANCE.get(context).folderShape
    }

    /** True when [info] is a folder created from a Caddy category card. */
    @JvmStatic
    fun isCaddyFolder(info: FolderInfo?): Boolean = info != null && info.hasOption(FolderInfo.FLAG_CADDY_FOLDER)
}
