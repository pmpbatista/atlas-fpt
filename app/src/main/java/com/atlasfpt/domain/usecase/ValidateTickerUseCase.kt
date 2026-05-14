package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.PriceRepository
import com.atlasfpt.domain.model.QuoteResult
import javax.inject.Inject

class ValidateTickerUseCase @Inject constructor(
    private val priceRepository: PriceRepository,
) {
    suspend operator fun invoke(ticker: String): QuoteResult =
        priceRepository.validateTicker(ticker.trim().uppercase())
}
