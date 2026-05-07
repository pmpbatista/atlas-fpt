package com.spendtrack.data.network

import com.spendtrack.domain.model.QuoteResult

/** Encapsulates the price-fetching backend so it can be swapped without touching callers. */
interface PriceSource {
    suspend fun fetchQuote(ticker: String): QuoteResult
}
