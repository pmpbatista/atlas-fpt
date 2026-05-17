package com.atlasfpt.ui.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlasfpt.data.settings.AppSettings
import com.atlasfpt.data.settings.SettingsRepository
import com.atlasfpt.domain.model.Transaction
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

    private val timelineFlow = mode.flatMapLatest { m -> getTimeline(m) }

    val uiState: StateFlow<TimelineUiState> = combine(
        timelineFlow,
        settingsRepository.settings,
        pendingDelete,
        mode
    ) { timeline, settings, pending, m ->
        TimelineUiState(
            timelineData = timeline,
            settings = settings,
            pendingDelete = pending,
            mode = m,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TimelineUiState()
    )

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
    fun onModeSelected(newMode: TimelineMode) { mode.value = newMode }
}
