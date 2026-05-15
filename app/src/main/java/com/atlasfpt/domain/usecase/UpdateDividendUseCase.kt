package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.DividendRepository
import com.atlasfpt.domain.model.Dividend
import javax.inject.Inject

class UpdateDividendUseCase @Inject constructor(
    private val repo: DividendRepository,
) {
    suspend operator fun invoke(assetId: Long, dividend: Dividend) =
        repo.update(assetId, dividend)
}
