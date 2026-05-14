package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.FinancialRepository
import com.atlasfpt.domain.model.FinancialAsset
import javax.inject.Inject

class GetFinancialAssetUseCase @Inject constructor(
    private val repo: FinancialRepository,
) {
    suspend operator fun invoke(id: Long): FinancialAsset? = repo.getById(id)
}
