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
 * Pure decisions for creating a Dossier card from a drag interaction.
 *
 * The original Dossier idea is that a card is born when the user drops one app
 * icon onto another app icon on the home screen. This object holds the
 * Android-free part of that decision so it can be exercised on the JVM; the
 * caller supplies booleans derived from the live item types.
 */
object DossierCreation {

    /** Fallback title when neither app exposes a usable label. */
    const val DEFAULT_TITLE = "Dossier"

    /**
     * True when a Dossier should be created for a drop: the dragged item and the
     * item under it are both plain apps, and the target is not already a card
     * (dropping onto a card is the add-to-card path, handled by the card itself).
     */
    fun shouldGroup(draggedIsApp: Boolean, targetIsApp: Boolean, targetIsDossier: Boolean): Boolean = draggedIsApp && targetIsApp && !targetIsDossier

    /**
     * Default title for a freshly created card: the first non-blank app label,
     * so the card carries a meaningful name the user can later rename.
     */
    fun defaultTitle(titles: List<CharSequence?>): String = titles.firstOrNull { !it.isNullOrBlank() }?.toString()?.trim() ?: DEFAULT_TITLE
}
