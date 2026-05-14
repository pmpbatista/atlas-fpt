package com.atlasfpt.ui.feature.assets.list

import app.cash.turbine.test
import com.atlasfpt.domain.model.AssetListItem
import com.atlasfpt.domain.model.AssetType
import com.atlasfpt.domain.model.TotalWealth
import com.atlasfpt.domain.usecase.GetAssetsListUseCase
import com.atlasfpt.domain.usecase.GetTotalWealthUseCase
import com.atlasfpt.domain.usecase.RefreshPricesUseCase
import com.atlasfpt.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AssetsListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getList: GetAssetsListUseCase = mockk()
    private val getTotal: GetTotalWealthUseCase = mockk()
    private val refreshPrices: RefreshPricesUseCase = mockk(relaxed = true)

    private fun item(id: Long, currency: String = "EUR") = AssetListItem(
        id = id,
        type = AssetType.REAL_ESTATE,
        name = "asset $id",
        currentValue = 100_000.0,
        currencyCode = currency,
        outstandingDebt = null
    )

    @Test
    fun `empty list shows empty state`() = runTest {
        every { getList() } returns flowOf(emptyList())
        every { getTotal() } returns flowOf(TotalWealth(emptyMap(), emptyMap()))
        val vm = AssetsListViewModel(getList, getTotal, refreshPrices)

        vm.uiState.test {
            val s = awaitItem()
            assertTrue(s.isEmpty)
            assertTrue(s.items.isEmpty())
            assertTrue(s.total.isEmpty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `populated list maps items 1 to 1`() = runTest {
        val items = listOf(item(1), item(2))
        every { getList() } returns flowOf(items)
        every { getTotal() } returns flowOf(TotalWealth(mapOf(AssetType.REAL_ESTATE to mapOf("EUR" to 200_000.0)), mapOf(AssetType.REAL_ESTATE to 2)))
        val vm = AssetsListViewModel(getList, getTotal, refreshPrices)

        vm.uiState.test {
            // first emission may be initial empty, then loaded
            var s = awaitItem()
            while (s.items.size < 2) s = awaitItem()
            assertEquals(2, s.items.size)
            assertFalse(s.isEmpty)
            assertEquals(200_000.0, s.total.byCurrency["EUR"]!!, 0.0001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `mixed currencies flagged on total`() = runTest {
        val items = listOf(item(1, "EUR"), item(2, "USD"))
        every { getList() } returns flowOf(items)
        every { getTotal() } returns flowOf(
            TotalWealth(mapOf(AssetType.REAL_ESTATE to mapOf("EUR" to 100_000.0, "USD" to 50_000.0)), mapOf(AssetType.REAL_ESTATE to 2))
        )
        val vm = AssetsListViewModel(getList, getTotal, refreshPrices)

        vm.uiState.test {
            var s = awaitItem()
            while (!s.total.isMixedCurrency) s = awaitItem()
            assertTrue(s.total.isMixedCurrency)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
