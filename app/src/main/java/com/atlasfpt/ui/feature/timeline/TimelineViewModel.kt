package com.atlasfpt.ui.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlasfpt.data.settings.AppSettings
import com.atlasfpt.data.settings.SettingsRepository
import com.atlasfpt.domain.model.Transaction
import com.atlasfpt.domain.usecase.CashFlowBar
import com.atlasfpt.domain.usecase.DeleteTransactionUseCase
import com.atlasfpt.domain.usecase.GetTimelineUseCase
import com.atlasfpt.domain.usecase.TimelineData
import com.atlasfpt.domain.usecase.TimelineMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimelineUiState(
    val timelineData: TimelineData = TimelineData(),
    val settings: AppSettings = AppSettings(),
    val pendingDelete: Transaction? = null,
    val mode: TimelineMode = TimelineMode.Monthly,
    val selectedBarIndex: Int = -1,
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val getTimeline: GetTimelineUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val pendingDelete = MutableStateFlow<Transaction?>(null)
    private val mode = MutableStateFlow(TimelineMode.Monthly)
    // null = follow the latest bar; an explicit index pins the selection.
    private val selectedBarIndex = MutableStateFlow<Int?>(null)

    private val timelineFlow = mode.flatMapLatest { m -> getTimeline(m) }

    val uiState: StateFlow<TimelineUiState> = combine(
        timelineFlow,
        settingsRepository.settings,
        pendingDelete,
        mode,
        selectedBarIndex
    ) { data, settings, pending, m, selIdx ->
        val effectiveIdx = effectiveIndex(data.bars, selIdx)
        val scoped = scopeToSelectedBar(data, effectiveIdx)
        TimelineUiState(
            timelineData = scoped,
            settings = settings,
            pendingDelete = pending,
            mode = m,
            selectedBarIndex = effectiveIdx,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TimelineUiState()
    )

    private fun effectiveIndex(bars: List<CashFlowBar>, pinned: Int?): Int {
        if (bars.isEmpty()) return -1
        val maxIdx = bars.size - 1
        return pinned?.coerceIn(0, maxIdx) ?: maxIdx
    }

    private fun scopeToSelectedBar(data: TimelineData, idx: Int): TimelineData {
        val bar = data.bars.getOrNull(idx) ?: return data
        val range = bar.periodStart..bar.periodEnd
        val scopedDays = data.days.filter { it.date in range }
        return data.copy(
            headerTotal = scopedDays.sumOf { it.net },
            days = scopedDays
        )
    }

    fun requestDelete(transaction: Transaction) {
        pendingDelete.value = transaction
        viewModelScope.launch {
            delay(5_000)
            val pending = pendingDelete.value
            if (pending?.id == transaction.id) {
                deleteTransaction(transaction)
                pendingDelete.value = null
            }
        }
    }

    fun undoDelete() { pendingDelete.value = null }

    fun onModeSelected(newMode: TimelineMode) {
        mode.value = newMode
        selectedBarIndex.value = null
    }

    fun onBarSelected(index: Int) {
        selectedBarIndex.value = index
    }
}
