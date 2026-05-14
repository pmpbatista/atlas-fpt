package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.FinancialRepository
import com.atlasfpt.domain.model.FinancialLot
import com.atlasfpt.domain.model.TickerQuote
import javax.inject.Inject

class SaveFinancialAssetUseCase @Inject constructor(
    private val repo: FinancialRepository,
) {
    suspend operator fun invoke(
        name: String,
        notes: String?,
        quote: TickerQuote,
        firstLot: FinancialLot,
    ): Long = repo.createAssetWithFirstLot(name, notes, quote, firstLot)
}
