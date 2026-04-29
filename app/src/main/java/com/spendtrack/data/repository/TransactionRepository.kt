package com.spendtrack.data.repository

import com.spendtrack.data.db.dao.CategoryTotal
import com.spendtrack.data.db.dao.DailySummary
import com.spendtrack.data.db.dao.MonthlySummary
import com.spendtrack.data.db.dao.TransactionDao
import com.spendtrack.data.db.entity.TransactionLabelCrossRef
import com.spendtrack.data.db.entity.toDomain
import com.spendtrack.data.db.entity.toEntity
import com.spendtrack.domain.model.Transaction
import com.spendtrack.domain.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(private val dao: TransactionDao) {

    fun observeAll(): Flow<List<Transaction>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeByDateRange(from: LocalDate, to: LocalDate): Flow<List<Transaction>> =
        dao.observeByDateRange(from, to).map { list -> list.map { it.toDomain() } }

    fun observeScheduled(): Flow<List<Transaction>> =
        dao.observeScheduled().map { list -> list.map { it.toDomain() } }

    fun observeDailySummaries(from: LocalDate, to: LocalDate): Flow<List<DailySummary>> =
        dao.observeDailySummaries(from, to)

    fun getCategoryTotals(
        type: TransactionType,
        from: LocalDate,
        to: LocalDate
    ): Flow<List<CategoryTotal>> = dao.getCategoryTotals(type, from, to)

    fun observeMonthlySummaries(): Flow<List<MonthlySummary>> = dao.observeMonthlySummaries()

    suspend fun save(transaction: Transaction): Long = withContext(Dispatchers.IO) {
        val entity = transaction.toEntity()
        val id = if (entity.id == 0L) dao.insert(entity) else {
            dao.update(entity)
            entity.id
        }
        dao.deleteAllLabelsForTransaction(id)
        transaction.labels.forEach { label ->
            dao.insertCrossRef(TransactionLabelCrossRef(id, label.id))
        }
        id
    }

    suspend fun delete(transaction: Transaction) = withContext(Dispatchers.IO) {
        dao.delete(transaction.toEntity())
    }

    suspend fun deleteScheduledForRule(ruleId: Long) = withContext(Dispatchers.IO) {
        dao.deleteScheduledForRule(ruleId)
    }

    suspend fun countRealTransactionsForRule(ruleId: Long): Int = withContext(Dispatchers.IO) {
        dao.countRealTransactionsForRule(ruleId)
    }

    suspend fun getLatestForRule(ruleId: Long): Transaction? = withContext(Dispatchers.IO) {
        dao.getLatestForRule(ruleId)?.toDomain()
    }
}
