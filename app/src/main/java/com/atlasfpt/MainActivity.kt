package com.atlasfpt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.atlasfpt.data.worker.RecurringTransactionWorker
import com.atlasfpt.data.worker.RefreshPricesWorker
import com.atlasfpt.ui.navigation.AppNavGraph
import com.atlasfpt.ui.theme.AtlasTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        scheduleRecurringWorker()
        schedulePriceRefreshWorker()

        setContent {
            AtlasTheme {
                AppNavGraph()
            }
        }
    }

    private fun scheduleRecurringWorker() {
        val request = PeriodicWorkRequestBuilder<RecurringTransactionWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            RecurringTransactionWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun schedulePriceRefreshWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()
        val request = PeriodicWorkRequestBuilder<RefreshPricesWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            RefreshPricesWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
