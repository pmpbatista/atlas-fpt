package com.atlasfpt.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlasfpt.data.repository.FxRatesRepository
import com.atlasfpt.data.settings.AppSettings
import com.atlasfpt.data.settings.SettingsRepository
import com.atlasfpt.domain.model.FxRate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    fxRatesRepository: FxRatesRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppSettings()
        )

    val fxRates: StateFlow<Map<String, FxRate>> = fxRatesRepository.observeRates()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )

    fun updateCurrency(symbol: String, code: String) {
        viewModelScope.launch {
            settingsRepository.updateCurrency(symbol, code)
        }
    }

    fun setBackgroundRefreshEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBackgroundRefreshEnabled(enabled)
        }
    }

    fun setDisplayCurrencyCode(code: String) {
        viewModelScope.launch {
            settingsRepository.setDisplayCurrencyCode(code)
        }
    }
}
