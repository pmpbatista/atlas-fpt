package com.atlasfpt.ui.feature.timeline

import app.cash.turbine.test
import com.atlasfpt.data.settings.AppSettings
import com.atlasfpt.data.settings.SettingsRepository
import com.atlasfpt.domain.usecase.CashFlowBar
import com.atlasfpt.domain.usecase.DayGroup
import com.atlasfpt.domain.usecase.DeleteTransactionUseCase
import com.atlasfpt.domain.usecase.GetTimelineUseCase
import com.atlasfpt.domain.usecase.TimelineData
import com.atlasfpt.domain.usecase.TimelineMode
import com.atlasfpt.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class TimelineViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val getTimeline: GetTimelineUseCase = mockk()
    private val deleteTransaction: DeleteTransactionUseCase = mockk(relaxed = true)
    private val settings: SettingsRepository = mockk()

    private fun bar(yearMonth: String, income: Double = 0.0, expense: Double = 0.0): CashFlowBar {
        val start = LocalDate.parse("$yearMonth-01")
        val end = start.withDayOfMonth(start.lengthOfMonth())
        return CashFlowBar(yearMonth, start, end, income, expense, false)
    }

    @Test
    fun `default selection is the last bar and scopes header to it`() = runTest {
        val bars = listOf(
            bar("2026-03", income = 100.0),
            bar("2026-04", income = 200.0)
        )
        val days = listOf(
            DayGroup(LocalDate.parse("2026-03-15"), net = 100.0, rows = emptyList()),
            DayGroup(LocalDate.parse("2026-04-10"), net = 200.0, rows = emptyList())
        )
        every { getTimeline(TimelineMode.Monthly) } returns flowOf(
            TimelineData(headerTotal = 300.0, bars = bars, days = days)
        )
        every { settings.settings } returns MutableStateFlow(AppSettings())

        val vm = TimelineViewModel(getTimeline, deleteTransaction, settings)

        vm.uiState.test {
            var item = awaitItem()
            while (item.isLoading) item = awaitItem()
            assertEquals(1, item.selectedBarIndex)
            assertEquals(200.0, item.timelineData.headerTotal, 0.0)
            assertEquals(1, item.timelineData.days.size)
            assertEquals(LocalDate.parse("2026-04-10"), item.timelineData.days.first().date)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onBarSelected pins selection and re-scopes header`() = runTest {
        val bars = listOf(
            bar("2026-03", income = 100.0),
            bar("2026-04", income = 200.0)
        )
        val days = listOf(
            DayGroup(LocalDate.parse("2026-03-15"), net = 100.0, rows = emptyList()),
            DayGroup(LocalDate.parse("2026-04-10"), net = 200.0, rows = emptyList())
        )
        every { getTimeline(TimelineMode.Monthly) } returns flowOf(
            TimelineData(headerTotal = 300.0, bars = bars, days = days)
        )
        every { settings.settings } returns MutableStateFlow(AppSettings())

        val vm = TimelineViewModel(getTimeline, deleteTransaction, settings)

        vm.uiState.test {
            var item = awaitItem()
            while (item.isLoading) item = awaitItem()
            assertEquals(1, item.selectedBarIndex)
            vm.onBarSelected(0)
            var next = awaitItem()
            while (next.selectedBarIndex != 0) next = awaitItem()
            assertEquals(100.0, next.timelineData.headerTotal, 0.0)
            assertEquals(LocalDate.parse("2026-03-15"), next.timelineData.days.first().date)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `mode switch resets selection to latest of new mode`() = runTest {
        val monthly = listOf(bar("2026-04", income = 200.0))
        val annual = listOf(
            bar("2025-01", income = 1000.0).copy(periodEnd = LocalDate.parse("2025-12-31"), label = "2025"),
            bar("2026-01", income = 1500.0).copy(periodEnd = LocalDate.parse("2026-12-31"), label = "2026")
        )
        every { getTimeline(TimelineMode.Monthly) } returns flowOf(
            TimelineData(bars = monthly, days = listOf(DayGroup(LocalDate.parse("2026-04-10"), 200.0, emptyList())))
        )
        every { getTimeline(TimelineMode.Annual) } returns flowOf(
            TimelineData(bars = annual, days = listOf(
                DayGroup(LocalDate.parse("2025-06-01"), 1000.0, emptyList()),
                DayGroup(LocalDate.parse("2026-06-01"), 1500.0, emptyList())
            ))
        )
        every { settings.settings } returns MutableStateFlow(AppSettings())

        val vm = TimelineViewModel(getTimeline, deleteTransaction, settings)

        vm.uiState.test {
            var item = awaitItem()
            while (item.isLoading) item = awaitItem()
            assertEquals(0, item.selectedBarIndex)
            vm.onModeSelected(TimelineMode.Annual)
            var next = awaitItem()
            while (next.mode != TimelineMode.Annual || next.timelineData.bars.size != 2) {
                next = awaitItem()
            }
            assertEquals(1, next.selectedBarIndex)
            assertEquals(1500.0, next.timelineData.headerTotal, 0.0)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
