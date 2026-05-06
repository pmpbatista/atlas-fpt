package com.spendtrack.ui.feature.assets.financial.add

import androidx.lifecycle.SavedStateHandle
import com.spendtrack.domain.model.QuoteResult
import com.spendtrack.domain.model.TickerQuote
import com.spendtrack.domain.usecase.SaveFinancialAssetUseCase
import com.spendtrack.domain.usecase.ValidateTickerUseCase
import com.spendtrack.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AddFinancialAssetViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val validate: ValidateTickerUseCase = mockk()
    private val save: SaveFinancialAssetUseCase = mockk(relaxed = true)

    private val sampleQuote = TickerQuote(
        ticker = "AAPL",
        displayName = "Apple Inc.",
        currencyCode = "USD",
        price = 234.56,
        asOf = Instant.parse("2026-05-06T12:00:00Z"),
    )

    private fun viewModel() = AddFinancialAssetViewModel(
        savedStateHandle = SavedStateHandle(),
        validateTicker = validate,
        saveAsset = save,
    )

    @Test fun `empty ticker is Idle`() = runTest {
        coEvery { save(any(), any(), any(), any()) } returns 1L
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.tickerState is TickerState.Idle)
    }

    @Test fun `valid ticker after debounce sets Valid and prefills`() = runTest {
        coEvery { validate("AAPL") } returns QuoteResult.Success(sampleQuote)
        val vm = viewModel()
        vm.onTicker("AAPL")
        advanceTimeBy(600) // past debounce
        advanceUntilIdle()
        val s = vm.uiState.value
        assertTrue("expected Valid, got ${s.tickerState}", s.tickerState is TickerState.Valid)
        assertEquals("Apple Inc.", s.name)
        assertEquals("234.56", s.pricePerUnit)
    }

    @Test fun `NotFound becomes Invalid`() = runTest {
        coEvery { validate("BAD") } returns QuoteResult.NotFound
        val vm = viewModel()
        vm.onTicker("BAD")
        advanceTimeBy(600); advanceUntilIdle()
        assertTrue(vm.uiState.value.tickerState is TickerState.Invalid)
    }

    @Test fun `Error becomes Error state`() = runTest {
        coEvery { validate("AAPL") } returns QuoteResult.Error("network down")
        val vm = viewModel()
        vm.onTicker("AAPL")
        advanceTimeBy(600); advanceUntilIdle()
        assertTrue(vm.uiState.value.tickerState is TickerState.Error)
    }

    @Test fun `name not overwritten on re-validation`() = runTest {
        coEvery { validate("AAPL") } returns QuoteResult.Success(sampleQuote)
        coEvery { validate("AAP") } returns QuoteResult.NotFound
        val vm = viewModel()
        vm.onTicker("AAPL")
        advanceTimeBy(600); advanceUntilIdle()
        vm.onName("My Apple")
        vm.onTicker("AAP")
        advanceTimeBy(600); advanceUntilIdle()
        // user-typed name preserved
        assertEquals("My Apple", vm.uiState.value.name)
    }

    @Test fun `Save disabled until form valid`() = runTest {
        coEvery { validate("AAPL") } returns QuoteResult.Success(sampleQuote)
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.formErrors.hasAny)

        vm.onTicker("AAPL")
        advanceTimeBy(600); advanceUntilIdle()
        vm.onPurchaseDate(LocalDate.of(2024, 1, 1))
        vm.onQuantity("10")
        // pricePerUnit prefilled by validation
        advanceUntilIdle()
        assertFalse(vm.uiState.value.formErrors.hasAny)
    }

    @Test fun `quantity must be greater than zero`() = runTest {
        coEvery { validate("AAPL") } returns QuoteResult.Success(sampleQuote)
        val vm = viewModel()
        vm.onTicker("AAPL")
        advanceTimeBy(600); advanceUntilIdle()
        vm.onPurchaseDate(LocalDate.of(2024, 1, 1))
        vm.onQuantity("0")
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.formErrors.quantity)
    }

    @Test fun `purchase date in future flagged`() = runTest {
        coEvery { validate("AAPL") } returns QuoteResult.Success(sampleQuote)
        val vm = viewModel()
        vm.onTicker("AAPL")
        advanceTimeBy(600); advanceUntilIdle()
        vm.onPurchaseDate(LocalDate.now().plusDays(1))
        vm.onQuantity("10")
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.formErrors.purchaseDate)
    }

    @Test fun `save success sets isSaved`() = runTest {
        coEvery { validate("AAPL") } returns QuoteResult.Success(sampleQuote)
        coEvery { save(any(), any(), any(), any()) } returns 1L
        val vm = viewModel()
        vm.onTicker("AAPL")
        advanceTimeBy(600); advanceUntilIdle()
        vm.onPurchaseDate(LocalDate.of(2024, 1, 1))
        vm.onQuantity("10")
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isSaved)
        coVerify { save(any(), any(), any(), any()) }
    }

    @Test fun `duplicate ticker throws and surfaces specific message`() = runTest {
        coEvery { validate("AAPL") } returns QuoteResult.Success(sampleQuote)
        coEvery { save(any(), any(), any(), any()) } throws
            IllegalStateException("Asset for AAPL already exists")
        val vm = viewModel()
        vm.onTicker("AAPL")
        advanceTimeBy(600); advanceUntilIdle()
        vm.onPurchaseDate(LocalDate.of(2024, 1, 1))
        vm.onQuantity("10")
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSaved)
        val msg = vm.uiState.value.errorMessage
        assertNotNull(msg)
        assertTrue("expected 'already exists' in '$msg'", msg!!.contains("already exists"))
    }

    @Test fun `generic save error surfaces fallback message`() = runTest {
        coEvery { validate("AAPL") } returns QuoteResult.Success(sampleQuote)
        coEvery { save(any(), any(), any(), any()) } throws RuntimeException("boom")
        val vm = viewModel()
        vm.onTicker("AAPL")
        advanceTimeBy(600); advanceUntilIdle()
        vm.onPurchaseDate(LocalDate.of(2024, 1, 1))
        vm.onQuantity("10")
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSaved)
        val msg = vm.uiState.value.errorMessage
        assertNotNull(msg)
        assertTrue("expected 'Try again' in '$msg'", msg!!.contains("Try again"))
    }
}
