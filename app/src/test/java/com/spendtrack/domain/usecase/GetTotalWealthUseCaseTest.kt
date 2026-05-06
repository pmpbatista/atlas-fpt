package com.spendtrack.domain.usecase

import app.cash.turbine.test
import com.spendtrack.data.repository.AssetRepository
import com.spendtrack.domain.model.AssetListItem
import com.spendtrack.domain.model.AssetType
import com.spendtrack.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GetTotalWealthUseCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo: AssetRepository = mockk()

    private fun item(
        id: Long,
        currentValue: Double,
        currencyCode: String = "EUR",
        outstandingDebt: Double? = null
    ) = AssetListItem(
        id = id,
        type = AssetType.REAL_ESTATE,
        name = "asset $id",
        currentValue = currentValue,
        currencyCode = currencyCode,
        outstandingDebt = outstandingDebt
    )

    private fun useCase(items: List<AssetListItem>) = GetTotalWealthUseCase(repo).also {
        every { repo.observeAssetList() } returns flowOf(items)
    }

    @Test
    fun `empty list yields empty total`() = runTest {
        useCase(emptyList())().test {
            val w = awaitItem()
            assertTrue(w.isEmpty)
            assertEquals(0, w.assetCount)
            awaitComplete()
        }
    }

    @Test
    fun `single EUR no debt sums to current value`() = runTest {
        useCase(listOf(item(1, 100_000.0)))().test {
            val w = awaitItem()
            assertEquals(100_000.0, w.byCurrency["EUR"]!!, 0.0001)
            assertEquals(1, w.assetCount)
            assertFalse(w.isMixedCurrency)
            awaitComplete()
        }
    }

    @Test
    fun `single EUR with debt subtracts outstanding`() = runTest {
        useCase(listOf(item(1, 300_000.0, outstandingDebt = 100_000.0)))().test {
            val w = awaitItem()
            assertEquals(200_000.0, w.byCurrency["EUR"]!!, 0.0001)
            awaitComplete()
        }
    }

    @Test
    fun `multiple EUR assets sum equity`() = runTest {
        useCase(listOf(
            item(1, 300_000.0, outstandingDebt = 100_000.0),
            item(2, 50_000.0)
        ))().test {
            val w = awaitItem()
            assertEquals(250_000.0, w.byCurrency["EUR"]!!, 0.0001)
            assertEquals(2, w.assetCount)
            awaitComplete()
        }
    }

    @Test
    fun `mixed EUR and USD produces two entries`() = runTest {
        useCase(listOf(
            item(1, 100_000.0, currencyCode = "EUR"),
            item(2, 50_000.0, currencyCode = "USD")
        ))().test {
            val w = awaitItem()
            assertEquals(100_000.0, w.byCurrency["EUR"]!!, 0.0001)
            assertEquals(50_000.0, w.byCurrency["USD"]!!, 0.0001)
            assertTrue(w.isMixedCurrency)
            assertEquals(2, w.assetCount)
            awaitComplete()
        }
    }

    @Test
    fun `null outstanding debt treated as zero`() = runTest {
        useCase(listOf(item(1, 200_000.0, outstandingDebt = null)))().test {
            val w = awaitItem()
            assertEquals(200_000.0, w.byCurrency["EUR"]!!, 0.0001)
            awaitComplete()
        }
    }

    @Test
    fun `underwater asset produces negative entry`() = runTest {
        useCase(listOf(item(1, 100_000.0, outstandingDebt = 150_000.0)))().test {
            val w = awaitItem()
            assertEquals(-50_000.0, w.byCurrency["EUR"]!!, 0.0001)
            awaitComplete()
        }
    }
}
