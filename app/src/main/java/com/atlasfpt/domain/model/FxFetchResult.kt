package com.atlasfpt.domain.model

sealed interface FxFetchResult {
    data class Success(val rates: List<FxRate>, val asOfDate: String) : FxFetchResult
    data class Error(val message: String) : FxFetchResult
}
