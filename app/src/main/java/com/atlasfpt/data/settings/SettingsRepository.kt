package com.atlasfpt.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs = context.getSharedPreferences("spendtrack_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings() = AppSettings(
        currencySymbol = prefs.getString("currency_symbol", "€") ?: "€",
        currencyCode = prefs.getString("currency_code", "EUR") ?: "EUR"
    )

    fun updateCurrency(symbol: String, code: String) {
        prefs.edit()
            .putString("currency_symbol", symbol)
            .putString("currency_code", code)
            .apply()
        _settings.value = loadSettings()
    }
}
