package com.spendtrack.data.repository

import com.spendtrack.data.db.dao.RecurringRuleDao
import com.spendtrack.data.db.entity.toDomain
import com.spendtrack.data.db.entity.toEntity
import com.spendtrack.domain.model.RecurringRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecurringRuleRepository @Inject constructor(private val dao: RecurringRuleDao) {

    fun observeAll(): Flow<List<RecurringRule>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getDueRules(date: LocalDate): List<RecurringRule> = withContext(Dispatchers.IO) {
        dao.getDueRules(date).map { it.toDomain() }
    }

    suspend fun save(rule: RecurringRule): Long = withContext(Dispatchers.IO) {
        dao.insert(rule.toEntity())
    }

    suspend fun update(rule: RecurringRule) = withContext(Dispatchers.IO) {
        dao.update(rule.toEntity())
    }

    suspend fun delete(rule: RecurringRule) = withContext(Dispatchers.IO) {
        dao.delete(rule.toEntity())
    }

    suspend fun getById(id: Long): RecurringRule? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    suspend fun deleteById(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }
}
