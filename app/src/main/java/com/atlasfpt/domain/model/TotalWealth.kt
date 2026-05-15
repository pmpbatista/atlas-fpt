package com.atlasfpt.domain.model

data class TotalWealth(
    val byTypeAndCurrency: Map<AssetType, Map<String, Double>>,
    val countByType: Map<AssetType, Int>,
    /**
     * Grand total converted into [displayCurrencyCode] using cached FX rates,
     * or null when one or more legs could not be converted (missing rate).
     */
    val totalInDisplayCurrency: Double? = null,
    val displayCurrencyCode: String = "EUR",
    /** `fetchedAt` of the earliest rate that contributed to [totalInDisplayCurrency]. */
    val fxFetchedAt: Long? = null,
) {
    val byCurrency: Map<String, Double>
        get() = byTypeAndCurrency.values
            .flatMap { it.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, values) -> values.sum() }

    val assetCount: Int get() = countByType.values.sum()
    val isMixedCurrency: Boolean get() = byCurrency.size > 1
    val isEmpty: Boolean get() = byTypeAndCurrency.isEmpty()

    fun hasType(type: AssetType): Boolean = (countByType[type] ?: 0) > 0
    fun byCurrencyForType(type: AssetType): Map<String, Double> = byTypeAndCurrency[type].orEmpty()
}
