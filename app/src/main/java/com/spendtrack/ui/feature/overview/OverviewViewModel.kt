package com.spendtrack.ui.feature.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtrack.data.settings.SettingsRepository
import com.spendtrack.domain.usecase.GetOverviewUseCase
import com.spendtrack.domain.usecase.OverviewUiState
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
    val currencySymbol: String = "€"
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val getOverview: GetOverviewUseCase,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val selectedMonth = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<OverviewScreenState> = combine(
        selectedMonth.flatMapLatest { month -> getOverview(month) },
        selectedMonth,
        settingsRepository.settings
    ) { overview, month, settings ->
        OverviewScreenState(
            overviewUiState = overview,
            selectedMonth = month,
            currencySymbol = settings.currencySymbol
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OverviewScreenState()
    )

    fun previousMonth() { selectedMonth.value = selectedMonth.value.minusMonths(1) }
    fun nextMonth() { selectedMonth.value = selectedMonth.value.plusMonths(1) }
}
