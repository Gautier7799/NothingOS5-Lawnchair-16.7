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

import android.graphics.Canvas
import android.view.View
import com.android.launcher3.graphics.DragPreviewProvider

/**
 * Drag preview for a Caddy category card.
 *
 * The stock [DragPreviewProvider] only renders a view that implements
 * `DraggableView`; a Caddy card is a plain container, so it produced an empty
 * bitmap and nothing followed the finger. This subclass draws the card view
 * itself, keeping the card's square, rounded-corner look throughout the gesture,
 * and otherwise reuses the launcher's preview pipeline (bitmap sizing, position
 * and drop animation are all inherited unchanged).
 */
class CaddyCardDragPreviewProvider(view: View) : DragPreviewProvider(view) {

    override fun drawDragView(destCanvas: Canvas, scale: Float) {
        val saveCount = destCanvas.save()
        destCanvas.scale(scale, scale)
        destCanvas.translate(blurSizeOutline / 2f, blurSizeOutline / 2f)
        mView.draw(destCanvas)
        destCanvas.restoreToCount(saveCount)
    }
}
