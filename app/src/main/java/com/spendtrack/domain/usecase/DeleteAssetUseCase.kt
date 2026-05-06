package com.spendtrack.domain.usecase

import com.spendtrack.data.repository.AssetRepository
import javax.inject.Inject

class DeleteAssetUseCase @Inject constructor(
    private val repo: AssetRepository,
) {
    suspend operator fun invoke(id: Long) = repo.deleteById(id)
}
