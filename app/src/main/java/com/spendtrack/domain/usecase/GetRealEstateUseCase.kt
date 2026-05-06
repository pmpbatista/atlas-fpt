package com.spendtrack.domain.usecase

import com.spendtrack.data.repository.RealEstateRepository
import com.spendtrack.domain.model.RealEstateAsset
import javax.inject.Inject

class GetRealEstateUseCase @Inject constructor(
    private val repo: RealEstateRepository,
) {
    suspend operator fun invoke(id: Long): RealEstateAsset? = repo.getById(id)
}
