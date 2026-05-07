package com.spendtrack.ui.feature.assets.financial.detail

import androidx.lifecycle.SavedStateHandle
import com.spendtrack.data.repository.PriceRepository
import com.spendtrack.domain.model.FinancialAsset
import com.spendtrack.domain.model.FinancialLot
import com.spendtrack.domain.usecase.DeleteAssetUseCase
import com.spendtrack.domain.usecase.DeleteLotUseCase
import com.spendtrack.domain.usecase.GetFinancialAssetUseCase
import com.spendtrack.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class FinancialDetailViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val getAsset: GetFinancialAssetUseCase = mockk()
    private val deleteAsset: DeleteAssetUseCase = mockk(relaxed = true)
    private val deleteLot: DeleteLotUseCase = mockk(relaxed = true)
    private val priceRepo: PriceRepository = mockk(relaxed = true)

    private fun asset(
        id: Long = 1L,
        latestPrice: Double? = 234.56,
        lots: List<FinancialLot> = listOf(
            FinancialLot(1L, LocalDate.of(2024, 1, 1), 10.0, 175.20),
        ),
    ) = FinancialAsset(
        id = id, name = "Apple Inc.", ticker = "AAPL", displayName = "Apple Inc.",
        currencyCode = "USD", latestPrice = latestPrice,
        latestPriceAt = latestPrice?.let { Instant.now() },
        notes = null, lots = lots,
    )

    private fun viewModel(idArg: Long?) = FinancialDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("assetId" to idArg?.toString())),
        getAsset = getAsset,
        deleteAssetUseCase = deleteAsset,
        deleteLotUseCase = deleteLot,
        priceRepository = priceRepo,
    )

    @Test fun `loads asset and computes equity`() = runTest {
        coEvery { getAsset(1L) } returns asset()
        val vm = viewModel(1L)
        advanceUntilIdle()
        val s = vm.uiState.value
        assertNotNull(s.asset)
        assertFalse(s.loadError)
        assertEquals(2345.6, s.asset!!.currentValue!!, 0.001)
    }

    @Test fun `null id sets loadError`() = runTest {
        val vm = viewModel(null)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.loadError)
    }

    @Test fun `missing asset sets loadError`() = runTest {
        coEvery { getAsset(99L) } returns null
        val vm = viewModel(99L)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.loadError)
    }

    @Test fun `refresh invokes price repo with force`() = runTest {
        coEvery { getAsset(1L) } returns asset()
        coEvery { priceRepo.getQuote("AAPL", true) } returns null
        val vm = viewModel(1L)
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()
        coVerify { priceRepo.getQuote("AAPL", true) }
    }

    @Test fun `deleteLot when not last only deletes lot`() = runTest {
        coEvery { getAsset(1L) } returns asset(lots = listOf(
            FinancialLot(1L, LocalDate.of(2024, 1, 1), 10.0, 175.20),
            FinancialLot(2L, LocalDate.of(2024, 6, 1), 5.0, 200.0),
        ))
        coEvery { deleteLot(1L, 1L) } returns false
        val vm = viewModel(1L)
        advanceUntilIdle()
        vm.deleteLot(1L)
        advanceUntilIdle()
        coVerify { deleteLot(1L, 1L) }
        assertFalse(vm.uiState.value.isDeleted)
    }

    @Test fun `deleteLot when last sets isDeleted`() = runTest {
        coEvery { getAsset(1L) } returns asset(lots = listOf(
            FinancialLot(1L, LocalDate.of(2024, 1, 1), 10.0, 175.20),
        ))
        coEvery { deleteLot(1L, 1L) } returns true
        val vm = viewModel(1L)
        advanceUntilIdle()
        vm.deleteLot(1L)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isDeleted)
    }

    @Test fun `deleteAsset invokes use case and sets isDeleted`() = runTest {
        coEvery { getAsset(1L) } returns asset()
        val vm = viewModel(1L)
        advanceUntilIdle()
        vm.deleteAsset()
        advanceUntilIdle()
        coVerify { deleteAsset(1L) }
        assertTrue(vm.uiState.value.isDeleted)
    }
}
