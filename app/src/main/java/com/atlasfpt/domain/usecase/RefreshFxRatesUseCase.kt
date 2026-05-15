package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.FxRatesRepository
import com.atlasfpt.domain.model.FxFetchResult
import javax.inject.Inject

class RefreshFxRatesUseCase @Inject constructor(
    private val repository: FxRatesRepository,
) {
    suspend operator fun invoke(): FxFetchResult = repository.refresh()
}
