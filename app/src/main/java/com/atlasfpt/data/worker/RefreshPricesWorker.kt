package com.atlasfpt.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.atlasfpt.data.repository.PriceRepository
import com.atlasfpt.data.settings.SettingsRepository
import com.atlasfpt.domain.usecase.RefreshEuriborUseCase
import com.atlasfpt.domain.usecase.RefreshFxRatesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class RefreshPricesWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val priceRepository: PriceRepository,
    private val settingsRepository: SettingsRepository,
    private val refreshEuribor: RefreshEuriborUseCase,
    private val refreshFxRates: RefreshFxRatesUseCase,
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
            runCatching { refreshEuribor() }
            runCatching { refreshFxRates() }
            when {
                result.failed == 0 -> Result.success()
                result.succeeded > 0 -> Result.success()
                else -> Result.retry()
            }
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "refresh_prices"
    }
}
