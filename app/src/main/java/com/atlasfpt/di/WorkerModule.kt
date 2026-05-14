package com.atlasfpt.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// HiltWorkerFactory is provided automatically via @HiltAndroidApp + hilt-work dependency.
// Worker bindings are declared via @HiltWorker on RecurringTransactionWorker.
@Module
@InstallIn(SingletonComponent::class)
object WorkerModule
