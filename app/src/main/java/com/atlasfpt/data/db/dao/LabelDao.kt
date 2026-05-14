package com.atlasfpt.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.atlasfpt.data.db.entity.LabelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelDao {

    @Query("SELECT * FROM labels ORDER BY name ASC")
    fun observeAll(): Flow<List<LabelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(label: LabelEntity): Long

    @Delete
    suspend fun delete(label: LabelEntity)
}
