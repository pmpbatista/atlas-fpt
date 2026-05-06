package com.spendtrack.ui.feature.assets.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtrack.domain.model.AssetListItem
import com.spendtrack.domain.model.TotalWealth
import com.spendtrack.domain.usecase.GetAssetsListUseCase
import com.spendtrack.domain.usecase.GetTotalWealthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AssetsListUiState(
    val items: List<AssetListItem> = emptyList(),
    val total: TotalWealth = TotalWealth(emptyMap(), 0),
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

@HiltViewModel
class AssetsListViewModel @Inject constructor(
    getList: GetAssetsListUseCase,
    getTotal: GetTotalWealthUseCase,
) : ViewModel() {

    val uiState: StateFlow<AssetsListUiState> = combine(
        getList(),
        getTotal()
    ) { items, total ->
        AssetsListUiState(items = items, total = total)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AssetsListUiState()
    )
}
