package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.RealEstateRepository
import com.atlasfpt.domain.model.RealEstateAsset
import javax.inject.Inject

class SaveRealEstateUseCase @Inject constructor(
    private val repo: RealEstateRepository,
) {
    suspend operator fun invoke(asset: RealEstateAsset): Long = repo.save(asset)
}
