package com.atlasfpt.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.atlasfpt.data.backup.BackupRepository
import com.atlasfpt.data.backup.BackupResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backupRepository: BackupRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return when (val result = backupRepository.runBackup()) {
            is BackupResult.Success -> Result.success()
            is BackupResult.Error -> {
                runCatching { Log.w("BackupWorker", "backup failed: ${result.message}") }
                Result.retry()
            }
        }
    }

    companion object {
        const val WORK_NAME = "atlas_backup"
    }
}
