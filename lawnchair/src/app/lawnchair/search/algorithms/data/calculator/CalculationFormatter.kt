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

import app.lawnchair.search.algorithms.data.Calculation
import java.math.BigDecimal
import java.math.MathContext

/**
 * Turns a raw query into the [Calculation] shown under the search bar.
 *
 * Kept free of Android types so the result formatting can be unit tested: it used to live inside
 * [app.lawnchair.search.algorithms.engine.provider.CalculatorSearchProvider], which needs a
 * `Context` and therefore could not be exercised off device.
 */
object CalculationFormatter {

    /**
     * Above this magnitude [BigDecimal.toPlainString] produces a wall of digits that is useless in
     * a search result, so the value is shown in scientific notation instead.
     */
    private val PLAIN_STRING_THRESHOLD = BigDecimal("9999999999999999")

    fun format(query: String, expressions: Expressions = Expressions()): Calculation = try {
        val rounded = expressions.eval(query).round(MathContext.DECIMAL64).stripTrailingZeros()
        val result = if (rounded.abs() > PLAIN_STRING_THRESHOLD) {
            rounded.toString()
        } else {
            rounded.toPlainString()
        }
        Calculation(equation = query, result = result, isValid = true)
    } catch (_: Exception) {
        Calculation(equation = "", result = "", isValid = false)
    }
}
