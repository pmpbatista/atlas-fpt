package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.AssetRepository
import com.atlasfpt.data.repository.FxRatesRepository
import com.atlasfpt.data.settings.SettingsRepository
import com.atlasfpt.domain.model.FxRate
import com.atlasfpt.domain.model.TotalWealth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class GetTotalWealthUseCase @Inject constructor(
    private val repo: AssetRepository,
    private val fxRatesRepository: FxRatesRepository,
    private val settingsRepository: SettingsRepository,
    private val convert: ConvertCurrencyUseCase,
) {
    operator fun invoke(): Flow<TotalWealth> = combine(
        repo.observeAssetList(),
        fxRatesRepository.observeRates(),
        settingsRepository.settings,
    ) { items, rates, settings ->
        val byTypeAndCurrency = items.groupBy { it.type }
            .mapValues { (_, group) ->
                group.groupBy { it.currencyCode }
                    .mapValues { (_, list) -> list.sumOf { it.equity } }
            }
        val countByType = items.groupBy { it.type }
            .mapValues { (_, g) -> g.size }

        val display = settings.displayCurrencyCode
        val byCurrency = byTypeAndCurrency.values
            .flatMap { it.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, values) -> values.sum() }

        val (totalInDisplay, fxFetchedAt) = convertTotal(byCurrency, display, rates)

        TotalWealth(
            byTypeAndCurrency = byTypeAndCurrency,
            countByType = countByType,
            totalInDisplayCurrency = totalInDisplay,
            displayCurrencyCode = display,
            fxFetchedAt = fxFetchedAt,
        )
    }

    private fun convertTotal(
        byCurrency: Map<String, Double>,
        display: String,
        rates: Map<String, FxRate>,
    ): Pair<Double?, Long?> {
        if (byCurrency.isEmpty()) return null to null
        var sum = 0.0
        var earliest: Long? = null
        for ((code, amount) in byCurrency) {
            val converted = convert(amount, code, display, rates) ?: return null to null
            sum += converted
            if (code != display) {
                val rate = rates[code] ?: rates[display]
                if (rate != null) {
                    earliest = earliest?.let { minOf(it, rate.fetchedAt) } ?: rate.fetchedAt
                }
            }
        }
        return sum to earliest
    }
}
