package com.atlasfpt.ui.feature.overview

import app.cash.turbine.test
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

    @Test
    fun `onSideSelected flips selectedSide`() = runTest {
        every { getOverview(any<YearMonth>()) } returns flowOf(OverviewUiState(isLoading = false))
        every { settings.settings } returns MutableStateFlow(AppSettings())

        val vm = OverviewViewModel(getOverview, settings)

        vm.uiState.test {
            assertEquals(TransactionType.EXPENSE, awaitItem().selectedSide)
            vm.onSideSelected(TransactionType.INCOME)
            assertEquals(TransactionType.INCOME, awaitItem().selectedSide)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
