package com.atlasfpt.domain.model

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
    val dividends: List<Dividend> = emptyList(),
) {
    val totalQuantity: Double get() = lots.sumOf {
        if (it.type == LotType.SELL) -it.quantity else it.quantity
    }
    val currentValue: Double? get() = latestPrice?.let { it * totalQuantity }
}
