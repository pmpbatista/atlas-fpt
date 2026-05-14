package com.atlasfpt.data.db.entity

import androidx.room.Embedded
import androidx.room.Relation

data class AssetWithRealEstate(
    @Embedded val asset: AssetEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "assetId"
    )
    val details: RealEstateDetailsEntity?
)
