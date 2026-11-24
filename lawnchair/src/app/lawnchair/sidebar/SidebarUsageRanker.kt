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

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * Ranks installed apps by how recently and how often they were used.
 *
 * The scoring is a pure function over [Usage] so it can be unit tested on the JVM; only [query]
 * touches [UsageStatsManager]. Callers must check usage access first — without it the platform
 * returns an empty result rather than an error, so the permission check cannot live here.
 */
object SidebarUsageRanker {

    private const val TAG = "SidebarUsageRanker"

    /** Seven days is long enough to capture a routine and short enough to follow a change. */
    private const val WINDOW_DAYS = 7L

    /** Recency is scored as `1 - age / this`, so anything older than a day scores nothing. */
    private const val RECENCY_HORIZON_MS = 24.0 * 60.0 * 60.0 * 1000.0

    private const val RECENCY_WEIGHT = 100.0
    private const val FOREGROUND_WEIGHT = 0.5

    /** One app's usage, reduced to the two facts the score needs. */
    data class Usage(
        val packageName: String,
        val lastTimeUsed: Long,
        val totalTimeInForeground: Long,
    )

    /**
     * Scores [stats] and returns at most [limit] package names, best first.
     *
     * Each app scores on two axes: how much time it spent in the foreground, and how recently it
     * was opened. Recency dominates because a sidebar is a launcher — the app the user opened
     * five minutes ago is what they want, even if a different app has more total minutes this
     * week.
     */
    fun score(
        stats: Collection<Usage>,
        now: Long,
        exclude: Set<String> = emptySet(),
        limit: Int = SidebarSuggestions.DEFAULT_SUGGESTION_COUNT,
    ): List<String> {
        if (limit <= 0) return emptyList()
        return stats.asSequence()
            .filter { it.packageName !in exclude }
            .filter { it.lastTimeUsed > 0L }
            .map { it.packageName to scoreOf(it, now) }
            .filter { it.second > 0.0 }
            .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })
            .map { it.first }
            .distinct()
            .take(limit)
            .toList()
    }

    private fun scoreOf(usage: Usage, now: Long): Double {
        val foregroundMinutes =
            TimeUnit.MILLISECONDS.toMinutes(usage.totalTimeInForeground).toDouble()
        val ageMs = (now - usage.lastTimeUsed).coerceAtLeast(0L).toDouble()
        val recency = (1.0 - ageMs / RECENCY_HORIZON_MS).coerceAtLeast(0.0)
        return recency * RECENCY_WEIGHT + foregroundMinutes * FOREGROUND_WEIGHT
    }

    /** Queries the platform and returns ranked package names, or an empty list on failure. */
    fun query(
        context: Context,
        exclude: Set<String> = emptySet(),
        limit: Int = SidebarSuggestions.DEFAULT_SUGGESTION_COUNT,
    ): List<String> {
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val now = System.currentTimeMillis()
        val stats = try {
            manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - TimeUnit.DAYS.toMillis(WINDOW_DAYS),
                now,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query usage stats", e)
            return emptyList()
        } ?: return emptyList()

        val installed = installedPackages(context)
        return score(
            stats = stats.map { it.toUsage() },
            now = now,
            exclude = exclude + context.packageName,
            limit = limit,
        ).filter { it in installed }
    }

    private fun UsageStats.toUsage(): Usage = Usage(
        packageName = packageName ?: "",
        lastTimeUsed = lastTimeUsed,
        totalTimeInForeground = totalTimeInForeground,
    )

    /**
     * Packages that currently have a launchable entry.
     *
     * Usage history keeps entries for apps that were uninstalled, so a suggestion must be
     * validated against what is installed now or the panel would show a dead icon.
     */
    private fun installedPackages(context: Context): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }
}
