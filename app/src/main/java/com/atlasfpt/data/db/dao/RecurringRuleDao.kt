package com.atlasfpt.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.atlasfpt.data.db.entity.RecurringRuleEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface RecurringRuleDao {

    @Query("SELECT * FROM recurring_rules ORDER BY nextTriggerDate ASC")
    fun observeAll(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE nextTriggerDate <= :date")
    suspend fun getDueRules(date: LocalDate): List<RecurringRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: RecurringRuleEntity): Long

    @Update
    suspend fun update(rule: RecurringRuleEntity)

    @Delete
    suspend fun delete(rule: RecurringRuleEntity)

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun getById(id: Long): RecurringRuleEntity?

    @Query("DELETE FROM recurring_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}
