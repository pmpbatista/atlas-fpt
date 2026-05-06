package com.spendtrack.domain.usecase

import com.spendtrack.data.repository.AssetRepository
import com.spendtrack.domain.model.AssetListItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAssetsListUseCase @Inject constructor(
    private val repo: AssetRepository,
) {
    operator fun invoke(): Flow<List<AssetListItem>> = repo.observeAssetList()
}
