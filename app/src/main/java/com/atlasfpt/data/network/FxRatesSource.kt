package com.atlasfpt.data.network

import com.atlasfpt.domain.model.FxFetchResult

interface FxRatesSource {
    suspend fun fetch(): FxFetchResult
}
