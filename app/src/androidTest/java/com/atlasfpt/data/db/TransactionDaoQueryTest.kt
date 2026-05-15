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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class TransactionDaoQueryTest {

    private lateinit var db: AppDatabase
    private var expenseFoodId: Long = 0
    private var expenseRentId: Long = 0
    private var incomeSalaryId: Long = 0

    @Before
    fun setup() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        expenseFoodId = db.categoryDao().insert(
            CategoryEntity(name = "Food", iconRes = "restaurant", color = 0, type = CategoryType.EXPENSE)
        )
        expenseRentId = db.categoryDao().insert(
            CategoryEntity(name = "Rent", iconRes = "home", color = 0, type = CategoryType.EXPENSE)
        )
        incomeSalaryId = db.categoryDao().insert(
            CategoryEntity(name = "Salary", iconRes = "work", color = 0, type = CategoryType.INCOME)
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun observeByDateRange_includesInclusiveBounds() = runTest {
        insertExpense(LocalDate.of(2026, 1, 1), 10.0)
        insertExpense(LocalDate.of(2026, 1, 15), 20.0)
        insertExpense(LocalDate.of(2026, 1, 31), 30.0)
        insertExpense(LocalDate.of(2026, 2, 1), 40.0)

        val rows = db.transactionDao().observeByDateRange(
            from = LocalDate.of(2026, 1, 1),
            to = LocalDate.of(2026, 1, 31),
        ).first()

        assertEquals(3, rows.size)
    }

    @Test
    fun observeByDateRange_excludesScheduledTransactions() = runTest {
        insertExpense(LocalDate.of(2026, 1, 10), 10.0)
        db.transactionDao().insert(
            TransactionEntity(
                amount = 99.0,
                type = TransactionType.EXPENSE,
                categoryId = expenseFoodId,
                date = LocalDate.of(2026, 1, 11),
                isScheduled = true,
            )
        )

        val rows = db.transactionDao().observeByDateRange(
            from = LocalDate.of(2026, 1, 1),
            to = LocalDate.of(2026, 1, 31),
        ).first()

        assertEquals(1, rows.size)
        assertEquals(10.0, rows.single().transaction.amount, 0.001)
    }

    @Test
    fun getCategoryTotals_sumsPerCategoryAndFiltersByType() = runTest {
        insertExpense(LocalDate.of(2026, 1, 5), 10.0, expenseFoodId)
        insertExpense(LocalDate.of(2026, 1, 6), 5.0, expenseFoodId)
        insertExpense(LocalDate.of(2026, 1, 7), 800.0, expenseRentId)
        db.transactionDao().insert(
            TransactionEntity(
                amount = 2500.0,
                type = TransactionType.INCOME,
                categoryId = incomeSalaryId,
                date = LocalDate.of(2026, 1, 8),
            )
        )

        val totals = db.transactionDao().getCategoryTotals(
            type = TransactionType.EXPENSE,
            from = LocalDate.of(2026, 1, 1),
            to = LocalDate.of(2026, 1, 31),
        ).first()

        assertEquals(2, totals.size)
        val byCat = totals.associateBy { it.categoryId }
        assertNotNull(byCat[expenseFoodId])
        assertEquals(15.0, byCat[expenseFoodId]!!.total, 0.001)
        assertEquals(2, byCat[expenseFoodId]!!.count)
        assertEquals(800.0, byCat[expenseRentId]!!.total, 0.001)
        assertNull(byCat[incomeSalaryId])
    }

    @Test
    fun observeMonthlySummaries_groupsByYearMonth() = runTest {
        insertExpense(LocalDate.of(2026, 1, 10), 30.0)
        insertExpense(LocalDate.of(2026, 1, 20), 70.0)
        db.transactionDao().insert(
            TransactionEntity(
                amount = 2000.0,
                type = TransactionType.INCOME,
                categoryId = incomeSalaryId,
                date = LocalDate.of(2026, 1, 25),
            )
        )
        insertExpense(LocalDate.of(2026, 2, 5), 50.0)

        val months = db.transactionDao().observeMonthlySummaries().first()
        assertEquals(2, months.size)

        val byMonth = months.associateBy { it.month }
        assertEquals(100.0, byMonth["2026-01"]!!.totalExpense, 0.001)
        assertEquals(2000.0, byMonth["2026-01"]!!.totalIncome, 0.001)
        assertEquals(50.0, byMonth["2026-02"]!!.totalExpense, 0.001)
        assertEquals(0.0, byMonth["2026-02"]!!.totalIncome, 0.001)
    }

    private suspend fun insertExpense(
        date: LocalDate,
        amount: Double,
        categoryId: Long = expenseFoodId,
    ): Long = db.transactionDao().insert(
        TransactionEntity(
            amount = amount,
            type = TransactionType.EXPENSE,
            categoryId = categoryId,
            date = date,
        )
    )
}
