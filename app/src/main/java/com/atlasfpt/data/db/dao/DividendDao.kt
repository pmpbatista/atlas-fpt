package com.atlasfpt.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.atlasfpt.data.db.entity.DividendEntity

@Dao
interface DividendDao {

    @Query("SELECT * FROM dividends WHERE assetId = :assetId ORDER BY payDate ASC, id ASC")
    suspend fun listForAsset(assetId: Long): List<DividendEntity>

    @Query("SELECT * FROM dividends WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DividendEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dividend: DividendEntity): Long

    @Update
    suspend fun update(dividend: DividendEntity)

    @Delete
    suspend fun delete(dividend: DividendEntity)
}
