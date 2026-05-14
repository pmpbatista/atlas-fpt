package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.PriceRepository
import com.atlasfpt.domain.model.SearchResult
import javax.inject.Inject

class SearchTickersUseCase @Inject constructor(
    private val priceRepository: PriceRepository,
) {
    suspend operator fun invoke(query: String): SearchResult = priceRepository.searchTickers(query)
}
