package com.spendtrack.di

import android.content.Context
import com.spendtrack.data.db.AppDatabase
import com.spendtrack.data.db.dao.CategoryDao
import com.spendtrack.data.db.dao.LabelDao
import com.spendtrack.data.db.dao.RecurringRuleDao
import com.spendtrack.data.db.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.create(context)

    @Provides
    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideLabelDao(db: AppDatabase): LabelDao = db.labelDao()

    @Provides
    fun provideRecurringRuleDao(db: AppDatabase): RecurringRuleDao = db.recurringRuleDao()
}
