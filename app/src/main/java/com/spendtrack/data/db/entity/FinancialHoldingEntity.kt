package com.spendtrack.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "financial_holdings",
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FinancialHoldingEntity(
    @PrimaryKey val assetId: Long,
    val ticker: String,
    val displayName: String,
    val latestPrice: Double?,
    val latestPriceAt: Long?,
)
