package com.atlasfpt.data.settings

data class AppSettings(
    val currencySymbol: String = "€",
    val currencyCode: String = "EUR",
    val backgroundRefreshEnabled: Boolean = true,
    val lastPriceRefreshAt: Long? = null,
    /**
     * Currency used to display converted aggregates (e.g. net wealth grand total).
     * Defaults to [currencyCode] when not explicitly set.
     */
    val displayCurrencyCode: String = "EUR",
)
