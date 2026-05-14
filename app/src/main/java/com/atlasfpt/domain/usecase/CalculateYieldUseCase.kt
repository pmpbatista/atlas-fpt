package com.atlasfpt.domain.usecase

import com.atlasfpt.domain.model.FinancialLot
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow

/**
 * Asset-level "average yearly yield" computed as per-lot CAGR weighted by lot cost.
 *
 * For each lot:
 *   years_held = (today - lot.purchaseDate) / 365.25
 *   per_lot_yield = (currentPrice / lot.pricePerUnit) ^ (1 / years_held) - 1
 *
 * Aggregated:
 *   yield = SUM(per_lot_yield * lot_cost) / SUM(lot_cost)
 *
 * Lots with years_held <= 0 (purchased today or future) are excluded — CAGR over 0 years
 * is undefined. Lots with non-positive cost are excluded to avoid weighting issues.
 *
 * Returns null if [currentPrice] is null, lots is empty, or no lot qualifies.
 */
fun calculateAvgYearlyYield(
    lots: List<FinancialLot>,
    currentPrice: Double?,
    today: LocalDate = LocalDate.now(),
): Double? {
    if (currentPrice == null || lots.isEmpty()) return null
    val perLot = lots.mapNotNull { lot ->
        val years = ChronoUnit.DAYS.between(lot.purchaseDate, today) / 365.25
        if (years <= 0.0 || lot.pricePerUnit <= 0.0) return@mapNotNull null
        val ratio = currentPrice / lot.pricePerUnit
        val yieldVal = if (ratio > 0.0) ratio.pow(1.0 / years) - 1.0 else -1.0
        val cost = lot.quantity * lot.pricePerUnit
        if (cost <= 0.0) return@mapNotNull null
        yieldVal to cost
    }
    if (perLot.isEmpty()) return null
    val totalCost = perLot.sumOf { it.second }
    return perLot.sumOf { it.first * it.second } / totalCost
}
