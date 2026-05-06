package com.spendtrack.domain.usecase

import com.spendtrack.data.repository.FinancialRepository
import com.spendtrack.domain.model.FinancialLot
import javax.inject.Inject

class AddLotUseCase @Inject constructor(
    private val repo: FinancialRepository,
) {
    suspend operator fun invoke(assetId: Long, lot: FinancialLot): Long =
        repo.addLot(assetId, lot)
}
