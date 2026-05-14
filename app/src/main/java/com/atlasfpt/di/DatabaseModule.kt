package com.atlasfpt.di

import android.content.Context
import com.atlasfpt.data.db.AppDatabase
import com.atlasfpt.data.db.dao.AssetDao
import com.atlasfpt.data.db.dao.CategoryDao
import com.atlasfpt.data.db.dao.FinancialDao
import com.atlasfpt.data.db.dao.LabelDao
import com.atlasfpt.data.db.dao.PersonDao
import com.atlasfpt.data.db.dao.RealEstateDao
import com.atlasfpt.data.db.dao.RecurringRuleDao
import com.atlasfpt.data.db.dao.TransactionDao
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

    @Provides
    fun providePersonDao(db: AppDatabase): PersonDao = db.personDao()

    @Provides
    fun provideAssetDao(db: AppDatabase): AssetDao = db.assetDao()

    @Provides
    fun provideRealEstateDao(db: AppDatabase): RealEstateDao = db.realEstateDao()

    @Provides
    fun provideFinancialDao(db: AppDatabase): FinancialDao = db.financialDao()
}
