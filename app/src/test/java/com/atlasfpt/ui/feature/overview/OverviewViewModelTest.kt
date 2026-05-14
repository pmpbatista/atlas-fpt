package com.atlasfpt.ui.feature.overview

import app.cash.turbine.test
import com.atlasfpt.data.repository.PersonRepository
import com.atlasfpt.data.settings.AppSettings
import com.atlasfpt.data.settings.SettingsRepository
import com.atlasfpt.domain.model.TransactionType
import com.atlasfpt.domain.usecase.GetOverviewUseCase
import com.atlasfpt.domain.usecase.OverviewUiState
import com.atlasfpt.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

class OverviewViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val getOverview: GetOverviewUseCase = mockk()
    private val settings: SettingsRepository = mockk()
    private val personRepository: PersonRepository = mockk()

    @Test
    fun `onSideSelected flips selectedSide`() = runTest {
        every { getOverview(any<YearMonth>(), any()) } returns flowOf(OverviewUiState(isLoading = false))
        every { settings.settings } returns MutableStateFlow(AppSettings())
        every { personRepository.observeAll() } returns flowOf(emptyList())

        val vm = OverviewViewModel(getOverview, settings, personRepository)

        vm.uiState.test {
            assertEquals(TransactionType.EXPENSE, awaitItem().selectedSide)
            vm.onSideSelected(TransactionType.INCOME)
            assertEquals(TransactionType.INCOME, awaitItem().selectedSide)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onPersonsSelected updates state and re-invokes use case with filter`() = runTest {
        val month = YearMonth.now()
        every { getOverview(month, emptySet()) } returns flowOf(OverviewUiState(isLoading = false))
        every { getOverview(month, setOf(1L)) } returns flowOf(OverviewUiState(isLoading = false, totalExpense = 99.0))
        every { settings.settings } returns MutableStateFlow(AppSettings())
        every { personRepository.observeAll() } returns flowOf(emptyList())

        val vm = OverviewViewModel(getOverview, settings, personRepository)

        vm.uiState.test {
            // Drain until the filter takes effect
            assertEquals(emptySet<Long>(), awaitItem().selectedPersonIds)
            vm.onPersonsSelected(setOf(1L))
            var item = awaitItem()
            while (item.overviewUiState.totalExpense != 99.0) item = awaitItem()
            assertEquals(setOf(1L), item.selectedPersonIds)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
