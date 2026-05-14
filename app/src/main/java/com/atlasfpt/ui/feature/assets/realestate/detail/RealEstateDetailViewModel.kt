package com.atlasfpt.ui.feature.assets.realestate.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlasfpt.data.repository.EuriborRepository
import com.atlasfpt.data.repository.TransactionRepository
import com.atlasfpt.data.settings.SettingsRepository
import com.atlasfpt.domain.model.EuriborRate
import com.atlasfpt.domain.model.RealEstateAsset
import com.atlasfpt.domain.model.ReferenceRate
import com.atlasfpt.domain.model.Transaction
import com.atlasfpt.domain.usecase.GetRealEstateUseCase
import com.atlasfpt.domain.usecase.SetEuriborManualUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class RealEstateDetailUiState(
    val asset: RealEstateAsset? = null,
    val loadError: Boolean = false,
    val linkedTransactions: List<Transaction> = emptyList(),
    val currencySymbol: String = "€",
    val euribor: EuriborRate? = null,
) {
    val equity: Double? get() = asset?.let { it.currentValue - (it.outstandingDebt ?: 0.0) }

    /** Effective rate (in percent) when both euribor + spread are known. Adds spread on top of the cached Euribor. */
    val effectiveRate: Double?
        get() {
            val a = asset ?: return null
            val euri = euribor?.value ?: return null
            val spread = a.spread ?: 0.0
            return euri + spread
        }
}

@HiltViewModel
class RealEstateDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getRealEstate: GetRealEstateUseCase,
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val euriborRepository: EuriborRepository,
    private val setEuriborManual: SetEuriborManualUseCase,
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
                    val cached = asset.referenceRate?.let { euriborRepository.cache.value.get(it) }
                    _state.update { it.copy(asset = asset, euribor = cached) }
                }
            }
            viewModelScope.launch {
                transactionRepository.observeByAssetId(id).collect { txs ->
                    _state.update { it.copy(linkedTransactions = txs) }
                }
            }
            viewModelScope.launch {
                settingsRepository.settings.collect { s ->
                    _state.update { it.copy(currencySymbol = s.currencySymbol) }
                }
            }
            viewModelScope.launch {
                euriborRepository.cache.collect { cache ->
                    val tenor = _state.value.asset?.referenceRate
                    _state.update { it.copy(euribor = tenor?.let(cache::get)) }
                }
            }
        }
    }

    fun onManualEuriborSet(tenor: ReferenceRate, value: Double, asOf: Instant) {
        setEuriborManual(tenor, value, asOf)
    }
}
