package com.atlasfpt.domain.usecase

import com.atlasfpt.domain.model.Dividend
import com.atlasfpt.domain.model.FinancialAsset
import com.atlasfpt.domain.model.FinancialLot
import com.atlasfpt.domain.model.LotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

class ComputeFinancialReturnsUseCaseDividendsTest {

    private val compute = ComputeFinancialReturnsUseCase()

    @Test
    fun `dividend income adds to total return`() {
        val asset = FinancialAsset(
            id = 1L,
            name = "Test", ticker = "TST", displayName = "T",
            currencyCode = "EUR",
            latestPrice = 10.0,
            latestPriceAt = null,
            notes = null,
            lots = listOf(
                FinancialLot(1, LocalDate.of(2026, 1, 1), 100.0, 10.0, LotType.BUY),
            ),
            dividends = listOf(
                Dividend(1, LocalDate.of(2026, 6, 1), 50.0),
                Dividend(2, LocalDate.of(2026, 12, 1), 30.0),
            ),
        )
        val r = compute(asset, today = LocalDate.of(2027, 1, 1))
        assertEquals(0.0, r.realizedPnl, 0.0001)
        assertEquals(80.0, r.dividendIncome, 0.0001)
        assertEquals(0.0, r.unrealizedPnl!!, 0.0001)
        assertEquals(80.0, r.totalReturn!!, 0.0001)
        assertEquals(0.08, r.totalReturnPct!!, 0.0001) // 80 / 1000
    }

    @Test
    fun `dividend flows feed XIRR`() {
        val asset = FinancialAsset(
            id = 1L,
            name = "Test", ticker = "TST", displayName = "T",
            currencyCode = "EUR",
            latestPrice = 10.0,
            latestPriceAt = null,
            notes = null,
            lots = listOf(
                FinancialLot(1, LocalDate.of(2026, 1, 1), 100.0, 10.0, LotType.BUY),
            ),
            dividends = listOf(
                Dividend(1, LocalDate.of(2027, 1, 1), 100.0),
            ),
        )
        val r = compute(asset, today = LocalDate.of(2027, 1, 1))
        // -1000 today, +1000 (terminal) + 100 (dividend) one year later → ~10%
        assertNotNull(r.xirr)
        assertEquals(0.10, r.xirr!!, 0.01)
    }
}
