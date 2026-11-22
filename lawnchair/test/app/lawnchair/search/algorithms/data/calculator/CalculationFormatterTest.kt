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

package app.lawnchair.search.algorithms.data.calculator

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Covers the formatting of the result shown under the search bar. */
class CalculationFormatterTest {

    @Test
    fun `a valid query keeps the equation and formats the result`() {
        val calculation = CalculationFormatter.format("1 + 2")

        assertThat(calculation.isValid).isTrue()
        assertThat(calculation.equation).isEqualTo("1 + 2")
        assertThat(calculation.result).isEqualTo("3")
    }

    @Test
    fun `trailing zeros are stripped`() {
        assertThat(CalculationFormatter.format("2 / 4").result).isEqualTo("0.5")
        assertThat(CalculationFormatter.format("4 / 2").result).isEqualTo("2")
    }

    @Test
    fun `plain notation is used for ordinary magnitudes`() {
        // 1e15 stays readable, so it must not be rendered as 1E+15.
        assertThat(CalculationFormatter.format("1000000 * 1000000").result).isEqualTo("1000000000000")
    }

    @Test
    fun `scientific notation is used above the threshold`() {
        val result = CalculationFormatter.format("99999999 * 999999999").result

        assertThat(result).contains("E")
    }

    @Test
    fun `an invalid query is reported as invalid`() {
        val calculation = CalculationFormatter.format("1 +")

        assertThat(calculation.isValid).isFalse()
        assertThat(calculation.equation).isEmpty()
        assertThat(calculation.result).isEmpty()
    }

    @Test
    fun `division by zero is invalid rather than infinite`() {
        assertThat(CalculationFormatter.format("1 / 0").isValid).isFalse()
    }

    @Test
    fun `a blank query is invalid`() {
        assertThat(CalculationFormatter.format("").isValid).isFalse()
        assertThat(CalculationFormatter.format("   ").isValid).isFalse()
    }

    @Test
    fun `a shared Expressions instance is reused across calls`() {
        val expressions = Expressions()
        expressions.define("answer", 42)

        assertThat(CalculationFormatter.format("answer * 2", expressions).result).isEqualTo("84")
    }
}
