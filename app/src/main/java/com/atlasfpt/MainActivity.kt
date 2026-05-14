package com.atlasfpt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.atlasfpt.data.worker.RecurringTransactionWorker
import com.atlasfpt.ui.navigation.AppNavGraph
import com.atlasfpt.ui.theme.AtlasTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        scheduleRecurringWorker()

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
            request
        )
    }
}
