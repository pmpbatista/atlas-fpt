package com.atlasfpt.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.atlasfpt.data.backup.BackupFrequency
import com.atlasfpt.data.backup.BackupResult
import com.atlasfpt.data.backup.BackupScheduler
import com.atlasfpt.data.repository.FxRatesRepository
import com.atlasfpt.data.settings.AppSettings
import com.atlasfpt.data.settings.SettingsRepository
import com.atlasfpt.domain.model.FxRate
import com.atlasfpt.domain.usecase.RunBackupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    fxRatesRepository: FxRatesRepository,
    private val backupScheduler: BackupScheduler,
    private val runBackup: RunBackupUseCase,
) : ViewModel() {

    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage.asStateFlow()
    private val _backupInProgress = MutableStateFlow(false)
    val backupInProgress: StateFlow<Boolean> = _backupInProgress.asStateFlow()

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

    fun setBackupFolderUri(uri: Uri?) {
        settingsRepository.setBackupFolderUri(uri)
        backupScheduler.apply(settingsRepository.settings.value)
    }

    fun setBackupScheduleEnabled(enabled: Boolean) {
        settingsRepository.setBackupScheduleEnabled(enabled)
        backupScheduler.apply(settingsRepository.settings.value)
    }

    fun setBackupFrequency(frequency: BackupFrequency) {
        settingsRepository.setBackupFrequency(frequency)
        backupScheduler.apply(settingsRepository.settings.value)
    }

    fun setBackupRetentionCount(count: Int) {
        settingsRepository.setBackupRetentionCount(count)
    }

    fun runBackupNow() {
        if (_backupInProgress.value) return
        _backupInProgress.value = true
        viewModelScope.launch {
            val message = when (val r = runBackup()) {
                is BackupResult.Success -> "Backup saved: ${r.filename}"
                is BackupResult.Error -> "Backup failed: ${r.message}"
            }
            _backupMessage.value = message
            _backupInProgress.value = false
        }
    }

    fun clearBackupMessage() { _backupMessage.value = null }
}
