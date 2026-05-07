package com.spendtrack.ui.feature.assets.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtrack.domain.model.AssetListItem
import com.spendtrack.domain.model.TotalWealth
import com.spendtrack.domain.usecase.GetAssetsListUseCase
import com.spendtrack.domain.usecase.GetTotalWealthUseCase
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
    val total: TotalWealth = TotalWealth(emptyMap(), 0),
    val isRefreshing: Boolean = false,
    val refreshMessage: String? = null,
) {
    val isEmpty: Boolean get() = items.isEmpty()
    val hasFinancial: Boolean get() = items.any { it.type == com.spendtrack.domain.model.AssetType.FINANCIAL }
}

@HiltViewModel
class AssetsListViewModel @Inject constructor(
    getList: GetAssetsListUseCase,
    getTotal: GetTotalWealthUseCase,
    private val refreshPrices: com.spendtrack.domain.usecase.RefreshPricesUseCase,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val refreshMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AssetsListUiState> = combine(
        getList(),
        getTotal(),
        refreshing,
        refreshMessage,
    ) { items, total, isRefreshing, message ->
        AssetsListUiState(
            items = items,
            total = total,
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
