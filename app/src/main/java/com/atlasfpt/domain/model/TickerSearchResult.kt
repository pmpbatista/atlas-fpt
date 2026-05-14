package com.atlasfpt.domain.model

data class TickerSearchResult(
    val symbol: String,
    val displayName: String,
    val exchange: String?,
    val typeLabel: String?,
)

sealed interface SearchResult {
    data object Empty : SearchResult
    data object Loading : SearchResult
    data class Success(val items: List<TickerSearchResult>) : SearchResult
    data object NoMatches : SearchResult
    data class Error(val reason: String) : SearchResult
}
