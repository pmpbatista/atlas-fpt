package com.atlasfpt.data.settings

data class AppSettings(
    val currencySymbol: String = "€",
    val currencyCode: String = "EUR",
    val backgroundRefreshEnabled: Boolean = true,
    val lastPriceRefreshAt: Long? = null,
)
