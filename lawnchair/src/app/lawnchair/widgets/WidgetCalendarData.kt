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

package app.lawnchair.widgets

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

/**
 * Pure calendar logic for the "on this day" widget family.
 *
 * The data model mirrors `assets/widgets/observances.json`. Everything here is deterministic and
 * framework-free so the matching rules can be unit tested on the JVM.
 */
object WidgetCalendarData {

    /**
     * A date rule for observances whose civil date moves from year to year, such as "the last
     * Saturday of March". The reference data set pinned these to a single day, which is wrong in
     * every year where the rule lands elsewhere.
     */
    sealed interface Rule {
        /** The [ordinal]-th [weekday] of the month (1-based); `ordinal = -1` means the last one. */
        data class NthWeekday(val weekday: DayOfWeek, val ordinal: Int) : Rule
    }

    /**
     * One observance.
     *
     * [day] is null when the observance follows a [rule] instead of a fixed day.
     */
    data class Observance(
        val month: Month,
        val day: Int?,
        val name: String,
        val rule: Rule? = null,
    ) {
        /** Whether this observance falls on [date]. */
        fun occursOn(date: LocalDate): Boolean = when {
            date.month != month -> false
            rule != null -> date.dayOfMonth == nthWeekdayDayOfMonth(date.year, month, rule)
            day != null -> date.dayOfMonth == day
            else -> false
        }
    }

    /** Today's observance, or null when no observance is registered for [date]. */
    fun observanceFor(observances: List<Observance>, date: LocalDate): Observance? = observances.firstOrNull { it.occursOn(date) }

    /**
     * Builds an [Observance] from the raw asset fields.
     *
     * Kept free of `org.json` so the field-to-model mapping (including the rule dispatch) is unit
     * testable on the JVM, where the Android `org.json` classes are stubs. Returns null when the
     * month is out of range.
     */
    fun observance(month: Int, day: Int?, name: String, ruleKind: String? = null, ruleWeekday: String? = null): Observance? {
        if (month !in 1..12) return null
        val rule = parseRule(ruleKind, ruleWeekday)
        return Observance(
            month = Month.of(month),
            day = if (rule == null) day else null,
            name = name,
            rule = rule,
        )
    }

    /**
     * Maps a rule descriptor from the asset to a [Rule].
     *
     * Unknown kinds and malformed weekdays resolve to null, which makes the observance fall back to
     * its fixed day (or to no match when it has none) instead of throwing.
     */
    fun parseRule(kind: String?, weekday: String?): Rule? = when (kind) {
        "lastWeekdayOfMonth" -> weekday?.let { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }?.let { Rule.NthWeekday(it, -1) }
        "thirdSaturdayOfMonth" -> Rule.NthWeekday(DayOfWeek.SATURDAY, 3)
        "firstSundayOfMonth" -> Rule.NthWeekday(DayOfWeek.SUNDAY, 1)
        else -> null
    }

    /** An upcoming observance together with how many days away it is. */
    data class Upcoming(val observance: Observance, val daysUntil: Int)

    /**
     * The next observance strictly after [date], searched over the coming year.
     *
     * Returns null only when [observances] is empty. The search is bounded to 366 days so a rule
     * that never resolves (bad data) cannot loop forever.
     */
    fun nextObservance(observances: List<Observance>, date: LocalDate): Upcoming? {
        if (observances.isEmpty()) return null
        for (offset in 1..366) {
            val candidate = date.plusDays(offset.toLong())
            val match = observanceFor(observances, candidate)
            if (match != null) return Upcoming(match, offset)
        }
        return null
    }

    /**
     * Resolves the day of month on which a [Rule.NthWeekday] lands.
     *
     * For a positive [Rule.NthWeekday.ordinal] the day is `first + (ordinal - 1) weeks`; a negative
     * ordinal counts back from the month's last day, which is what "last Saturday of March" needs.
     */
    fun nthWeekdayDayOfMonth(year: Int, month: Month, rule: Rule): Int = when (rule) {
        is Rule.NthWeekday -> {
            val length = month.length(java.time.Year.isLeap(year.toLong()))
            val anchor = if (rule.ordinal >= 0) LocalDate.of(year, month, 1) else LocalDate.of(year, month, length)
            val shift = (rule.weekday.value - anchor.dayOfWeek.value + 7) % 7
            if (rule.ordinal >= 0) {
                1 + shift + (rule.ordinal - 1) * 7
            } else {
                length - ((anchor.dayOfWeek.value - rule.weekday.value + 7) % 7)
            }
        }
    }
}
