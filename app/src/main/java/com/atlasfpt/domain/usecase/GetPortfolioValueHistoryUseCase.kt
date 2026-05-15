package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.AssetRepository
import com.atlasfpt.data.repository.FinancialRepository
import com.atlasfpt.data.repository.PriceRepository
import com.atlasfpt.domain.model.AssetType
import com.atlasfpt.domain.model.ChartRange
import com.atlasfpt.domain.model.FxRate
import com.atlasfpt.domain.model.LotType
import com.atlasfpt.domain.model.PricePoint
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

class GetPortfolioValueHistoryUseCase @Inject constructor(
    private val assetRepository: AssetRepository,
    private val financialRepository: FinancialRepository,
    private val priceRepository: PriceRepository,
    private val convert: ConvertCurrencyUseCase,
) {
    suspend operator fun invoke(
        range: ChartRange,
        displayCurrency: String,
        rates: Map<String, FxRate>,
    ): List<PricePoint> = coroutineScope {
        val items = assetRepository.observeAssetList().first()
        val financialItems = items.filter { it.type == AssetType.FINANCIAL }
        if (financialItems.isEmpty()) return@coroutineScope emptyList()

        val assets = financialItems.map { item ->
            async {
                val asset = financialRepository.getById(item.id) ?: return@async null
                val history = priceRepository.fetchHistory(asset.ticker, range)
                Triple(asset, item.currencyCode, history)
            }
        }.mapNotNull { it.await() }.filter { it.third.isNotEmpty() }

        if (assets.isEmpty()) return@coroutineScope emptyList()

        val allDates = assets.flatMap { (_, _, h) -> h.map { it.date } }.toSortedSet().toList()
        val realEstateConstant = items.filter { it.type == AssetType.REAL_ESTATE }
            .sumOf { item ->
                convert(item.equity, item.currencyCode, displayCurrency, rates) ?: 0.0
            }

        allDates.map { date ->
            var total = realEstateConstant
            for ((asset, currency, history) in assets) {
                val priceOnOrBefore = history.lastOrNull { it.date <= date }?.price ?: continue
                val netQty = netQuantityAt(asset.lots, date)
                if (netQty == 0.0) continue
                val nativeValue = priceOnOrBefore * netQty
                val converted = convert(nativeValue, currency, displayCurrency, rates) ?: continue
                total += converted
            }
            PricePoint(date = date, price = total)
        }
    }

    private fun netQuantityAt(lots: List<com.atlasfpt.domain.model.FinancialLot>, date: LocalDate): Double {
        var net = 0.0
        for (lot in lots) {
            if (lot.purchaseDate > date) continue
            net += if (lot.type == LotType.BUY) lot.quantity else -lot.quantity
        }
        return net
    }
}
