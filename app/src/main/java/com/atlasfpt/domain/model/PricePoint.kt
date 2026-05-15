package com.atlasfpt.domain.model

import java.time.LocalDate

data class PricePoint(
    val date: LocalDate,
    val price: Double,
)

enum class ChartRange(val yahooRange: String, val yahooInterval: String, val label: String) {
    ONE_MONTH("1mo", "1d", "1M"),
    SIX_MONTHS("6mo", "1d", "6M"),
    ONE_YEAR("1y", "1d", "1Y"),
    ALL("max", "1wk", "All"),
}
