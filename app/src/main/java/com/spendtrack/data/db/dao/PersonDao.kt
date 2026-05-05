package com.spendtrack.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.spendtrack.data.db.entity.PersonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {

    @Query("SELECT * FROM persons ORDER BY name ASC")
    fun observeAll(): Flow<List<PersonEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(person: PersonEntity): Long

    @Delete
    suspend fun delete(person: PersonEntity)

    @Query("SELECT COUNT(*) FROM transaction_person_cross_ref WHERE personId = :personId")
    suspend fun countTransactions(personId: Long): Int
}
