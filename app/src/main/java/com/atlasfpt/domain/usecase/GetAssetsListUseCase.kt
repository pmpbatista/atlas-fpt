package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.AssetRepository
import com.atlasfpt.domain.model.AssetListItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAssetsListUseCase @Inject constructor(
    private val repo: AssetRepository,
) {
    operator fun invoke(): Flow<List<AssetListItem>> = repo.observeAssetList()
}
