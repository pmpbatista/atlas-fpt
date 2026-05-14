package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.PriceRepository
import com.atlasfpt.data.repository.RefreshResult
import javax.inject.Inject

class RefreshPricesUseCase @Inject constructor(
    private val priceRepository: PriceRepository,
) {
    suspend operator fun invoke(): RefreshResult = priceRepository.refreshAll()
}
