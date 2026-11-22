package app.lawnchair.search.algorithms.engine.provider

import android.content.Context
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.search.algorithms.data.calculator.CalculationFormatter
import app.lawnchair.search.algorithms.engine.SearchProvider
import app.lawnchair.search.algorithms.engine.SearchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

object CalculatorSearchProvider : SearchProvider {

    override val id: String = "calculator"

    override fun search(
        context: Context,
        query: String,
    ): Flow<List<SearchResult>> = flow {
        val legacyPrefs = PreferenceManager.getInstance(context)

        if (query.isBlank() || !legacyPrefs.searchResultCalculator.get()) {
            emit(emptyList())
            return@flow
        }

        val calculation = CalculationFormatter.format(query)

        if (calculation.isValid) {
            val searchResult = SearchResult.Calculation(data = calculation)
            emit(listOf(searchResult))
        } else {
            emit(emptyList())
        }
    }
}
