package com.atlasfpt.data.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.atlasfpt.data.backup.BackupFrequency
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

    private fun loadSettings(): AppSettings {
        val currencyCode = prefs.getString("currency_code", "EUR") ?: "EUR"
        return AppSettings(
            currencySymbol = prefs.getString("currency_symbol", "€") ?: "€",
            currencyCode = currencyCode,
            backgroundRefreshEnabled = prefs.getBoolean("bg_refresh_enabled", true),
            lastPriceRefreshAt = if (prefs.contains("last_price_refresh_at"))
                prefs.getLong("last_price_refresh_at", 0L)
            else null,
            displayCurrencyCode = prefs.getString("display_currency_code", currencyCode) ?: currencyCode,
            backupFolderUri = prefs.getString("backup_folder_uri", null),
            backupScheduleEnabled = prefs.getBoolean("backup_schedule_enabled", false),
            backupFrequency = prefs.getString("backup_frequency", BackupFrequency.WEEKLY.name)
                ?.let { runCatching { BackupFrequency.valueOf(it) }.getOrNull() }
                ?: BackupFrequency.WEEKLY,
            backupRetentionCount = prefs.getInt("backup_retention_count", 5),
            lastBackupAt = if (prefs.contains("last_backup_at"))
                prefs.getLong("last_backup_at", 0L)
            else null,
            lastBackupFilename = prefs.getString("last_backup_filename", null),
        )
    }

    fun updateCurrency(symbol: String, code: String) {
        prefs.edit()
            .putString("currency_symbol", symbol)
            .putString("currency_code", code)
            .apply()
        _settings.value = loadSettings()
    }

    fun setBackgroundRefreshEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("bg_refresh_enabled", enabled).apply()
        _settings.value = loadSettings()
    }

    fun setLastPriceRefreshAt(millis: Long) {
        prefs.edit().putLong("last_price_refresh_at", millis).apply()
        _settings.value = loadSettings()
    }

    fun setDisplayCurrencyCode(code: String) {
        prefs.edit().putString("display_currency_code", code).apply()
        _settings.value = loadSettings()
    }

    fun setBackupFolderUri(uri: Uri?) {
        val previous = prefs.getString("backup_folder_uri", null)
        if (previous != null && previous != uri?.toString()) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(previous),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        prefs.edit().apply {
            if (uri == null) remove("backup_folder_uri")
            else putString("backup_folder_uri", uri.toString())
        }.apply()
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        _settings.value = loadSettings()
    }

    fun setBackupScheduleEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("backup_schedule_enabled", enabled).apply()
        _settings.value = loadSettings()
    }

    fun setBackupFrequency(frequency: BackupFrequency) {
        prefs.edit().putString("backup_frequency", frequency.name).apply()
        _settings.value = loadSettings()
    }

    fun setBackupRetentionCount(count: Int) {
        prefs.edit().putInt("backup_retention_count", count.coerceAtLeast(0)).apply()
        _settings.value = loadSettings()
    }

    fun setLastBackup(at: Long, filename: String) {
        prefs.edit()
            .putLong("last_backup_at", at)
            .putString("last_backup_filename", filename)
            .apply()
        _settings.value = loadSettings()
    }
}
