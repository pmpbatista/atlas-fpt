package com.atlasfpt.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fx_rates")
data class FxRateEntity(
    @PrimaryKey val currencyCode: String,
    val unitsPerEur: Double,
    val fetchedAt: Long,
)
