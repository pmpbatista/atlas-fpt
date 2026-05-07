package com.spendtrack.domain.model

import java.time.Instant

data class FinancialAsset(
    val id: Long,
    val name: String,
    val ticker: String,
    val displayName: String,
    val currencyCode: String,
    val latestPrice: Double?,
    val latestPriceAt: Instant?,
    val notes: String?,
    val lots: List<FinancialLot>,
) {
    val totalQuantity: Double get() = lots.sumOf { it.quantity }
    val totalCost: Double get() = lots.sumOf { it.quantity * it.pricePerUnit }
    val avgCostPerUnit: Double
        get() = if (totalQuantity > 0) totalCost / totalQuantity else 0.0
    val currentValue: Double? get() = latestPrice?.let { it * totalQuantity }
    val unrealizedPnl: Double? get() = currentValue?.let { it - totalCost }
    val unrealizedPnlPct: Double?
        get() = currentValue?.let { (it - totalCost) / (totalCost.takeIf { c -> c > 0 } ?: return@let null) }
}
