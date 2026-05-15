package com.atlasfpt.domain.model

/**
 * Currency exchange rate anchored on EUR (the ECB reference currency).
 *
 * `unitsPerEur` follows the ECB convention: how many units of `currencyCode` equal 1 EUR.
 * Example: for USD with rate 1.0780, `unitsPerEur = 1.0780` — 1 EUR = 1.0780 USD.
 *
 * EUR is implicit and never stored as a row; identity conversions are handled by the use case.
 */
data class FxRate(
    val currencyCode: String,
    val unitsPerEur: Double,
    val fetchedAt: Long,
)
