package com.spendtrack.domain.usecase

import com.spendtrack.data.repository.PriceRepository
import com.spendtrack.domain.model.QuoteResult
import javax.inject.Inject

class ValidateTickerUseCase @Inject constructor(
    private val priceRepository: PriceRepository,
) {
    suspend operator fun invoke(ticker: String): QuoteResult =
        priceRepository.validateTicker(ticker.trim().uppercase())
}
