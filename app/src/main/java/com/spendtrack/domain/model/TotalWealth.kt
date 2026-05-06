package com.spendtrack.domain.model

data class TotalWealth(
    val byCurrency: Map<String, Double>,
    val assetCount: Int,
) {
    val isMixedCurrency: Boolean get() = byCurrency.size > 1
    val isEmpty: Boolean get() = byCurrency.isEmpty()
}
