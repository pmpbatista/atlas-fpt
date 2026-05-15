package com.atlasfpt.data.network

import com.atlasfpt.domain.model.ChartRange
import com.atlasfpt.domain.model.PricePoint
import com.atlasfpt.domain.model.QuoteResult
import com.atlasfpt.domain.model.SearchResult

interface PriceSource {
    suspend fun fetchQuote(ticker: String): QuoteResult
    suspend fun searchTickers(query: String): SearchResult
    suspend fun fetchHistory(ticker: String, range: ChartRange): List<PricePoint>
}
