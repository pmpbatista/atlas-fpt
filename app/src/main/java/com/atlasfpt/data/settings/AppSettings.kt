package com.atlasfpt.data.settings

import com.atlasfpt.data.backup.BackupFrequency

data class AppSettings(
    val currencySymbol: String = "€",
    val currencyCode: String = "EUR",
    val backgroundRefreshEnabled: Boolean = true,
    val lastPriceRefreshAt: Long? = null,
    /**
     * Currency used to display converted aggregates (e.g. net wealth grand total).
     * Defaults to [currencyCode] when not explicitly set.
     */
    val displayCurrencyCode: String = "EUR",
    val backupFolderUri: String? = null,
    val backupScheduleEnabled: Boolean = false,
    val backupFrequency: BackupFrequency = BackupFrequency.WEEKLY,
    val backupRetentionCount: Int = 5,
    val lastBackupAt: Long? = null,
    val lastBackupFilename: String? = null,
)
