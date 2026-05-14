package com.atlasfpt.ui.feature.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlasfpt.data.repository.PersonRepository
import com.atlasfpt.data.settings.AppSettings
import com.atlasfpt.data.settings.SettingsRepository
import com.atlasfpt.domain.model.Person
import com.atlasfpt.domain.model.TransactionType
import com.atlasfpt.domain.usecase.GetOverviewUseCase
import com.atlasfpt.domain.usecase.OverviewUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import javax.inject.Inject

data class OverviewScreenState(
    val overviewUiState: OverviewUiState = OverviewUiState(),
    val selectedMonth: YearMonth = YearMonth.now(),
    val selectedSide: TransactionType = TransactionType.EXPENSE,
    val selectedPersonIds: Set<Long> = emptySet(),
    val availablePersons: List<Person> = emptyList(),
    val currencySymbol: String = "€"
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val getOverview: GetOverviewUseCase,
    private val settingsRepository: SettingsRepository,
    private val personRepository: PersonRepository
) : ViewModel() {

    val selectedMonth = MutableStateFlow(YearMonth.now())
    private val selectedSide = MutableStateFlow(TransactionType.EXPENSE)
    private val selectedPersonIds = MutableStateFlow<Set<Long>>(emptySet())

    private val overviewFlow = combine(
        selectedMonth,
        selectedPersonIds
    ) { month, ids -> month to ids }
        .flatMapLatest { (month, ids) -> getOverview(month, ids) }

    val uiState: StateFlow<OverviewScreenState> = combine(
        overviewFlow,
        selectedMonth,
        selectedSide,
        selectedPersonIds,
        personRepository.observeAll(),
        settingsRepository.settings
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        OverviewScreenState(
            overviewUiState = values[0] as OverviewUiState,
            selectedMonth = values[1] as YearMonth,
            selectedSide = values[2] as TransactionType,
            selectedPersonIds = values[3] as Set<Long>,
            availablePersons = values[4] as List<Person>,
            currencySymbol = (values[5] as AppSettings).currencySymbol
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OverviewScreenState()
    )

    fun previousMonth() { selectedMonth.value = selectedMonth.value.minusMonths(1) }
    fun nextMonth() { selectedMonth.value = selectedMonth.value.plusMonths(1) }
    fun onSideSelected(side: TransactionType) { selectedSide.value = side }
    fun onPersonsSelected(ids: Set<Long>) { selectedPersonIds.value = ids }
}
