package com.atlasfpt.data.network

import com.atlasfpt.domain.model.QuoteResult

/** Encapsulates the price-fetching backend so it can be swapped without touching callers. */
interface PriceSource {
    suspend fun fetchQuote(ticker: String): QuoteResult
}
