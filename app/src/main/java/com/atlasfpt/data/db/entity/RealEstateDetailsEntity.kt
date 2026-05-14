package com.atlasfpt.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.atlasfpt.domain.model.EnergyRating
import com.atlasfpt.domain.model.InterestType
import com.atlasfpt.domain.model.ReferenceRate
import java.time.LocalDate

@Entity(
    tableName = "real_estate_details",
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
data class RealEstateDetailsEntity(
    @PrimaryKey val assetId: Long,
    val cost: Double,
    val investedCapital: Double,
    val debtAmount: Double?,
    val outstandingDebt: Double?,
    val interestType: InterestType?,
    val fixedRate: Double?,
    val referenceRate: ReferenceRate?,
    val spread: Double?,
    val creditEndDate: LocalDate?,
    val district: String,
    val council: String,
    val parish: String,
    val sizeM2: Double,
    val energyRating: EnergyRating,
)
