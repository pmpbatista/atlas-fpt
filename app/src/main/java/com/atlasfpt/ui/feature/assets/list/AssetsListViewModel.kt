package com.atlasfpt.ui.feature.assets.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlasfpt.data.repository.FxRatesRepository
import com.atlasfpt.data.settings.AppSettings
import com.atlasfpt.data.settings.SettingsRepository
import com.atlasfpt.domain.model.AssetListItem
import com.atlasfpt.domain.model.ChartRange
import com.atlasfpt.domain.model.PricePoint
import com.atlasfpt.domain.model.TotalWealth
import com.atlasfpt.domain.usecase.GetAssetsListUseCase
import com.atlasfpt.domain.usecase.GetPortfolioValueHistoryUseCase
import com.atlasfpt.domain.usecase.GetTotalWealthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AssetsListUiState(
    val items: List<AssetListItem> = emptyList(),
    val total: TotalWealth = TotalWealth(emptyMap(), emptyMap()),
    val settings: AppSettings = AppSettings(),
    val isRefreshing: Boolean = false,
    val refreshMessage: String? = null,
    val chartRange: ChartRange = ChartRange.SIX_MONTHS,
    val portfolioHistory: List<PricePoint> = emptyList(),
    val isChartLoading: Boolean = false,
) {
    val isEmpty: Boolean get() = items.isEmpty()
    val hasFinancial: Boolean get() = items.any { it.type == com.atlasfpt.domain.model.AssetType.FINANCIAL }
}

@HiltViewModel
class AssetsListViewModel @Inject constructor(
    getList: GetAssetsListUseCase,
    getTotal: GetTotalWealthUseCase,
    private val refreshPrices: com.atlasfpt.domain.usecase.RefreshPricesUseCase,
    private val getPortfolioHistory: GetPortfolioValueHistoryUseCase,
    private val fxRatesRepository: FxRatesRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val refreshMessage = MutableStateFlow<String?>(null)
    private val chartRange = MutableStateFlow(ChartRange.SIX_MONTHS)
    private val portfolioHistory = MutableStateFlow<List<PricePoint>>(emptyList())
    private val chartLoading = MutableStateFlow(false)

    val uiState: StateFlow<AssetsListUiState> = combine(
        combine(getList(), getTotal(), settingsRepository.settings) { items, total, settings ->
            Triple(items, total, settings)
        },
        combine(refreshing, refreshMessage) { isRefreshing, message ->
            Pair(isRefreshing, message)
        },
        combine(chartRange, portfolioHistory, chartLoading) { range, history, loading ->
            Triple(range, history, loading)
        },
    ) { (items, total, settings), (isRefreshing, message), (range, history, loading) ->
        AssetsListUiState(
            items = items,
            total = total,
            settings = settings,
            isRefreshing = isRefreshing,
            refreshMessage = message,
            chartRange = range,
            portfolioHistory = history,
            isChartLoading = loading,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AssetsListUiState(),
    )

    init { refreshChart() }

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            val result = runCatching { refreshPrices() }.getOrNull()
            refreshing.value = false
            if (result != null && result.failed > 0) {
                refreshMessage.value = "Couldn't refresh ${result.failed} of ${result.succeeded + result.failed} assets"
            } else if (result == null) {
                refreshMessage.value = "Couldn't refresh prices"
            }
            refreshChart()
        }
    }

    fun onChartRange(range: ChartRange) {
        if (range == chartRange.value) return
        chartRange.value = range
        refreshChart()
    }

    private fun refreshChart() {
        chartLoading.value = true
        viewModelScope.launch {
            val rates = fxRatesRepository.observeRates().first()
            val displayCurrency = settingsRepository.settings.value.displayCurrencyCode
            val history = runCatching {
                getPortfolioHistory(chartRange.value, displayCurrency, rates)
            }.getOrDefault(emptyList())
            portfolioHistory.value = history
            chartLoading.value = false
        }
    }

    fun clearRefreshMessage() {
        refreshMessage.value = null
    }
}
