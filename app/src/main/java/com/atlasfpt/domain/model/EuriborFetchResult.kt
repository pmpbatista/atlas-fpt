package com.atlasfpt.domain.model

sealed interface EuriborFetchResult {
    data class Success(val rate: EuriborRate) : EuriborFetchResult
    data class Error(val reason: String) : EuriborFetchResult
}
