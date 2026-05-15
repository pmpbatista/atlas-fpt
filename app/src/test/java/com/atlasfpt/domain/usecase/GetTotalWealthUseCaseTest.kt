package com.atlasfpt.domain.usecase

import app.cash.turbine.test
import com.atlasfpt.data.repository.AssetRepository
import com.atlasfpt.data.repository.FxRatesRepository
import com.atlasfpt.data.settings.AppSettings
import com.atlasfpt.data.settings.SettingsRepository
import com.atlasfpt.domain.model.AssetListItem
import com.atlasfpt.domain.model.AssetType
import com.atlasfpt.domain.model.FxRate
import com.atlasfpt.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GetTotalWealthUseCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo: AssetRepository = mockk()
    private val fxRepo: FxRatesRepository = mockk()
    private val settingsRepo: SettingsRepository = mockk()

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

    private fun useCase(
        items: List<AssetListItem>,
        rates: Map<String, FxRate> = emptyMap(),
        displayCurrency: String = "EUR",
    ): GetTotalWealthUseCase {
        every { repo.observeAssetList() } returns flowOf(items)
        every { fxRepo.observeRates() } returns flowOf(rates)
        every { settingsRepo.settings } returns MutableStateFlow(
            AppSettings(displayCurrencyCode = displayCurrency)
        )
        return GetTotalWealthUseCase(repo, fxRepo, settingsRepo, ConvertCurrencyUseCase())
    }

    @Test
    fun `empty list yields empty total`() = runTest {
        useCase(emptyList())().test {
            val w = awaitItem()
            assertTrue(w.isEmpty)
            assertEquals(0, w.assetCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `single EUR no debt sums to current value`() = runTest {
        useCase(listOf(item(1, 100_000.0)))().test {
            val w = awaitItem()
            assertEquals(100_000.0, w.byCurrency["EUR"]!!, 0.0001)
            assertEquals(1, w.assetCount)
            assertFalse(w.isMixedCurrency)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `single EUR with debt subtracts outstanding`() = runTest {
        useCase(listOf(item(1, 300_000.0, outstandingDebt = 100_000.0)))().test {
            val w = awaitItem()
            assertEquals(200_000.0, w.byCurrency["EUR"]!!, 0.0001)
            cancelAndIgnoreRemainingEvents()
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
            cancelAndIgnoreRemainingEvents()
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
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `null outstanding debt treated as zero`() = runTest {
        useCase(listOf(item(1, 200_000.0, outstandingDebt = null)))().test {
            val w = awaitItem()
            assertEquals(200_000.0, w.byCurrency["EUR"]!!, 0.0001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `underwater asset produces negative entry`() = runTest {
        useCase(listOf(item(1, 100_000.0, outstandingDebt = 150_000.0)))().test {
            val w = awaitItem()
            assertEquals(-50_000.0, w.byCurrency["EUR"]!!, 0.0001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `groups by type and currency`() = runTest {
        useCase(listOf(
            item(1, 100_000.0, currencyCode = "EUR"),                               // REAL_ESTATE (default)
            AssetListItem(2L, AssetType.FINANCIAL, "stocks", 50_000.0, "EUR", null) // FINANCIAL
        ))().test {
            val w = awaitItem()
            assertEquals(100_000.0, w.byCurrencyForType(AssetType.REAL_ESTATE)["EUR"]!!, 0.0001)
            assertEquals(50_000.0, w.byCurrencyForType(AssetType.FINANCIAL)["EUR"]!!, 0.0001)
            assertEquals(150_000.0, w.byCurrency["EUR"]!!, 0.0001)
            assertEquals(2, w.assetCount)
            assertTrue(w.hasType(AssetType.REAL_ESTATE))
            assertTrue(w.hasType(AssetType.FINANCIAL))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `single-type list reports only that type`() = runTest {
        useCase(listOf(item(1, 100_000.0)))().test {
            val w = awaitItem()
            assertTrue(w.hasType(AssetType.REAL_ESTATE))
            assertFalse(w.hasType(AssetType.FINANCIAL))
            assertEquals(emptyMap<String, Double>(), w.byCurrencyForType(AssetType.FINANCIAL))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `mixed currency with rates converts grand total to display currency`() = runTest {
        val rates = mapOf(
            "USD" to FxRate("USD", unitsPerEur = 1.10, fetchedAt = 1_000L),
        )
        useCase(
            items = listOf(
                item(1, 100_000.0, currencyCode = "EUR"),
                item(2, 11_000.0, currencyCode = "USD"),
            ),
            rates = rates,
            displayCurrency = "EUR",
        )().test {
            val w = awaitItem()
            assertNotNull(w.totalInDisplayCurrency)
            assertEquals(110_000.0, w.totalInDisplayCurrency!!, 0.0001) // 100k EUR + 11k USD / 1.10 = 110k EUR
            assertEquals("EUR", w.displayCurrencyCode)
            assertEquals(1_000L, w.fxFetchedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `mixed currency without rate hides converted total`() = runTest {
        useCase(
            items = listOf(
                item(1, 100_000.0, currencyCode = "EUR"),
                item(2, 50_000.0, currencyCode = "USD"),
            ),
            rates = emptyMap(),
            displayCurrency = "EUR",
        )().test {
            val w = awaitItem()
            assertNull(w.totalInDisplayCurrency)
            assertNull(w.fxFetchedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
