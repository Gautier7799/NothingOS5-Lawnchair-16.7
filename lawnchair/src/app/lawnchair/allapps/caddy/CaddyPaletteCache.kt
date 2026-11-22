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

/**
 * A one-slot memo for a palette value derived from the wallpaper scheme.
 *
 * Resolving a role costs a `ColorScheme` lookup plus a Cielab round trip per call
 * (`setLuminance` converts to Cielab and back), and a single category card asks for four roles,
 * so one drawer pass repeated the same conversions for every visible card.
 *
 * The key carries the `ColorScheme` instance itself, so the memo invalidates by construction:
 * `ThemeProvider` caches one scheme per (accent colour, style) pair and hands out a *new* instance
 * when the wallpaper or accent changes. A different scheme therefore misses, and the same scheme
 * always describes the same colours. That is why this class needs no listener and no lifecycle
 * ownership.
 *
 * The key holds a strong reference on purpose: `ThemeProvider` already keeps every scheme alive
 * for the life of the process, so retaining one here cannot keep a stale palette alive.
 */
internal class CaddyPaletteCache<T> {

    private class Entry<T>(val key: Any, val value: T)

    @Volatile
    private var entry: Entry<T>? = null

    /** Returns the memoised value for [key], computing it with [compute] on a miss. */
    fun getOrCompute(key: Any, compute: () -> T): T {
        entry?.let { if (it.key == key) return it.value }
        return compute().also { entry = Entry(key, it) }
    }
}
