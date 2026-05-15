package com.atlasfpt.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.atlasfpt.data.db.entity.FxRateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FxRateDao {

    @Query("SELECT * FROM fx_rates ORDER BY currencyCode ASC")
    fun observeAll(): Flow<List<FxRateEntity>>

    @Query("SELECT * FROM fx_rates WHERE currencyCode = :code LIMIT 1")
    suspend fun getByCode(code: String): FxRateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rates: List<FxRateEntity>)
}
