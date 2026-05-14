package com.atlasfpt.data.repository

import com.atlasfpt.data.db.dao.FinancialDao
import com.atlasfpt.data.db.dao.TickerRow
import com.atlasfpt.data.db.entity.FinancialHoldingEntity
import com.atlasfpt.data.network.PriceSource
import com.atlasfpt.domain.model.QuoteResult
import com.atlasfpt.domain.model.TickerQuote
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class PriceRepositoryTest {

    private val source: PriceSource = mockk()
    private val financialDao: FinancialDao = mockk(relaxed = true)
    private val financialRepo: FinancialRepository = mockk(relaxed = true)

    private fun repo() = PriceRepository(source, financialDao, financialRepo)

    private val sampleQuote = TickerQuote(
        ticker = "AAPL",
        displayName = "Apple Inc.",
        currencyCode = "USD",
        price = 234.56,
        asOf = Instant.parse("2026-05-06T12:00:00Z"),
    )

    @Test fun `cache miss + Success caches and returns quote`() = runTest {
        coEvery { source.fetchQuote("AAPL") } returns QuoteResult.Success(sampleQuote)
        coEvery { financialDao.countByTicker(any()) } returns 1
        coEvery { financialDao.getAllTickers() } returns listOf(TickerRow(1L, "AAPL"))

        val r = repo().getQuote("AAPL")

        assertEquals(234.56, r!!.price, 0.0001)
        coVerify { source.fetchQuote("AAPL") }
    }

    @Test fun `cache fresh skips network`() = runTest {
        coEvery { source.fetchQuote("AAPL") } returns QuoteResult.Success(sampleQuote)
        coEvery { financialDao.getAllTickers() } returns listOf(TickerRow(1L, "AAPL"))

        val r = repo()
        r.getQuote("AAPL")  // populates cache
        r.getQuote("AAPL")  // should NOT call network again

        coVerify(exactly = 1) { source.fetchQuote("AAPL") }
    }

    @Test fun `force=true bypasses fresh cache`() = runTest {
        coEvery { source.fetchQuote("AAPL") } returns QuoteResult.Success(sampleQuote)
        coEvery { financialDao.getAllTickers() } returns listOf(TickerRow(1L, "AAPL"))

        val r = repo()
        r.getQuote("AAPL")
        r.getQuote("AAPL", force = true)

        coVerify(exactly = 2) { source.fetchQuote("AAPL") }
    }

    @Test fun `NotFound on first fetch falls back to persisted last-known`() = runTest {
        coEvery { source.fetchQuote("AAPL") } returns QuoteResult.NotFound
        coEvery { financialDao.getAllTickers() } returns listOf(TickerRow(1L, "AAPL"))
        coEvery { financialDao.getHolding(1L) } returns FinancialHoldingEntity(
            assetId = 1L, ticker = "AAPL", displayName = "Apple Inc.",
            latestPrice = 200.0, latestPriceAt = 1_000L,
        )

        val r = repo().getQuote("AAPL")

        assertNotNull(r)
        assertEquals(200.0, r!!.price, 0.0001)
    }

    @Test fun `Error with no persisted returns null`() = runTest {
        coEvery { source.fetchQuote("AAPL") } returns QuoteResult.Error("boom")
        coEvery { financialDao.getAllTickers() } returns listOf(TickerRow(1L, "AAPL"))
        coEvery { financialDao.getHolding(1L) } returns FinancialHoldingEntity(
            assetId = 1L, ticker = "AAPL", displayName = "Apple Inc.",
            latestPrice = null, latestPriceAt = null,
        )

        val r = repo().getQuote("AAPL")
        assertNull(r)
    }

    @Test fun `validateTicker always hits network even with cache`() = runTest {
        coEvery { source.fetchQuote("AAPL") } returns QuoteResult.Success(sampleQuote)
        coEvery { financialDao.getAllTickers() } returns listOf(TickerRow(1L, "AAPL"))

        val r = repo()
        r.getQuote("AAPL")
        val result = r.validateTicker("AAPL")

        coVerify(exactly = 2) { source.fetchQuote("AAPL") }
        assert(result is QuoteResult.Success)
    }

    @Test fun `refreshAll counts successes and failures per asset`() = runTest {
        coEvery { financialDao.getAllTickers() } returns listOf(
            TickerRow(1L, "AAPL"),
            TickerRow(2L, "BADTICKER"),
        )
        coEvery { source.fetchQuote("AAPL") } returns QuoteResult.Success(sampleQuote)
        coEvery { source.fetchQuote("BADTICKER") } returns QuoteResult.NotFound

        val result = repo().refreshAll()

        assertEquals(1, result.succeeded)
        assertEquals(1, result.failed)
        coVerify { financialRepo.applyPriceUpdate(1L, sampleQuote.price) }
    }
}
