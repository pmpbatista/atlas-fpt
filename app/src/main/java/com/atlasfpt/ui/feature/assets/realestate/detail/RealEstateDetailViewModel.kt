package com.atlasfpt.ui.feature.assets.realestate.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlasfpt.domain.model.RealEstateAsset
import com.atlasfpt.domain.usecase.GetRealEstateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RealEstateDetailUiState(
    val asset: RealEstateAsset? = null,
    val loadError: Boolean = false,
) {
    val equity: Double? get() = asset?.let { it.currentValue - (it.outstandingDebt ?: 0.0) }
}

@HiltViewModel
class RealEstateDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getRealEstate: GetRealEstateUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(RealEstateDetailUiState())
    val uiState: StateFlow<RealEstateDetailUiState> = _state.asStateFlow()

    init {
        val id = savedStateHandle.get<String>("assetId")?.toLongOrNull()
        if (id == null) {
            _state.update { it.copy(loadError = true) }
        } else {
            viewModelScope.launch {
                val asset = runCatching { getRealEstate(id) }.getOrNull()
                if (asset == null) {
                    _state.update { it.copy(loadError = true) }
                } else {
                    _state.update { it.copy(asset = asset) }
                }
            }
        }
    }
}
