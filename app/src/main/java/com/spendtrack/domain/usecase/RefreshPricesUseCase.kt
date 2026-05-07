package com.spendtrack.domain.usecase

import com.spendtrack.data.repository.PriceRepository
import com.spendtrack.data.repository.RefreshResult
import javax.inject.Inject

class RefreshPricesUseCase @Inject constructor(
    private val priceRepository: PriceRepository,
) {
    suspend operator fun invoke(): RefreshResult = priceRepository.refreshAll()
}
