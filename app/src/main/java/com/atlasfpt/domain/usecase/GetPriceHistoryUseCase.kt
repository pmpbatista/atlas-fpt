package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.PriceRepository
import com.atlasfpt.domain.model.ChartRange
import com.atlasfpt.domain.model.PricePoint
import javax.inject.Inject

class GetPriceHistoryUseCase @Inject constructor(
    private val priceRepository: PriceRepository,
) {
    suspend operator fun invoke(ticker: String, range: ChartRange): List<PricePoint> =
        priceRepository.fetchHistory(ticker, range)
}
