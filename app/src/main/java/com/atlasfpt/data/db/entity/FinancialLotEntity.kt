package com.atlasfpt.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "financial_lots",
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("assetId")]
)
data class FinancialLotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: Long,
    val purchaseDate: LocalDate,
    val quantity: Double,
    val pricePerUnit: Double,
)
