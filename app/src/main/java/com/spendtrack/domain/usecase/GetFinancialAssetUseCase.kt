package com.spendtrack.domain.usecase

import com.spendtrack.data.repository.FinancialRepository
import com.spendtrack.domain.model.FinancialAsset
import javax.inject.Inject

class GetFinancialAssetUseCase @Inject constructor(
    private val repo: FinancialRepository,
) {
    suspend operator fun invoke(id: Long): FinancialAsset? = repo.getById(id)
}
