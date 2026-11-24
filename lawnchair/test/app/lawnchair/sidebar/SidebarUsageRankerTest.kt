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

import app.lawnchair.sidebar.SidebarUsageRanker.Usage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Covers the usage scoring that decides which apps the sidebar suggests. */
class SidebarUsageRankerTest {

    private val now = 1_700_000_000_000L

    private fun usage(pkg: String, lastUsedAgoMs: Long, foregroundMs: Long = 0L) = Usage(
        packageName = pkg,
        lastTimeUsed = now - lastUsedAgoMs,
        totalTimeInForeground = foregroundMs,
    )

    @Test
    fun `a more recently used app outranks a stale one`() {
        val ranked = SidebarUsageRanker.score(
            stats = listOf(
                usage("old.app", lastUsedAgoMs = TimeUnit.HOURS.toMillis(10)),
                usage("fresh.app", lastUsedAgoMs = TimeUnit.MINUTES.toMillis(5)),
            ),
            now = now,
        )
        assertEquals(listOf("fresh.app", "old.app"), ranked)
    }

    @Test
    fun `foreground time breaks a recency tie`() {
        val ranked = SidebarUsageRanker.score(
            stats = listOf(
                usage("light.app", 0L, foregroundMs = TimeUnit.MINUTES.toMillis(1)),
                usage("heavy.app", 0L, foregroundMs = TimeUnit.HOURS.toMillis(2)),
            ),
            now = now,
        )
        assertEquals(listOf("heavy.app", "light.app"), ranked)
    }

    @Test
    fun `an app never opened is not suggested`() {
        val ranked = SidebarUsageRanker.score(
            stats = listOf(
                Usage(packageName = "never.app", lastTimeUsed = 0L, totalTimeInForeground = 0L),
            ),
            now = now,
        )
        assertTrue(ranked.isEmpty())
    }

    @Test
    fun `an app older than the recency horizon scores nothing`() {
        val ranked = SidebarUsageRanker.score(
            stats = listOf(usage("ancient.app", lastUsedAgoMs = TimeUnit.DAYS.toMillis(3))),
            now = now,
        )
        assertTrue(ranked.isEmpty())
    }

    @Test
    fun `excluded packages are dropped`() {
        val ranked = SidebarUsageRanker.score(
            stats = listOf(
                usage("pinned.app", 0L),
                usage("other.app", TimeUnit.MINUTES.toMillis(1)),
            ),
            now = now,
            exclude = setOf("pinned.app"),
        )
        assertEquals(listOf("other.app"), ranked)
    }

    @Test
    fun `the limit caps the result`() {
        val stats = (1..10).map {
            usage("app$it", lastUsedAgoMs = TimeUnit.MINUTES.toMillis(it.toLong()))
        }
        val ranked = SidebarUsageRanker.score(stats, now = now, limit = 3)
        assertEquals(listOf("app1", "app2", "app3"), ranked)
    }

    @Test
    fun `a zero limit returns nothing`() {
        val ranked = SidebarUsageRanker.score(
            stats = listOf(usage("a.app", 0L)),
            now = now,
            limit = 0,
        )
        assertTrue(ranked.isEmpty())
    }

    @Test
    fun `a future timestamp is clamped to now rather than scoring above it`() {
        // A clock skew can leave lastTimeUsed in the future. Clamping means it scores exactly
        // like an app opened right now instead of earning a bonus above the maximum.
        val future = SidebarUsageRanker.score(
            stats = listOf(usage("future.app", lastUsedAgoMs = -TimeUnit.HOURS.toMillis(1))),
            now = now,
        )
        val present = SidebarUsageRanker.score(
            stats = listOf(usage("present.app", 0L)),
            now = now,
        )
        assertEquals(1, future.size)
        assertEquals(1, present.size)
        assertEquals(
            SidebarUsageRanker.score(
                stats = listOf(usage("x.app", 0L)),
                now = now,
            ),
            SidebarUsageRanker.score(
                stats = listOf(usage("x.app", lastUsedAgoMs = -TimeUnit.HOURS.toMillis(1))),
                now = now,
            ),
        )
    }

    @Test
    fun `ties are ordered by package name so the list is stable`() {
        val ranked = SidebarUsageRanker.score(
            stats = listOf(usage("b.app", 0L), usage("a.app", 0L)),
            now = now,
        )
        assertEquals(listOf("a.app", "b.app"), ranked)
    }
}
