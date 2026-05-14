package com.atlasfpt.ui.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlasfpt.data.settings.AppSettings
import com.atlasfpt.data.settings.SettingsRepository
import com.atlasfpt.domain.model.Transaction
import com.atlasfpt.domain.usecase.DeleteTransactionUseCase
import com.atlasfpt.domain.usecase.GetTimelineUseCase
import com.atlasfpt.domain.usecase.TimelineData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class WalletFilter { All }
enum class RangeMode { ByMonths, ByWeeks }

data class TimelineUiState(
    val timelineData: TimelineData = TimelineData(),
    val settings: AppSettings = AppSettings(),
    val pendingDelete: Transaction? = null,
    val walletFilter: WalletFilter = WalletFilter.All,
    val rangeMode: RangeMode = RangeMode.ByMonths,
    val isLoading: Boolean = true
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val getTimeline: GetTimelineUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val pendingDelete = MutableStateFlow<Transaction?>(null)
    private val walletFilter = MutableStateFlow(WalletFilter.All)
    private val rangeMode = MutableStateFlow(RangeMode.ByMonths)

    val uiState: StateFlow<TimelineUiState> = combine(
        getTimeline(),
        settingsRepository.settings,
        pendingDelete,
        walletFilter,
        rangeMode
    ) { data, settings, pending, wallet, range ->
        TimelineUiState(
            timelineData = data,
            settings = settings,
            pendingDelete = pending,
            walletFilter = wallet,
            rangeMode = range,
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
    fun onWalletFilterSelected(filter: WalletFilter) { walletFilter.value = filter }
    fun onRangeModeSelected(mode: RangeMode) { rangeMode.value = mode }
}
