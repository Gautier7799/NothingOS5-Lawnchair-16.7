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

package app.lawnchair.homecards

/**
 * Android-free decisions the controller makes while attaching cards and syncing them with the
 * app list. Keeping them here lets the JVM tests pin behaviour that would otherwise only be
 * observable on a device.
 */
object CaddyHomeCardAttachment {

    /** What [CaddyHomeCardController.attach] must do with a card's existing view. */
    enum class Action {
        /** The view has no live owner: add it to the target page. */
        ADD,

        /** The view already lives on the target page: re-apply its position and span. */
        RELAYOUT,

        /** The view lives on a different page: remove it from its real owner, then add it. */
        MOVE,
    }

    /**
     * Chooses the attach action from the view's resolved owner.
     *
     * [ownerOnTargetPage] must only be consulted when [hasOwner] is true. The owner is resolved
     * from the live view hierarchy (the hosting `ShortcutAndWidgetContainer`), never from
     * `view.parent`, which is the container and not the `CellLayout`.
     */
    fun attachAction(hasOwner: Boolean, ownerOnTargetPage: Boolean): Action = when {
        !hasOwner -> Action.ADD
        ownerOnTargetPage -> Action.RELAYOUT
        else -> Action.MOVE
    }

    /**
     * The saved cards that must survive a sync even though their category is absent from the
     * current app list.
     *
     * The launcher can publish an incomplete `AllAppsStore` snapshot while loading. A card whose
     * apps are still installed but whose category did not form in that snapshot must keep its
     * view and its saved placement; dropping it would hide the card and erase the user's position.
     * A card whose apps no longer exist is genuinely gone and is not carried over.
     */
    fun carriedOver(
        stored: List<CaddyHomeCardInfo>,
        desiredIds: Set<Int>,
        liveAppKeys: Set<String>,
    ): List<CaddyHomeCardInfo> = stored.filter { card ->
        card.id !in desiredIds && card.appKeys.any(liveAppKeys::contains)
    }
}
