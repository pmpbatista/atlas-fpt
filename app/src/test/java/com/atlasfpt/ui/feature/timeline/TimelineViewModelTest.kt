package com.atlasfpt.ui.feature.timeline

import app.cash.turbine.test
import com.atlasfpt.data.settings.AppSettings
import com.atlasfpt.data.settings.SettingsRepository
import com.atlasfpt.domain.usecase.DeleteTransactionUseCase
import com.atlasfpt.domain.usecase.GetTimelineUseCase
import com.atlasfpt.domain.usecase.TimelineData
import com.atlasfpt.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TimelineViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val getTimeline: GetTimelineUseCase = mockk()
    private val deleteTransaction: DeleteTransactionUseCase = mockk(relaxed = true)
    private val settings: SettingsRepository = mockk()

    @Test
    fun `onRangeModeSelected flips rangeMode`() = runTest {
        every { getTimeline() } returns flowOf(TimelineData())
        every { settings.settings } returns MutableStateFlow(AppSettings())

        val vm = TimelineViewModel(getTimeline, deleteTransaction, settings)

        vm.uiState.test {
            assertEquals(RangeMode.ByMonths, awaitItem().rangeMode)
            vm.onRangeModeSelected(RangeMode.ByWeeks)
            assertEquals(RangeMode.ByWeeks, awaitItem().rangeMode)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
