package com.atlasfpt.domain.usecase

import com.atlasfpt.domain.model.FinancialAsset
import com.atlasfpt.domain.model.FinancialLot
import com.atlasfpt.domain.model.LotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ComputeFinancialReturnsUseCaseTest {

    private val compute = ComputeFinancialReturnsUseCase()

    @Test
    fun `buy only yields zero realized and unrealized at zero pnl`() {
        val asset = asset(
            latestPrice = 10.0,
            lots = listOf(buy(1, "2026-01-01", 100.0, 10.0)),
        )
        val r = compute(asset, today = LocalDate.of(2027, 1, 1))
        assertEquals(0.0, r.realizedPnl, 0.0001)
        assertEquals(0.0, r.unrealizedPnl!!, 0.0001)
        assertEquals(100.0, r.netQuantity, 0.0001)
        assertEquals(1000.0, r.totalInvested, 0.0001)
    }

    @Test
    fun `FIFO matches oldest buy on partial sale`() {
        val asset = asset(
            latestPrice = 15.0,
            lots = listOf(
                buy(1, "2026-01-01", 100.0, 10.0),
                buy(2, "2026-06-01", 50.0, 12.0),
                sell(3, "2026-09-01", 30.0, 14.0),
            ),
        )
        val r = compute(asset, today = LocalDate.of(2027, 1, 1))
        // SELL 30 @ 14 against oldest BUY (cost 10): realized = (14 - 10) * 30 = 120
        assertEquals(120.0, r.realizedPnl, 0.0001)
        // Remaining: 70 @ 10 + 50 @ 12 = 120 units, total cost basis = 700 + 600 = 1300
        assertEquals(120.0, r.netQuantity, 0.0001)
        // Unrealized at price 15: (15-10)*70 + (15-12)*50 = 350 + 150 = 500
        assertEquals(500.0, r.unrealizedPnl!!, 0.0001)
        // Total return = 120 + 500 = 620, invested = 1000 + 600 = 1600
        assertEquals(620.0, r.totalReturn!!, 0.0001)
        assertEquals(1600.0, r.totalInvested, 0.0001)
    }

    @Test
    fun `selling everything zeros net qty and unrealized`() {
        val asset = asset(
            latestPrice = 20.0,
            lots = listOf(
                buy(1, "2026-01-01", 100.0, 10.0),
                sell(2, "2026-12-01", 100.0, 12.0),
            ),
        )
        val r = compute(asset, today = LocalDate.of(2027, 1, 1))
        assertEquals(200.0, r.realizedPnl, 0.0001)
        assertEquals(0.0, r.netQuantity, 0.0001)
        assertEquals(0.0, r.unrealizedPnl!!, 0.0001)
    }

    @Test
    fun `null latest price hides unrealized but realized still populates`() {
        val asset = asset(
            latestPrice = null,
            lots = listOf(
                buy(1, "2026-01-01", 10.0, 10.0),
                sell(2, "2026-12-01", 5.0, 15.0),
            ),
        )
        val r = compute(asset, today = LocalDate.of(2027, 1, 1))
        assertEquals(25.0, r.realizedPnl, 0.0001)
        assertNull(r.unrealizedPnl)
        assertNull(r.totalReturn)
    }

    @Test
    fun `xirr is approximately ten percent on buy-then-sell year apart`() {
        val asset = asset(
            latestPrice = null,
            lots = listOf(
                buy(1, "2026-01-01", 100.0, 10.0),
                sell(2, "2027-01-01", 100.0, 11.0),
            ),
        )
        val r = compute(asset, today = LocalDate.of(2027, 1, 2))
        assertNotNull(r.xirr)
        assertEquals(0.10, r.xirr!!, 0.005)
    }

    private fun asset(latestPrice: Double?, lots: List<FinancialLot>) = FinancialAsset(
        id = 1L,
        name = "Test",
        ticker = "TST",
        displayName = "Test",
        currencyCode = "EUR",
        latestPrice = latestPrice,
        latestPriceAt = null,
        notes = null,
        lots = lots,
    )

    private fun buy(id: Long, date: String, qty: Double, price: Double) = FinancialLot(
        id = id, purchaseDate = LocalDate.parse(date), quantity = qty, pricePerUnit = price, type = LotType.BUY,
    )

    private fun sell(id: Long, date: String, qty: Double, price: Double) = FinancialLot(
        id = id, purchaseDate = LocalDate.parse(date), quantity = qty, pricePerUnit = price, type = LotType.SELL,
    )
}
