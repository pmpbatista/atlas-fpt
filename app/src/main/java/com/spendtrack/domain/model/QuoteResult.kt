package com.spendtrack.domain.model

sealed interface QuoteResult {
    data class Success(val quote: TickerQuote) : QuoteResult
    data object NotFound : QuoteResult
    data class Error(val cause: String) : QuoteResult
}
