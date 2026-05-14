package com.atlasfpt.ui.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlasfpt.data.repository.PersonRepository
import com.atlasfpt.data.settings.AppSettings
import com.atlasfpt.data.settings.SettingsRepository
import com.atlasfpt.domain.model.Person
import com.atlasfpt.domain.model.Transaction
import com.atlasfpt.domain.usecase.DeleteTransactionUseCase
import com.atlasfpt.domain.usecase.GetTimelineUseCase
import com.atlasfpt.domain.usecase.TimelineData
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

enum class WalletFilter { All }
enum class RangeMode { ByMonths, ByWeeks }

data class TimelineUiState(
    val timelineData: TimelineData = TimelineData(),
    val settings: AppSettings = AppSettings(),
    val pendingDelete: Transaction? = null,
    val walletFilter: WalletFilter = WalletFilter.All,
    val rangeMode: RangeMode = RangeMode.ByMonths,
    val selectedPersonIds: Set<Long> = emptySet(),
    val availablePersons: List<Person> = emptyList(),
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val getTimeline: GetTimelineUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val settingsRepository: SettingsRepository,
    private val personRepository: PersonRepository
) : ViewModel() {

    private val pendingDelete = MutableStateFlow<Transaction?>(null)
    private val walletFilter = MutableStateFlow(WalletFilter.All)
    private val rangeMode = MutableStateFlow(RangeMode.ByMonths)
    private val selectedPersonIds = MutableStateFlow<Set<Long>>(emptySet())

    private val timelineFlow = selectedPersonIds
        .flatMapLatest { ids -> getTimeline(ids) }

    val uiState: StateFlow<TimelineUiState> = combine(
        timelineFlow,
        settingsRepository.settings,
        pendingDelete,
        walletFilter,
        rangeMode,
        selectedPersonIds,
        personRepository.observeAll()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        TimelineUiState(
            timelineData = values[0] as TimelineData,
            settings = values[1] as AppSettings,
            pendingDelete = values[2] as Transaction?,
            walletFilter = values[3] as WalletFilter,
            rangeMode = values[4] as RangeMode,
            selectedPersonIds = values[5] as Set<Long>,
            availablePersons = values[6] as List<Person>,
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
    fun onPersonsSelected(ids: Set<Long>) { selectedPersonIds.value = ids }
}
