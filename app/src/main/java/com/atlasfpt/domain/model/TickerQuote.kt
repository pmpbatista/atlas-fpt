package com.atlasfpt.domain.model

import java.time.Instant

data class TickerQuote(
    val ticker: String,
    val displayName: String,
    val currencyCode: String,
    val price: Double,
    val asOf: Instant,
)
