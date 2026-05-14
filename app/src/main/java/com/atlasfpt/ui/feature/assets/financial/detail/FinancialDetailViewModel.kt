package com.atlasfpt.ui.feature.assets.financial.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlasfpt.data.repository.PriceRepository
import com.atlasfpt.domain.model.FinancialAsset
import com.atlasfpt.domain.usecase.DeleteAssetUseCase
import com.atlasfpt.domain.usecase.DeleteLotUseCase
import com.atlasfpt.domain.usecase.GetFinancialAssetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FinancialDetailUiState(
    val asset: FinancialAsset? = null,
    val loadError: Boolean = false,
    val isRefreshing: Boolean = false,
    val isDeleted: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class FinancialDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getAsset: GetFinancialAssetUseCase,
    private val deleteAssetUseCase: DeleteAssetUseCase,
    private val deleteLotUseCase: DeleteLotUseCase,
    private val priceRepository: PriceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FinancialDetailUiState())
    val uiState: StateFlow<FinancialDetailUiState> = _state.asStateFlow()

    private val assetId: Long = savedStateHandle.get<String>("assetId")?.toLongOrNull() ?: 0L

    init {
        if (assetId == 0L) {
            _state.update { it.copy(loadError = true) }
        } else {
            loadAsset()
        }
    }

    private fun loadAsset() {
        viewModelScope.launch {
            val asset = runCatching { getAsset(assetId) }.getOrNull()
            if (asset == null) _state.update { it.copy(loadError = true) }
            else _state.update { it.copy(asset = asset, loadError = false) }
        }
    }

    fun refresh() {
        val ticker = _state.value.asset?.ticker ?: return
        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            runCatching { priceRepository.getQuote(ticker, force = true) }
            // Re-load to pick up persisted price update
            val updated = runCatching { getAsset(assetId) }.getOrNull()
            _state.update { it.copy(isRefreshing = false, asset = updated ?: it.asset) }
        }
    }

    fun deleteLot(lotId: Long) {
        viewModelScope.launch {
            try {
                val assetDeleted = deleteLotUseCase(assetId, lotId)
                if (assetDeleted) {
                    _state.update { it.copy(isDeleted = true) }
                } else {
                    val updated = getAsset(assetId)
                    _state.update { it.copy(asset = updated ?: it.asset) }
                }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = "Couldn't delete lot. Try again.") }
            }
        }
    }

    fun deleteAsset() {
        viewModelScope.launch {
            try {
                deleteAssetUseCase(assetId)
                _state.update { it.copy(isDeleted = true) }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = "Couldn't delete asset. Try again.") }
            }
        }
    }

    fun clearErrorMessage() = _state.update { it.copy(errorMessage = null) }
}
