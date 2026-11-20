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
import android.util.AttributeSet
import com.android.launcher3.BubbleTextView
import com.android.launcher3.dragndrop.DragOptions.PreDragCondition

/**
 * An app icon inside [CaddyCategoryPanel].
 *
 * It behaves like a drawer icon except that a long press goes straight to the drag. The
 * stock implementation opens a shortcut popup anchored to the icon, and that popup does
 * not work from inside a DragLayer overlay: it is added to the same DragLayer, so it
 * covers the panel and its own touch handling takes over the gesture the user meant as a
 * drag.
 */
class CaddyPanelIcon @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BubbleTextView(context, attrs, defStyleAttr) {

    /** Returns no pre-drag condition, so the drag starts as soon as the long press fires. */
    override fun startLongPressAction(): PreDragCondition? = null
}
