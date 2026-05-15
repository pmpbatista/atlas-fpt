package com.atlasfpt.data.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.atlasfpt.data.settings.AppSettings
import com.atlasfpt.data.worker.BackupWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun apply(settings: AppSettings) {
        val wm = WorkManager.getInstance(context)
        if (settings.backupScheduleEnabled && settings.backupFolderUri != null) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(
                settings.backupFrequency.repeatInterval,
                settings.backupFrequency.repeatUnit,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                .build()
            wm.enqueueUniquePeriodicWork(
                BackupWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        } else {
            wm.cancelUniqueWork(BackupWorker.WORK_NAME)
        }
    }
}
