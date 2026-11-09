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

import app.lawnchair.widgets.WidgetCalendarData.Observance
import app.lawnchair.widgets.WidgetCalendarData.Rule
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import org.junit.Test

class WidgetCalendarDataTest {

    private val lastSaturday = Rule.NthWeekday(DayOfWeek.SATURDAY, -1)
    private val thirdSaturday = Rule.NthWeekday(DayOfWeek.SATURDAY, 3)
    private val newYear = Observance(Month.JANUARY, 1, "New Year's Day")
    private val earthHour = Observance(Month.MARCH, null, "Earth Hour", lastSaturday)
    private val softwareFreedom = Observance(Month.SEPTEMBER, null, "Software Freedom Day", thirdSaturday)

    @Test
    fun occursOn_fixedDate_matchesOnlyThatDay() {
        assertThat(newYear.occursOn(LocalDate.of(2026, 1, 1))).isTrue()
        assertThat(newYear.occursOn(LocalDate.of(2026, 1, 2))).isFalse()
        assertThat(newYear.occursOn(LocalDate.of(2026, 2, 1))).isFalse()
    }

    @Test
    fun nthWeekday_lastSaturdayOfMarch_resolvesForEachYear() {
        // 2026: 31 March is a Tuesday, so the last Saturday is the 28th.
        assertThat(WidgetCalendarData.nthWeekdayDayOfMonth(2026, Month.MARCH, lastSaturday)).isEqualTo(28)
        assertThat(earthHour.occursOn(LocalDate.of(2026, 3, 28))).isTrue()
        assertThat(earthHour.occursOn(LocalDate.of(2026, 3, 21))).isFalse()
        // 2027: 31 March is a Wednesday, so the last Saturday is the 27th.
        assertThat(WidgetCalendarData.nthWeekdayDayOfMonth(2027, Month.MARCH, lastSaturday)).isEqualTo(27)
    }

    @Test
    fun nthWeekday_thirdSaturdayOfSeptember_resolvesForEachYear() {
        assertThat(WidgetCalendarData.nthWeekdayDayOfMonth(2026, Month.SEPTEMBER, thirdSaturday)).isEqualTo(19)
        assertThat(softwareFreedom.occursOn(LocalDate.of(2026, 9, 19))).isTrue()
        assertThat(WidgetCalendarData.nthWeekdayDayOfMonth(2027, Month.SEPTEMBER, thirdSaturday)).isEqualTo(18)
    }

    @Test
    fun observanceFor_returnsMatchingEntry() {
        val all = listOf(newYear, earthHour, softwareFreedom)
        assertThat(WidgetCalendarData.observanceFor(all, LocalDate.of(2026, 1, 1))).isEqualTo(newYear)
        assertThat(WidgetCalendarData.observanceFor(all, LocalDate.of(2026, 6, 15))).isNull()
    }

    @Test
    fun nextObservance_findsNextWithinTheYear() {
        val all = listOf(newYear, earthHour)
        val upcoming = WidgetCalendarData.nextObservance(all, LocalDate.of(2026, 1, 2))
        assertThat(upcoming).isNotNull()
        assertThat(upcoming!!.observance).isEqualTo(earthHour)
        assertThat(upcoming.daysUntil).isEqualTo(85)
    }

    @Test
    fun nextObservance_wrapsIntoNextYear() {
        val all = listOf(newYear)
        val upcoming = WidgetCalendarData.nextObservance(all, LocalDate.of(2026, 12, 31))
        assertThat(upcoming).isNotNull()
        assertThat(upcoming!!.observance).isEqualTo(newYear)
        assertThat(upcoming.daysUntil).isEqualTo(1)
    }

    @Test
    fun nextObservance_emptyList_returnsNull() {
        assertThat(WidgetCalendarData.nextObservance(emptyList(), LocalDate.of(2026, 1, 1))).isNull()
    }

    @Test
    fun observance_buildsFixedAndRuleEntries() {
        val fixed = WidgetCalendarData.observance(month = 1, day = 1, name = "New Year's Day")
        assertThat(fixed).isNotNull()
        assertThat(fixed!!.day).isEqualTo(1)
        assertThat(fixed.rule).isNull()

        val ruled = WidgetCalendarData.observance(
            month = 3,
            day = null,
            name = "Earth Hour",
            ruleKind = "lastWeekdayOfMonth",
            ruleWeekday = "SATURDAY",
        )
        assertThat(ruled).isNotNull()
        assertThat(ruled!!.day).isNull()
        assertThat(ruled.rule).isEqualTo(Rule.NthWeekday(DayOfWeek.SATURDAY, -1))
    }

    @Test
    fun observance_rejectsOutOfRangeMonthAndUnknownRule() {
        assertThat(WidgetCalendarData.observance(month = 0, day = 1, name = "Nope")).isNull()
        assertThat(WidgetCalendarData.observance(month = 13, day = 1, name = "Nope")).isNull()

        val unknownRule = WidgetCalendarData.observance(month = 5, day = 9, name = "X", ruleKind = "doesNotExist")
        assertThat(unknownRule!!.rule).isNull()
        assertThat(unknownRule.day).isEqualTo(9)
    }

    @Test
    fun parseRule_mapsKnownKindsAndRejectsMalformedWeekday() {
        assertThat(WidgetCalendarData.parseRule("thirdSaturdayOfMonth", null))
            .isEqualTo(Rule.NthWeekday(DayOfWeek.SATURDAY, 3))
        assertThat(WidgetCalendarData.parseRule("firstSundayOfMonth", null))
            .isEqualTo(Rule.NthWeekday(DayOfWeek.SUNDAY, 1))
        assertThat(WidgetCalendarData.parseRule("lastWeekdayOfMonth", "NOT_A_DAY")).isNull()
        assertThat(WidgetCalendarData.parseRule(null, null)).isNull()
    }
}
