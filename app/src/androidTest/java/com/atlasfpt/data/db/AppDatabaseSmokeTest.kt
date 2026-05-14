package com.atlasfpt.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atlasfpt.data.db.entity.CategoryEntity
import com.atlasfpt.data.db.entity.TransactionEntity
import com.atlasfpt.domain.model.CategoryType
import com.atlasfpt.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AppDatabaseSmokeTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insertAndObserveTransactionRoundtrip() = runTest {
        val categoryId = db.categoryDao().insert(
            CategoryEntity(name = "Food", iconRes = "restaurant", color = 0, type = CategoryType.EXPENSE)
        )
        val txDate = LocalDate.of(2026, 3, 14)
        db.transactionDao().insert(
            TransactionEntity(
                amount = 12.50,
                type = TransactionType.EXPENSE,
                categoryId = categoryId,
                date = txDate,
                note = "Lunch",
            )
        )

        val transactions = db.transactionDao().observeAll().first()
        assertEquals(1, transactions.size)
        val tx = transactions.single().transaction
        assertEquals(12.50, tx.amount, 0.001)
        assertEquals(txDate, tx.date)
        assertEquals(categoryId, tx.categoryId)
    }
}
