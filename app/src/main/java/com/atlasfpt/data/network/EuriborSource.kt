package com.atlasfpt.data.network

import com.atlasfpt.domain.model.EuriborFetchResult
import com.atlasfpt.domain.model.ReferenceRate

interface EuriborSource {
    suspend fun fetch(tenor: ReferenceRate): EuriborFetchResult
}
