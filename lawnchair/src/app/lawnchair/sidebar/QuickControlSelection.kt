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

package app.lawnchair.sidebar

/**
 * Which quick controls the sidebar shows, stored as a comma-separated preference string.
 *
 * The standalone Samart sidebar app let each tile be switched on or off from its Control tab.
 * This is the persistence half of that, kept Android-free and unit tested.
 *
 * The string is treated as untrusted: it can contain tiles from a newer build, tiles removed
 * since, duplicates, or nothing at all. Every one of those cases resolves to a usable list
 * instead of an empty strip or a crash, because a preference file is exactly the kind of place
 * a stale value survives an upgrade in.
 */
object QuickControlSelection {

    /** Separator used in the preference string. */
    private const val SEPARATOR = ","

    /**
     * Resolves [saved] into the controls to show, in display order.
     *
     * An unset preference (`null` or blank) means every control is enabled, which is the sane
     * default for a fresh install and for a user who never opened the settings screen.
     *
     * Unknown names are ignored. Controls that exist in this build but not in the saved string
     * are only added when the preference has never been written; once the user has made a
     * choice, a missing control means they turned it off, not that it was added later.
     */
    fun resolve(saved: String?): List<QuickControl> {
        if (saved.isNullOrBlank()) return QuickControl.defaultOrder
        val wanted = saved.split(SEPARATOR).mapNotNull { name ->
            QuickControl.values().firstOrNull { it.name == name.trim() }
        }.toSet()
        return QuickControl.defaultOrder.filter { it in wanted }
    }

    /** Serialises [controls] for storage, always in display order so the file is stable. */
    fun serialise(controls: Collection<QuickControl>): String {
        val wanted = controls.toSet()
        return QuickControl.defaultOrder.filter { it in wanted }
            .joinToString(SEPARATOR) { it.name }
    }

    /** Returns [controls] with [control] turned on when it was off, or off when it was on. */
    fun toggle(controls: Collection<QuickControl>, control: QuickControl): Set<QuickControl> {
        val next = controls.toMutableSet()
        if (!next.add(control)) next.remove(control)
        return next
    }

    /** Whether [control] is currently shown. */
    fun isEnabled(controls: Collection<QuickControl>, control: QuickControl): Boolean = control in controls
}
