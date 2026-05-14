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
        val byTypeAndCurrency = items.groupBy { it.type }
            .mapValues { (_, group) ->
                group.groupBy { it.currencyCode }
                    .mapValues { (_, list) -> list.sumOf { it.equity } }
            }
        val countByType = items.groupBy { it.type }
            .mapValues { (_, g) -> g.size }
        TotalWealth(byTypeAndCurrency = byTypeAndCurrency, countByType = countByType)
    }
}
