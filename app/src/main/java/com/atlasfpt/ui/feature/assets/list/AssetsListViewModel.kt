package com.atlasfpt.ui.feature.assets.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlasfpt.data.settings.AppSettings
import com.atlasfpt.data.settings.SettingsRepository
import com.atlasfpt.domain.model.AssetListItem
import com.atlasfpt.domain.model.TotalWealth
import com.atlasfpt.domain.usecase.GetAssetsListUseCase
import com.atlasfpt.domain.usecase.GetTotalWealthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AssetsListUiState(
    val items: List<AssetListItem> = emptyList(),
    val total: TotalWealth = TotalWealth(emptyMap(), emptyMap()),
    val settings: AppSettings = AppSettings(),
    val isRefreshing: Boolean = false,
    val refreshMessage: String? = null,
) {
    val isEmpty: Boolean get() = items.isEmpty()
    val hasFinancial: Boolean get() = items.any { it.type == com.atlasfpt.domain.model.AssetType.FINANCIAL }
}

@HiltViewModel
class AssetsListViewModel @Inject constructor(
    getList: GetAssetsListUseCase,
    getTotal: GetTotalWealthUseCase,
    private val refreshPrices: com.atlasfpt.domain.usecase.RefreshPricesUseCase,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val refreshMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AssetsListUiState> = combine(
        combine(getList(), getTotal(), settingsRepository.settings) { items, total, settings ->
            Triple(items, total, settings)
        },
        combine(refreshing, refreshMessage) { isRefreshing, message ->
            Pair(isRefreshing, message)
        },
    ) { (items, total, settings), (isRefreshing, message) ->
        AssetsListUiState(
            items = items,
            total = total,
            settings = settings,
            isRefreshing = isRefreshing,
            refreshMessage = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AssetsListUiState(),
    )

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
        }
    }

    fun clearRefreshMessage() {
        refreshMessage.value = null
    }
}
