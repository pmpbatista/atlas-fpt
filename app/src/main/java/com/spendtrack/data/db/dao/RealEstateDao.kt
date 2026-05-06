package com.spendtrack.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.spendtrack.data.db.entity.AssetWithRealEstate
import com.spendtrack.data.db.entity.RealEstateDetailsEntity

@Dao
interface RealEstateDao {

    @Transaction
    @Query("SELECT * FROM assets WHERE id = :id LIMIT 1")
    suspend fun getWithDetails(id: Long): AssetWithRealEstate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetails(details: RealEstateDetailsEntity)

    @Update
    suspend fun updateDetails(details: RealEstateDetailsEntity)
}
