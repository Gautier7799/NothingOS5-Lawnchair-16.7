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

import dev.kdrag0n.colorkt.Color as ColorKt
import dev.kdrag0n.colorkt.rgb.Srgb
import dev.kdrag0n.monet.theme.ColorScheme
import dev.kdrag0n.monet.theme.ColorSwatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * A distinguishable scheme instance. The cache keys on the scheme, so two instances with identical
 * swatches must still be treated as different palettes.
 */
private class KeyedScheme : ColorScheme() {
    private fun swatch(color: Srgb): ColorSwatch =
        (setOf(0, 10, 50) + (1..10).map { it * 100 }).associateWith { color as ColorKt }

    override val neutral1 = swatch(Srgb(0.9, 0.9, 0.9))
    override val neutral2 = swatch(Srgb(0.9, 0.9, 0.9))
    override val accent1 = swatch(Srgb(0.1, 0.4, 0.9))
    override val accent2 = swatch(Srgb(0.1, 0.4, 0.9))
    override val accent3 = swatch(Srgb(0.1, 0.4, 0.9))
}

/**
 * Pins the memo that keeps a drawer pass from re-deriving the same palette roles per card.
 *
 * The risk with any cache here is a stale colour after the wallpaper changes, so the tests focus
 * on the invalidation rule: a new scheme must miss, and the same scheme must hit.
 */
class CaddyPaletteCacheTest {

    @Test
    fun repeatedLookupsWithTheSameKeyComputeOnce() {
        val cache = CaddyPaletteCache<Int>()
        var computations = 0
        val key = KeyedScheme()

        repeat(5) {
            cache.getOrCompute(key) {
                computations++
                42
            }
        }

        assertEquals("the same key must hit the memo", 1, computations)
    }

    @Test
    fun aDifferentSchemeRecomputes() {
        val cache = CaddyPaletteCache<Int>()
        var computations = 0
        val first = KeyedScheme()
        val second = KeyedScheme()

        val a = cache.getOrCompute(first) { computations++; 1 }
        val b = cache.getOrCompute(second) { computations++; 2 }

        assertEquals(2, computations)
        assertNotEquals("a new scheme must not reuse the old value", a, b)
    }

    @Test
    fun switchingBackRecomputesRatherThanKeepingTwoEntries() {
        val cache = CaddyPaletteCache<Int>()
        var computations = 0
        val first = KeyedScheme()
        val second = KeyedScheme()

        cache.getOrCompute(first) { computations++; 1 }
        cache.getOrCompute(second) { computations++; 2 }
        cache.getOrCompute(first) { computations++; 1 }

        assertEquals("a one-slot memo must not grow into a multi-entry cache", 3, computations)
    }

    @Test
    fun lightAndDarkAreSeparateKeys() {
        val cache = CaddyPaletteCache<Int>()
        var computations = 0
        val scheme = KeyedScheme()

        cache.getOrCompute("light-$scheme") { computations++; 1 }
        cache.getOrCompute("dark-$scheme") { computations++; 2 }

        assertEquals("the mode must be part of the key", 2, computations)
    }

    @Test
    fun theComputedValueIsReturnedNotJustMemoised() {
        val cache = CaddyPaletteCache<String>()
        val key = KeyedScheme()

        assertEquals("first", cache.getOrCompute(key) { "first" })
        assertEquals("first", cache.getOrCompute(key) { "second" })
    }
}
