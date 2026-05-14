package com.atlasfpt.ui.feature.assets.realestate.edit

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.atlasfpt.data.settings.AppSettings
import com.atlasfpt.data.settings.SettingsRepository
import com.atlasfpt.domain.model.EnergyRating
import com.atlasfpt.domain.model.InterestType
import com.atlasfpt.domain.model.RealEstateAsset
import com.atlasfpt.domain.model.ReferenceRate
import com.atlasfpt.domain.usecase.DeleteAssetUseCase
import com.atlasfpt.domain.usecase.GetRealEstateUseCase
import com.atlasfpt.domain.usecase.SaveRealEstateUseCase
import com.atlasfpt.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AddEditRealEstateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val save: SaveRealEstateUseCase = mockk(relaxed = true)
    private val delete: DeleteAssetUseCase = mockk(relaxed = true)
    private val get: GetRealEstateUseCase = mockk()
    private val settingsRepository: SettingsRepository = mockk()

    private val existing = RealEstateAsset(
        id = 7L,
        name = "Existing flat",
        currencyCode = "USD",
        currentValue = 250_000.0,
        currentValueUpdatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        purchaseDate = LocalDate.of(2020, 1, 1),
        notes = "old",
        cost = 230_000.0,
        investedCapital = 80_000.0,
        debtAmount = 150_000.0,
        outstandingDebt = 120_000.0,
        interestType = InterestType.FIXED,
        fixedRate = 3.0,
        referenceRate = null,
        spread = null,
        creditEndDate = LocalDate.of(2050, 1, 1),
        district = "Lisboa",
        council = "Lisboa",
        parish = "Alvalade",
        sizeM2 = 85.0,
        energyRating = EnergyRating.B
    )

    @Before
    fun setUp() {
        coEvery { save(any()) } returns 42L
        every { settingsRepository.settings } returns MutableStateFlow(AppSettings())
    }

    private fun viewModel(assetId: Long? = null) = AddEditRealEstateViewModel(
        savedStateHandle = SavedStateHandle(mapOf("assetId" to assetId?.toString())),
        saveUseCase = save,
        deleteUseCase = delete,
        getUseCase = get,
        settingsRepository = settingsRepository
    )

    private fun fillValidForm(vm: AddEditRealEstateViewModel) {
        vm.onName("Lisbon flat")
        vm.onPurchaseDate(LocalDate.of(2020, 1, 1))
        vm.onCurrentValue("300000")
        vm.onCost("250000")
        vm.onInvestedCapital("80000")
        vm.onDebtAmount("200000")
        vm.onOutstandingDebt("180000")
        vm.onInterestType(InterestType.VARIABLE)
        vm.onReferenceRate(ReferenceRate.EURIBOR_12M)
        vm.onSpread("1.5")
        vm.onCreditEndDate(LocalDate.of(2050, 1, 1))
        vm.onDistrict("Lisboa")
        vm.onCouncil("Lisboa")
        vm.onParish("Alvalade")
        vm.onSizeM2("85")
        vm.onEnergyRating(EnergyRating.B)
    }

    @Test
    fun `empty form has all required errors`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.uiState.test {
            val s = awaitItem()
            assertTrue(s.formErrors.hasAny)
            assertNotNull(s.formErrors.name)
            assertNotNull(s.formErrors.purchaseDate)
            assertNotNull(s.formErrors.currentValue)
            assertNotNull(s.formErrors.cost)
            assertNotNull(s.formErrors.investedCapital)
            assertNotNull(s.formErrors.district)
            assertNotNull(s.formErrors.council)
            assertNotNull(s.formErrors.parish)
            assertNotNull(s.formErrors.sizeM2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `valid form has no errors`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        fillValidForm(vm)

        vm.uiState.test {
            var s = awaitItem()
            while (s.formErrors.hasAny) s = awaitItem()
            assertFalse(s.formErrors.hasAny)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `debt fields not required when debt amount blank`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onName("Lisbon flat")
        vm.onPurchaseDate(LocalDate.of(2020, 1, 1))
        vm.onCurrentValue("300000")
        vm.onCost("250000")
        vm.onInvestedCapital("80000")
        // skip debt entirely
        vm.onDistrict("Lisboa")
        vm.onCouncil("Lisboa")
        vm.onParish("Alvalade")
        vm.onSizeM2("85")
        vm.onEnergyRating(EnergyRating.B)

        vm.uiState.test {
            var s = awaitItem()
            while (s.formErrors.hasAny) s = awaitItem()
            assertNull(s.formErrors.outstandingDebt)
            assertNull(s.formErrors.creditEndDate)
            assertNull(s.formErrors.interestType)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `fixed interest does not require reference rate`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        fillValidForm(vm)
        vm.onInterestType(InterestType.FIXED)
        vm.onFixedRate("3,2")
        vm.onSpread("") // remove spread
        vm.onReferenceRate(null) // remove reference rate

        vm.uiState.test {
            var s = awaitItem()
            while (s.formErrors.hasAny) s = awaitItem()
            assertFalse(s.formErrors.hasAny)
            assertNull(s.formErrors.referenceRate)
            assertNull(s.formErrors.spread)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `variable interest requires reference rate and spread`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        fillValidForm(vm)
        vm.onReferenceRate(null)
        vm.onSpread("")

        vm.uiState.test {
            var s = awaitItem()
            while (s.formErrors.referenceRate == null) s = awaitItem()
            assertNotNull(s.formErrors.referenceRate)
            assertNotNull(s.formErrors.spread)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `purchase date in future flagged`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        fillValidForm(vm)
        vm.onPurchaseDate(LocalDate.now().plusDays(1))

        vm.uiState.test {
            var s = awaitItem()
            while (s.formErrors.purchaseDate == null) s = awaitItem()
            assertNotNull(s.formErrors.purchaseDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `credit end date before purchase date flagged`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        fillValidForm(vm)
        vm.onPurchaseDate(LocalDate.of(2020, 1, 1))
        vm.onCreditEndDate(LocalDate.of(2019, 1, 1))

        vm.uiState.test {
            var s = awaitItem()
            while (s.formErrors.creditEndDate == null) s = awaitItem()
            assertNotNull(s.formErrors.creditEndDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `decimal input accepts comma`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        fillValidForm(vm)
        vm.onCurrentValue("123,45")

        vm.uiState.test {
            var s = awaitItem()
            while (s.formErrors.hasAny) s = awaitItem()
            assertFalse(s.formErrors.hasAny)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `non-numeric current value flagged`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        fillValidForm(vm)
        vm.onCurrentValue("abc")

        vm.uiState.test {
            var s = awaitItem()
            while (s.formErrors.currentValue == null) s = awaitItem()
            assertNotNull(s.formErrors.currentValue)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `add saves and sets isSaved`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        fillValidForm(vm)
        vm.uiState.test {
            // wait for valid state
            var s = awaitItem()
            while (s.formErrors.hasAny) s = awaitItem()
            vm.save()
            advanceUntilIdle()
            // poll until isSaved
            var t = awaitItem()
            while (!t.isSaved) t = awaitItem()
            assertTrue(t.isSaved)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { save(any()) }
    }

    @Test
    fun `save error sets errorMessage and not isSaved`() = runTest {
        coEvery { save(any()) } throws IllegalStateException("boom")
        val vm = viewModel()
        advanceUntilIdle()
        fillValidForm(vm)
        vm.save()
        advanceUntilIdle()

        vm.uiState.test {
            val s = awaitItem()
            assertFalse(s.isSaved)
            assertNotNull(s.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `edit mode loads asset and currency is read only`() = runTest {
        coEvery { get(7L) } returns existing
        val vm = viewModel(7L)
        advanceUntilIdle()

        // Direct value reads — onCurrencyCode is a no-op in edit mode and does not
        // trigger an emission, so awaitItem() would hang.
        assertEquals("Existing flat", vm.uiState.value.name)
        assertEquals("USD", vm.uiState.value.currencyCode)
        assertTrue(vm.uiState.value.isEditMode)

        vm.onCurrencyCode("EUR")
        advanceUntilIdle()
        assertEquals("USD", vm.uiState.value.currencyCode)
    }

    @Test
    fun `delete in edit mode invokes use case`() = runTest {
        coEvery { get(7L) } returns existing
        val vm = viewModel(7L)
        advanceUntilIdle()
        vm.uiState.test {
            // wait for load
            var s = awaitItem()
            while (s.name != "Existing flat") s = awaitItem()
            vm.confirmDelete()
            advanceUntilIdle()
            var t = awaitItem()
            while (!t.isDeleted) t = awaitItem()
            assertTrue(t.isDeleted)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { delete(7L) }
    }
}
