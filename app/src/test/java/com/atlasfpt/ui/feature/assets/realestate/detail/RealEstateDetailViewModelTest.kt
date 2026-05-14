package com.atlasfpt.ui.feature.assets.realestate.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.atlasfpt.data.repository.TransactionRepository
import com.atlasfpt.data.settings.AppSettings
import com.atlasfpt.data.settings.SettingsRepository
import com.atlasfpt.domain.model.EnergyRating
import com.atlasfpt.domain.model.InterestType
import com.atlasfpt.domain.model.RealEstateAsset
import com.atlasfpt.domain.model.ReferenceRate
import com.atlasfpt.domain.usecase.GetRealEstateUseCase
import com.atlasfpt.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class RealEstateDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getUseCase: GetRealEstateUseCase = mockk()
    private val transactionRepository: TransactionRepository = mockk {
        every { observeByAssetId(any()) } returns flowOf(emptyList())
    }
    private val settingsRepository: SettingsRepository = mockk {
        every { settings } returns MutableStateFlow(AppSettings())
    }

    private fun asset(
        id: Long = 1L,
        currentValue: Double = 300_000.0,
        outstandingDebt: Double? = 100_000.0,
        interestType: InterestType? = InterestType.VARIABLE,
        fixedRate: Double? = null,
        referenceRate: ReferenceRate? = ReferenceRate.EURIBOR_12M,
        spread: Double? = 1.5,
        creditEndDate: LocalDate? = LocalDate.of(2050, 5, 6)
    ) = RealEstateAsset(
        id = id,
        name = "Lisbon flat",
        currencyCode = "EUR",
        currentValue = currentValue,
        currentValueUpdatedAt = Instant.parse("2026-05-06T12:00:00Z"),
        purchaseDate = LocalDate.of(2020, 1, 1),
        notes = null,
        cost = 250_000.0,
        investedCapital = 80_000.0,
        debtAmount = 200_000.0,
        outstandingDebt = outstandingDebt,
        interestType = interestType,
        fixedRate = fixedRate,
        referenceRate = referenceRate,
        spread = spread,
        creditEndDate = creditEndDate,
        district = "Lisboa",
        council = "Lisboa",
        parish = "Alvalade",
        sizeM2 = 85.0,
        energyRating = EnergyRating.B
    )

    private fun viewModel(savedId: Long?) = RealEstateDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("assetId" to savedId?.toString())),
        getRealEstate = getUseCase,
        transactionRepository = transactionRepository,
        settingsRepository = settingsRepository,
    )

    @Test
    fun `loads asset on init and computes equity`() = runTest {
        coEvery { getUseCase(1L) } returns asset()
        val vm = viewModel(1L)
        advanceUntilIdle()

        vm.uiState.test {
            var s = awaitItem()
            while (s.asset == null) s = awaitItem()
            assertEquals(200_000.0, s.equity!!, 0.0001)
            assertFalse(s.loadError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `null id sets loadError`() = runTest {
        val vm = viewModel(null)
        advanceUntilIdle()

        vm.uiState.test {
            var s = awaitItem()
            while (!s.loadError) s = awaitItem()
            assertTrue(s.loadError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `missing asset sets loadError`() = runTest {
        coEvery { getUseCase(99L) } returns null
        val vm = viewModel(99L)
        advanceUntilIdle()

        vm.uiState.test {
            var s = awaitItem()
            while (!s.loadError) s = awaitItem()
            assertTrue(s.loadError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cash purchase has null equity contribution from debt`() = runTest {
        coEvery { getUseCase(1L) } returns asset(
            outstandingDebt = null,
            interestType = null,
            fixedRate = null,
            referenceRate = null,
            spread = null,
            creditEndDate = null
        )
        val vm = viewModel(1L)
        advanceUntilIdle()

        vm.uiState.test {
            var s = awaitItem()
            while (s.asset == null) s = awaitItem()
            // equity == currentValue when no debt
            assertEquals(300_000.0, s.equity!!, 0.0001)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
