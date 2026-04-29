package com.spendtrack.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.spendtrack.domain.usecase.MaterialiseRecurringTransactionsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class RecurringTransactionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val materialise: MaterialiseRecurringTransactionsUseCase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            materialise()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "recurring_transactions"
    }
}
