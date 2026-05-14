package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.AssetRepository
import com.atlasfpt.domain.model.TotalWealth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetTotalWealthUseCase @Inject constructor(
    private val repo: AssetRepository,
) {
    operator fun invoke(): Flow<TotalWealth> = repo.observeAssetList().map { items ->
        val byCurrency = items.groupBy { it.currencyCode }
            .mapValues { (_, list) -> list.sumOf { it.equity } }
        TotalWealth(byCurrency = byCurrency, assetCount = items.size)
    }
}
