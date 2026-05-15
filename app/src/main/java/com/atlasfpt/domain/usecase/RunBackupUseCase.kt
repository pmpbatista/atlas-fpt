package com.atlasfpt.domain.usecase

import com.atlasfpt.data.backup.BackupRepository
import com.atlasfpt.data.backup.BackupResult
import javax.inject.Inject

class RunBackupUseCase @Inject constructor(
    private val repository: BackupRepository,
) {
    suspend operator fun invoke(): BackupResult = repository.runBackup()
}
