package com.spendtrack.domain.usecase

import com.spendtrack.data.repository.RealEstateRepository
import com.spendtrack.domain.model.RealEstateAsset
import javax.inject.Inject

class SaveRealEstateUseCase @Inject constructor(
    private val repo: RealEstateRepository,
) {
    suspend operator fun invoke(asset: RealEstateAsset): Long = repo.save(asset)
}
