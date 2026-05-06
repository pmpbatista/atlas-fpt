package com.spendtrack.domain.usecase

import com.spendtrack.data.repository.FinancialRepository
import javax.inject.Inject

class DeleteLotUseCase @Inject constructor(
    private val repo: FinancialRepository,
) {
    /** Returns true if the entire asset was deleted (last lot removed). */
    suspend operator fun invoke(assetId: Long, lotId: Long): Boolean =
        repo.deleteLot(assetId, lotId)
}
