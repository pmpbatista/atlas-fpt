package com.atlasfpt.domain.usecase

import com.atlasfpt.domain.model.FinancialLot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

class CalculateYieldUseCaseTest {

    private val today = LocalDate.of(2026, 5, 6)

    private fun lot(date: LocalDate, qty: Double, price: Double, id: Long = 1L) =
        FinancialLot(id = id, purchaseDate = date, quantity = qty, pricePerUnit = price)

    @Test fun `empty lots returns null`() {
        assertNull(calculateAvgYearlyYield(emptyList(), 100.0, today))
    }

    @Test fun `null current price returns null`() {
        assertNull(calculateAvgYearlyYield(listOf(lot(today.minusYears(1), 1.0, 100.0)), null, today))
    }

    @Test fun `single lot held 1 year with 10pct gain returns 10pct`() {
        val result = calculateAvgYearlyYield(
            listOf(lot(today.minusYears(1), 1.0, 100.0)), 110.0, today
        )!!
        assertTrue("expected ~0.10, got $result", abs(result - 0.10) < 0.005)
    }

    @Test fun `single lot held half year with 10pct gain annualizes up`() {
        val result = calculateAvgYearlyYield(
            listOf(lot(today.minusDays(183), 1.0, 100.0)), 110.0, today
        )!!
        // 1.10^2 - 1 = 0.21
        assertTrue("expected ~0.21, got $result", abs(result - 0.21) < 0.01)
    }

    @Test fun `single lot held 2 years with 21pct gain annualizes down to ~10pct`() {
        val result = calculateAvgYearlyYield(
            listOf(lot(today.minusYears(2), 1.0, 100.0)), 121.0, today
        )!!
        // 1.21^0.5 - 1 ≈ 0.10
        assertTrue("expected ~0.10, got $result", abs(result - 0.10) < 0.01)
    }

    @Test fun `today-purchased lot excluded`() {
        // Two lots: one today (excluded), one a year ago (10% gain) — answer should be ~0.10
        val result = calculateAvgYearlyYield(
            listOf(
                lot(today, 1.0, 100.0, id = 1L),
                lot(today.minusYears(1), 1.0, 100.0, id = 2L),
            ),
            110.0, today
        )!!
        assertTrue("expected ~0.10, got $result", abs(result - 0.10) < 0.005)
    }

    @Test fun `only-today lots returns null`() {
        assertNull(calculateAvgYearlyYield(listOf(lot(today, 1.0, 100.0)), 110.0, today))
    }

    @Test fun `negative ratio returns minus 100pct`() {
        val result = calculateAvgYearlyYield(
            listOf(lot(today.minusYears(1), 1.0, 100.0)), 0.0, today
        )!!
        assertTrue("expected ~-1.0, got $result", abs(result - -1.0) < 0.001)
    }

    @Test fun `zero-cost lot excluded`() {
        // Free lot (price=0) excluded; the other lot drives the result
        val result = calculateAvgYearlyYield(
            listOf(
                lot(today.minusYears(1), 1.0, 0.0, id = 1L),
                lot(today.minusYears(1), 1.0, 100.0, id = 2L),
            ),
            110.0, today
        )!!
        assertTrue("expected ~0.10, got $result", abs(result - 0.10) < 0.005)
    }

    @Test fun `weighted average across two lots`() {
        // Both lots: 1 year hold, +10% gain, so each lot's yield is 0.10. Weighted: ((0.10*100) + (0.10*300))/400 = 0.10
        val result = calculateAvgYearlyYield(
            listOf(
                lot(today.minusYears(1), 1.0, 100.0, id = 1L),  // cost 100, yield 0.10
                lot(today.minusYears(1), 3.0, 100.0, id = 2L),  // cost 300, yield 0.10
            ),
            110.0, today
        )!!
        assertTrue("expected ~0.10, got $result", abs(result - 0.10) < 0.005)
    }
}
