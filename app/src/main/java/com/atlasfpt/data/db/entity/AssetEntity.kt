package com.atlasfpt.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.atlasfpt.domain.model.AssetType
import java.time.LocalDate

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: AssetType,
    val name: String,
    val currencyCode: String,
    val currentValue: Double,
    val currentValueUpdatedAt: Long,
    val purchaseDate: LocalDate?,
    val notes: String?,
)
