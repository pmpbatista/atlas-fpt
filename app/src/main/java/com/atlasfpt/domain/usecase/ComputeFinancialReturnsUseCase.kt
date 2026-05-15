package com.atlasfpt.domain.usecase

import com.atlasfpt.domain.model.FinancialAsset
import com.atlasfpt.domain.model.FinancialAssetReturns
import com.atlasfpt.domain.model.FinancialLot
import com.atlasfpt.domain.model.LotType
import com.atlasfpt.util.Xirr
import java.time.LocalDate
import javax.inject.Inject

class ComputeFinancialReturnsUseCase @Inject constructor() {

    operator fun invoke(
        asset: FinancialAsset,
        today: LocalDate = LocalDate.now(),
    ): FinancialAssetReturns {
        val lots = asset.lots.sortedWith(compareBy({ it.purchaseDate }, { it.id }))
        val pool = ArrayDeque<MutableCell>()
        var realized = 0.0
        var totalInvested = 0.0

        for (lot in lots) {
            when (lot.type) {
                LotType.BUY -> {
                    pool.addLast(MutableCell(qty = lot.quantity, price = lot.pricePerUnit))
                    totalInvested += lot.quantity * lot.pricePerUnit
                }
                LotType.SELL -> {
                    var remaining = lot.quantity
                    while (remaining > 0 && pool.isNotEmpty()) {
                        val head = pool.first()
                        val take = minOf(remaining, head.qty)
                        realized += (lot.pricePerUnit - head.price) * take
                        head.qty -= take
                        remaining -= take
                        if (head.qty <= 1e-9) pool.removeFirst()
                    }
                }
            }
        }

        val netQuantity = pool.sumOf { it.qty }
        val avgCostPerRemainingShare = if (netQuantity > 0) {
            pool.sumOf { it.qty * it.price } / netQuantity
        } else 0.0
        val unrealizedPnl = asset.latestPrice?.let { price ->
            pool.sumOf { it.qty * (price - it.price) }
        }
        val totalReturn = unrealizedPnl?.let { realized + it }
        val totalReturnPct = totalReturn?.let { tr ->
            if (totalInvested > 0) tr / totalInvested else null
        }
        val xirr = computeXirr(lots, netQuantity, asset.latestPrice, today)

        return FinancialAssetReturns(
            realizedPnl = realized,
            unrealizedPnl = unrealizedPnl,
            totalReturn = totalReturn,
            totalReturnPct = totalReturnPct,
            totalInvested = totalInvested,
            xirr = xirr,
            netQuantity = netQuantity,
            avgCostPerRemainingShare = avgCostPerRemainingShare,
        )
    }

    private fun computeXirr(
        lots: List<FinancialLot>,
        netQuantity: Double,
        currentPrice: Double?,
        today: LocalDate,
    ): Double? {
        if (lots.isEmpty()) return null
        val flows = mutableListOf<Pair<LocalDate, Double>>()
        for (lot in lots) {
            val signed = when (lot.type) {
                LotType.BUY -> -lot.quantity * lot.pricePerUnit
                LotType.SELL -> lot.quantity * lot.pricePerUnit
            }
            flows += lot.purchaseDate to signed
        }
        if (netQuantity > 0 && currentPrice != null) {
            flows += today to netQuantity * currentPrice
        }
        return Xirr.solve(flows)
    }

    private class MutableCell(var qty: Double, val price: Double)
}
