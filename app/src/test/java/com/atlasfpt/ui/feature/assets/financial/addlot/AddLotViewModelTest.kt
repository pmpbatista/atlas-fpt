package com.atlasfpt.ui.feature.assets.financial.addlot

import androidx.lifecycle.SavedStateHandle
import com.atlasfpt.domain.model.FinancialAsset
import com.atlasfpt.domain.model.FinancialLot
import com.atlasfpt.domain.usecase.AddLotUseCase
import com.atlasfpt.domain.usecase.GetFinancialAssetUseCase
import com.atlasfpt.domain.usecase.UpdateLotUseCase
import com.atlasfpt.util.MainDispatcherRule
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

class AddLotViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val getAsset: GetFinancialAssetUseCase = mockk()
    private val addLot: AddLotUseCase = mockk(relaxed = true)
    private val updateLot: UpdateLotUseCase = mockk(relaxed = true)

    private val asset = FinancialAsset(
        id = 1L, name = "Apple Inc.", ticker = "AAPL", displayName = "Apple Inc.",
        currencyCode = "USD", latestPrice = 234.56, latestPriceAt = Instant.now(),
        notes = null,
        lots = listOf(FinancialLot(7L, LocalDate.of(2024, 1, 1), 10.0, 175.20)),
    )

    private fun viewModel(assetId: Long? = 1L, lotId: Long? = null) = AddLotViewModel(
        savedStateHandle = SavedStateHandle(mapOf(
            "assetId" to assetId?.toString(),
            "lotId" to lotId?.toString(),
        )),
        getAsset = getAsset,
        addLotUseCase = addLot,
        updateLotUseCase = updateLot,
    )

    @Test fun `add mode prefills currency and price from asset`() = runTest {
        coEvery { getAsset(1L) } returns asset
        val vm = viewModel()
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals("USD", s.currencyCode)
        assertEquals("234.56", s.pricePerUnit)
        assertFalse(s.isEditMode)
    }

    @Test fun `edit mode prefills from existing lot`() = runTest {
        coEvery { getAsset(1L) } returns asset
        val vm = viewModel(lotId = 7L)
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(LocalDate.of(2024, 1, 1), s.purchaseDate)
        assertEquals("10", s.quantity)
        assertEquals("175.2", s.pricePerUnit)
        assertTrue(s.isEditMode)
    }

    @Test fun `quantity must be greater than zero`() = runTest {
        coEvery { getAsset(1L) } returns asset
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPurchaseDate(LocalDate.of(2024, 1, 1))
        vm.onQuantity("0")
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.formErrors.quantity)
    }

    @Test fun `valid form add saves and isSaved`() = runTest {
        coEvery { getAsset(1L) } returns asset
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPurchaseDate(LocalDate.of(2025, 6, 1))
        vm.onQuantity("5")
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        coVerify { addLot(1L, any()) }
        assertTrue(vm.uiState.value.isSaved)
    }

    @Test fun `edit mode save calls update`() = runTest {
        coEvery { getAsset(1L) } returns asset
        val vm = viewModel(lotId = 7L)
        advanceUntilIdle()
        vm.onQuantity("12")
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        coVerify { updateLot(1L, any()) }
        assertTrue(vm.uiState.value.isSaved)
    }

    @Test fun `asset deleted yields specific error`() = runTest {
        coEvery { getAsset(1L) } returns asset
        coEvery { addLot(1L, any()) } throws IllegalStateException("Asset 1 no longer exists")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPurchaseDate(LocalDate.of(2025, 6, 1))
        vm.onQuantity("5")
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSaved)
        val msg = vm.uiState.value.errorMessage
        assertNotNull(msg)
        assertTrue("expected 'no longer exists' in '$msg'", msg!!.contains("no longer exists"))
    }
}
