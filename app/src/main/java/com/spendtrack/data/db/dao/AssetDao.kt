package com.spendtrack.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.spendtrack.data.db.entity.AssetEntity
import com.spendtrack.domain.model.AssetListItem
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {

    @Query("""
        SELECT a.id AS id,
               a.type AS type,
               a.name AS name,
               a.currentValue AS currentValue,
               a.currencyCode AS currencyCode,
               r.outstandingDebt AS outstandingDebt
        FROM assets a
        LEFT JOIN real_estate_details r ON r.assetId = a.id
        ORDER BY LOWER(a.name) ASC
    """)
    fun observeAssetList(): Flow<List<AssetListItem>>

    @Query("SELECT * FROM assets WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AssetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: AssetEntity): Long

    @Update
    suspend fun update(asset: AssetEntity)

    @Delete
    suspend fun delete(asset: AssetEntity)

    @Query("DELETE FROM assets WHERE id = :id")
    suspend fun deleteById(id: Long)
}
