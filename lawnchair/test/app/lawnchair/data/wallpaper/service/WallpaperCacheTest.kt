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

package app.lawnchair.data.wallpaper.service

import app.lawnchair.data.wallpaper.Wallpaper
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The long-press popup asks for the ranked wallpapers synchronously to choose between two layouts.
 * That used to be a `runBlocking` Room query on the main thread, so a regression back to a blocking
 * read shows up here as a cache that does not answer before the first refresh.
 */
class WallpaperCacheTest {

    private fun wallpaper(id: Long, rank: Int) = Wallpaper(
        id = id,
        imagePath = "/data/wallpaper-$id.png",
        rank = rank,
        timestamp = id,
        checksum = "sum-$id",
    )

    @Test
    fun get_beforeFirstRefresh_isEmptyRatherThanBlocking() {
        // The popup must get an answer with no database access. An empty list means "no saved
        // wallpapers", which selects the plain long-press layout.
        assertThat(WallpaperCache().get()).isEmpty()
    }

    @Test
    fun update_replacesContents() {
        val cache = WallpaperCache()
        cache.update(listOf(wallpaper(1, rank = 0)))
        assertThat(cache.get().map { it.id }).containsExactly(1L)

        cache.update(listOf(wallpaper(2, rank = 0), wallpaper(3, rank = 1)))
        assertThat(cache.get().map { it.id }).containsExactly(2L, 3L).inOrder()
    }

    @Test
    fun update_withEmptyList_clearsPreviousEntries() {
        // saveWallpaper deletes the lowest-ranked entry when the list is full, so the cache has to
        // be able to shrink as well as grow.
        val cache = WallpaperCache()
        cache.update(listOf(wallpaper(1, rank = 0)))
        cache.update(emptyList())
        assertThat(cache.get()).isEmpty()
    }

    @Test
    fun get_isVisibleToOtherThreadsAfterUpdate() {
        // Reads happen on the main thread while writes happen on the room executor.
        val cache = WallpaperCache()
        val expected = listOf(wallpaper(7, rank = 0))

        val writer = Thread { cache.update(expected) }
        writer.start()
        writer.join()

        assertThat(cache.get()).isEqualTo(expected)
    }
}
