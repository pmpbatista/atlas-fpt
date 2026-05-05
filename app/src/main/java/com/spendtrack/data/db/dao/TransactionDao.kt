package com.spendtrack.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.spendtrack.data.db.entity.TransactionEntity
import com.spendtrack.data.db.entity.TransactionLabelCrossRef
import com.spendtrack.data.db.entity.TransactionPersonCrossRef
import com.spendtrack.data.db.entity.TransactionWithDetails
import com.spendtrack.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface TransactionDao {

    @Transaction
    @Query("SELECT * FROM transactions WHERE isScheduled = 0 ORDER BY date DESC")
    fun observeAll(): Flow<List<TransactionWithDetails>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE isScheduled = 0 AND date BETWEEN :from AND :to ORDER BY date DESC")
    fun observeByDateRange(from: LocalDate, to: LocalDate): Flow<List<TransactionWithDetails>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE isScheduled = 1 ORDER BY date ASC")
    fun observeScheduled(): Flow<List<TransactionWithDetails>>

    @Query("""
        SELECT date,
            SUM(CASE WHEN type='EXPENSE' THEN amount ELSE 0 END) as totalExpense,
            SUM(CASE WHEN type='INCOME' THEN amount ELSE 0 END) as totalIncome
        FROM transactions
        WHERE isScheduled = 0 AND date BETWEEN :from AND :to
        GROUP BY date
        ORDER BY date DESC
    """)
    fun observeDailySummaries(from: LocalDate, to: LocalDate): Flow<List<DailySummary>>

    @Query("""
        SELECT categoryId, SUM(amount) as total
        FROM transactions
        WHERE isScheduled = 0 AND type = :type AND date BETWEEN :from AND :to
        GROUP BY categoryId
    """)
    fun getCategoryTotals(
        type: TransactionType,
        from: LocalDate,
        to: LocalDate
    ): Flow<List<CategoryTotal>>

    @Query("""
        SELECT strftime('%Y-%m', date) as month,
            SUM(CASE WHEN type='EXPENSE' THEN amount ELSE 0 END) as totalExpense,
            SUM(CASE WHEN type='INCOME' THEN amount ELSE 0 END) as totalIncome
        FROM transactions
        WHERE isScheduled = 0
        GROUP BY month
        ORDER BY month DESC
        LIMIT 12
    """)
    fun observeMonthlySummaries(): Flow<List<MonthlySummary>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE recurringRuleId = :ruleId AND isScheduled = 1")
    suspend fun deleteScheduledForRule(ruleId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(crossRef: TransactionLabelCrossRef)

    @Query("DELETE FROM transaction_label_cross_ref WHERE transactionId = :transactionId")
    suspend fun deleteAllLabelsForTransaction(transactionId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPersonCrossRef(crossRef: TransactionPersonCrossRef)

    @Query("DELETE FROM transaction_person_cross_ref WHERE transactionId = :transactionId")
    suspend fun deleteAllPersonsForTransaction(transactionId: Long)

    @Query("SELECT COUNT(*) FROM transactions WHERE recurringRuleId = :ruleId AND isScheduled = 0")
    suspend fun countRealTransactionsForRule(ruleId: Long): Int

    @Transaction
    @Query("SELECT * FROM transactions WHERE recurringRuleId = :ruleId AND isScheduled = 0 ORDER BY date DESC LIMIT 1")
    suspend fun getLatestForRule(ruleId: Long): TransactionWithDetails?

    @Transaction
    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionWithDetails?
}

data class DailySummary(val date: LocalDate, val totalExpense: Double, val totalIncome: Double)
data class MonthlySummary(val month: String, val totalExpense: Double, val totalIncome: Double)
data class CategoryTotal(val categoryId: Long, val total: Double)
