package com.atlasfpt.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.atlasfpt.data.repository.PriceRepository
import com.atlasfpt.data.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class RefreshPricesWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val priceRepository: PriceRepository,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!settingsRepository.settings.value.backgroundRefreshEnabled) {
            return Result.success()
        }
        return try {
            val result = priceRepository.refreshAll()
            if (result.succeeded > 0) {
                settingsRepository.setLastPriceRefreshAt(System.currentTimeMillis())
            }
            when {
                result.failed == 0 -> Result.success()
                result.succeeded > 0 -> Result.success()      // partial success — not worth retrying
                else -> Result.retry()                         // all failed — try again later
            }
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "refresh_prices"
    }
}
