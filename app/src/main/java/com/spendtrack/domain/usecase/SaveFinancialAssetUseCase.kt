package com.spendtrack.domain.usecase

import com.spendtrack.data.repository.FinancialRepository
import com.spendtrack.domain.model.FinancialLot
import com.spendtrack.domain.model.TickerQuote
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
