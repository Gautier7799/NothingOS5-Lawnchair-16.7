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

package app.lawnchair.dossier

/**
 * Android-free decisions for turning a Caddy category card dragged out of the app
 * drawer into a workspace Dossier card.
 *
 * The card is the same presentation the drawer shows: a title and a grid of app
 * icons. Placing it as a native [DossierInfo] (rather than a folder) is what makes it
 * move, resize with handles, and accept further app icons, exactly like the launcher's
 * own resizable items.
 */
object CaddyCardDrop {

    /** A category card covers 2x2 cells, matching the drawer card's 2x2 icon preview. */
    const val DEFAULT_SPAN_X = 2
    const val DEFAULT_SPAN_Y = 2

    /** True when a drag carries at least one app key, so a card can be built. */
    @JvmStatic
    fun canCreate(appKeyCount: Int): Boolean = appKeyCount > 0

    /**
     * Span for a card dropped at `(cellX, cellY)`.
     *
     * Prefers the full 2x2 and shrinks toward a single cell, so a drop in a cramped
     * region still lands instead of being refused. Returns null when not even one cell
     * is vacant, which means the caller must not create a card.
     */
    @JvmStatic
    fun resolveSpan(
        cellX: Int,
        cellY: Int,
        gridCountX: Int,
        gridCountY: Int,
        vacancy: DossierPlacement.RegionVacancy,
    ): IntArray? = DossierPlacement.resolveSpan(
        cellX = cellX,
        cellY = cellY,
        gridCountX = gridCountX,
        gridCountY = gridCountY,
        sourceSpanX = 1,
        sourceSpanY = 1,
        vacancy = vacancy,
    )
}
