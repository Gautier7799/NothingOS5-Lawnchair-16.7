/*
 * Copyright (C) 2026 Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.popup

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.view.View
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.Launcher
import com.android.launcher3.Utilities
import com.android.launcher3.views.ActivityContext

/**
 * Blurs and lightly dims the surface behind the App Info / shortcut popup.
 *
 * The popup is shared by the app drawer (Caddy Drawer) and the workspace (Caddy Home), so a single
 * call site in the popup covers both surfaces.
 */
object PopupBlur {

    private const val BLUR_RADIUS = 24f
    private const val DIM_FACTOR = 0.88f

    fun apply(ctx: Context, activityContext: ActivityContext) {
        if (!Utilities.ATLEAST_S) return
        if (!PreferenceManager2.getInstance(ctx).blurBehindPopup.firstCached()) return

        val blur = RenderEffect.createBlurEffect(BLUR_RADIUS, BLUR_RADIUS, Shader.TileMode.CLAMP)
        val dimMatrix = ColorMatrix().apply { setScale(DIM_FACTOR, DIM_FACTOR, DIM_FACTOR, 1f) }
        val dim = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(dimMatrix))
        val effect = RenderEffect.createChainEffect(dim, blur)

        targets(activityContext).forEach { it.setRenderEffect(effect) }
    }

    fun clear(activityContext: ActivityContext) {
        if (!Utilities.ATLEAST_S) return
        targets(activityContext).forEach {
            it.setRenderEffect(null)
            it.invalidate()
        }
    }

    private fun targets(activityContext: ActivityContext): List<View> {
        val result = ArrayList<View>(3)
        (activityContext as? Launcher)?.depthBlurTargets?.let(result::addAll)
        activityContext.appsView?.let(result::add)
        return result
    }
}
