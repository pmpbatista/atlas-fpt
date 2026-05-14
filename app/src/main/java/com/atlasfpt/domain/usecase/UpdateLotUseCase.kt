package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.FinancialRepository
import com.atlasfpt.domain.model.FinancialLot
import javax.inject.Inject

class UpdateLotUseCase @Inject constructor(
    private val repo: FinancialRepository,
) {
    suspend operator fun invoke(assetId: Long, lot: FinancialLot) =
        repo.updateLot(assetId, lot)
}
