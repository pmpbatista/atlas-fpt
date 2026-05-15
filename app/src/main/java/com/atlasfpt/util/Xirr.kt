package com.atlasfpt.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow

object Xirr {

    /**
     * Solve for the annualised rate r such that NPV(r) = 0 over [flows], compounded daily
     * (365-day year). Returns null when the cash flows can't bracket a root in [-0.99, 100].
     *
     * `flows` are (date, signed amount). Bisection: simple, robust, deterministic — and
     * adequate for retail-portfolio sizes.
     */
    fun solve(flows: List<Pair<LocalDate, Double>>): Double? {
        if (flows.size < 2) return null
        val base = flows.minOf { it.first }
        val days = flows.map { ChronoUnit.DAYS.between(base, it.first).toDouble() to it.second }
        if (days.none { it.second > 0 } || days.none { it.second < 0 }) return null

        fun npv(r: Double): Double {
            val factor = 1.0 + r
            if (factor <= 0.0) return Double.NaN
            return days.sumOf { (d, amount) -> amount / factor.pow(d / 365.0) }
        }

        var lo = -0.99
        var hi = 100.0
        var loVal = npv(lo)
        var hiVal = npv(hi)
        if (loVal.isNaN() || hiVal.isNaN()) return null
        if (loVal * hiVal > 0) return null

        repeat(80) {
            val mid = (lo + hi) / 2.0
            val midVal = npv(mid)
            if (midVal.isNaN()) return null
            if (kotlin.math.abs(midVal) < 1e-7 || (hi - lo) < 1e-9) return mid
            if (loVal * midVal < 0) {
                hi = mid
                hiVal = midVal
            } else {
                lo = mid
                loVal = midVal
            }
        }
        return (lo + hi) / 2.0
    }
}
