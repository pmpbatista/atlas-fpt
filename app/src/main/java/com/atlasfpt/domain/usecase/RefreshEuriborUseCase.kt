package com.atlasfpt.domain.usecase

import com.atlasfpt.data.network.EuriborSource
import com.atlasfpt.data.repository.EuriborRepository
import com.atlasfpt.domain.model.EuriborFetchResult
import com.atlasfpt.domain.model.ReferenceRate
import javax.inject.Inject

class RefreshEuriborUseCase @Inject constructor(
    private val source: EuriborSource,
    private val repository: EuriborRepository,
) {
    suspend operator fun invoke() {
        ReferenceRate.values().forEach { tenor ->
            when (val result = source.fetch(tenor)) {
                is EuriborFetchResult.Success -> repository.put(result.rate)
                is EuriborFetchResult.Error -> Unit
            }
        }
    }
}
