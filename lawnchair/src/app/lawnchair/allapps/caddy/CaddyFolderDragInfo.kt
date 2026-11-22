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

import com.android.launcher3.model.data.ItemInfo

/**
 * Payload carried while a Caddy category card is dragged from the drawer to the
 * home screen. It is a plain [ItemInfo] so `Workspace.beginDragShared` accepts the
 * source view (that path requires an ItemInfo tag), and so the drop resolver can
 * recognise the drag by type.
 *
 * It is never written to the database: the folder that results from the drop is an
 * ordinary [com.android.launcher3.model.data.FolderInfo] whose contents are real
 * app rows. This object only travels with the gesture.
 */
class CaddyFolderDragInfo(
    val spec: CaddyFolderFactory.FolderSpec,
) : ItemInfo() {

    init {
        itemType = CaddyFolderDragContract.DRAG_ITEM_TYPE
        spanX = CaddyFolderDragContract.SPAN_X
        spanY = CaddyFolderDragContract.SPAN_Y
    }

    companion object {
        /** True when [info] is a Caddy card being dragged onto the home screen. */
        @JvmStatic
        fun isCaddyFolderDrag(info: ItemInfo?): Boolean = info is CaddyFolderDragInfo
    }
}
