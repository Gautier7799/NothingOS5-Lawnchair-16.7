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
import java.math.BigDecimal
import java.math.RoundingMode
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Covers the expression engine behind the calculator search result. The engine is plain Kotlin
 * with no Android dependencies, so it is exercised directly on the JVM.
 *
 * Results are compared with [BigDecimal.compareTo] rather than equality, because equality is
 * scale sensitive: `BigDecimal("10")` and `BigDecimal("1E+1")` are the same number but not equal.
 */
class ExpressionsTest {

    private fun eval(expression: String): BigDecimal = Expressions().eval(expression)

    private fun assertEval(expected: String, expression: String) {
        assertThat(eval(expression).compareTo(BigDecimal(expected))).isEqualTo(0)
    }

    @Test
    fun `addition and subtraction`() {
        assertEval("3", "1 + 2")
        assertEval("6", "10 - 4")
        assertEval("10", "1 + 2 + 3 + 4")
    }

    @Test
    fun `multiplication and division`() {
        assertEval("42", "6 * 7")
        assertEval("5", "20 / 4")
        assertEval("24", "2 * 3 * 4")
    }

    @Test
    fun `multiplication binds tighter than addition`() {
        assertEval("14", "2 + 3 * 4")
        assertEval("10", "2 * 3 + 4")
        assertEval("11", "2 + 3 * 4 - 6 / 2")
    }

    @Test
    fun `parentheses override precedence`() {
        assertEval("20", "(2 + 3) * 4")
        assertEval("21", "((1 + 2) * (3 + 4))")
    }

    @Test
    fun `exponent is right associative`() {
        assertEval("1024", "2 ^ 10")
        // 2^(3^2) = 2^9 = 512, not (2^3)^2 = 64.
        assertEval("512", "2 ^ 3 ^ 2")
    }

    @Test
    fun `unary minus applies to the following term`() {
        assertEval("-5", "-5")
        assertEval("-2", "-5 + 3")
        assertEval("-6", "3 * -2")
    }

    @Test
    fun `square root operator`() {
        assertEval("3", "√9")
        assertEval("4", "√(16)")
    }

    @Test
    fun `modulo returns the remainder`() {
        assertEval("1", "10 % 3")
        assertEval("0", "10 % 5")
    }

    @Test
    fun `comparisons yield one or zero`() {
        assertEval("1", "1 < 2")
        assertEval("0", "2 < 1")
        assertEval("1", "2 == 2")
        assertEval("0", "2 != 2")
        assertEval("1", "3 >= 3")
        assertEval("0", "2 > 5")
        assertEval("0", "2 <= 1")
    }

    @Test
    fun `logical operators short circuit`() {
        assertEval("1", "1 && 1")
        assertEval("0", "1 && 0")
        assertEval("1", "0 || 1")
        assertEval("0", "0 || 0")
    }

    @Test
    fun `constants pi and e are defined`() {
        assertThat(eval("pi").toDouble()).isWithin(1e-9).of(Math.PI)
        assertThat(eval("e").toDouble()).isWithin(1e-9).of(Math.E)
    }

    @Test
    fun `built in functions`() {
        assertEval("7", "abs(-7)")
        assertEval("6", "sum(1, 2, 3)")
        assertEval("2", "floor(2.9)")
        assertEval("3", "ceil(2.1)")
        assertEval("3", "round(2.6)")
        assertEval("1", "min(3, 1, 2)")
        assertEval("3", "max(3, 1, 2)")
    }

    /**
     * `round` uses the [RoundingMode] of the [java.math.MathContext] it runs in, and the default is
     * HALF_EVEN (banker's rounding), not HALF_UP. 2.345 therefore rounds down to 2.34 because the
     * preceding digit 4 is even. Users typing a sum expect 2.35, so this pins the current behavior
     * and documents the surprise instead of leaving it silent.
     */
    @Test
    fun `round uses half even by default`() {
        assertEval("2.34", "round(2.345, 2)")
        assertEval("2.36", "round(2.355, 2)")

        val halfUp = Expressions().setRoundingMode(RoundingMode.HALF_UP)
        assertThat(halfUp.eval("round(2.345, 2)").toPlainString()).isEqualTo("2.35")
    }

    @Test
    fun `if picks a branch from a comparison`() {
        assertEval("10", "if(1 < 2, 10, 20)")
        assertEval("20", "if(1 > 2, 10, 20)")
    }

    @Test
    fun `variables can be defined and reassigned`() {
        val expressions = Expressions()
        expressions.define("x", 5)
        assertThat(expressions.eval("x * 2").compareTo(BigDecimal(10))).isEqualTo(0)

        expressions.eval("x = 7")
        assertThat(expressions.eval("x").compareTo(BigDecimal(7))).isEqualTo(0)
    }

    @Test
    fun `decimal literals and scientific notation`() {
        assertEval("3", "1.5 + 1.5")
        assertEval("1000", "1e3")
        assertEval("0.25", "2.5e-1")
    }

    @Test
    fun `division by zero throws instead of returning infinity`() {
        assertThrows(ArithmeticException::class.java) {
            Expressions().eval("1 / 0")
        }
    }

    @Test
    fun `undefined variable and function throw`() {
        assertThrows(ExpressionException::class.java) {
            Expressions().eval("unknownName")
        }
        assertThrows(ExpressionException::class.java) {
            Expressions().eval("unknownFunction(1)")
        }
    }

    @Test
    fun `malformed input throws`() {
        assertThrows(ExpressionException::class.java) { Expressions().eval("1 +") }
        assertThrows(ExpressionException::class.java) { Expressions().eval("(1 + 2") }
        assertThrows(ExpressionException::class.java) { Expressions().eval("1 2") }
        assertThrows(ExpressionException::class.java) { Expressions().eval("1 & 2") }
    }

    @Test
    fun `setPrecision controls the number of significant digits`() {
        val expressions = Expressions().setPrecision(4)
        assertThat(expressions.precision).isEqualTo(4)
        assertThat(expressions.eval("1 / 3").toPlainString()).isEqualTo("0.3333")
    }

    @Test
    fun `setRoundingMode controls how results are rounded`() {
        val expressions = Expressions().setPrecision(2).setRoundingMode(RoundingMode.DOWN)
        assertThat(expressions.roundingMode).isEqualTo(RoundingMode.DOWN)
        assertThat(expressions.eval("2 / 3").toPlainString()).isEqualTo("0.66")
    }

    @Test
    fun `evalToString formats a result and reports errors as text`() {
        val expressions = Expressions()
        assertThat(expressions.evalToString("1 + 1")).isEqualTo("2")
        assertThat(expressions.evalToString("1 +")).isNotEmpty()
        assertThat(expressions.evalToString("1 +")).isNotEqualTo("2")
    }

    @Test
    fun `large results are not truncated`() {
        assertEval("9999999800000001", "99999999 * 99999999")
    }
}
