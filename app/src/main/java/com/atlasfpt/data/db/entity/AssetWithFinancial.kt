package com.atlasfpt.data.db.entity

import androidx.room.Embedded
import androidx.room.Relation

data class AssetWithFinancial(
    @Embedded val asset: AssetEntity,
    @Relation(parentColumn = "id", entityColumn = "assetId")
    val holding: FinancialHoldingEntity?,
    @Relation(parentColumn = "id", entityColumn = "assetId")
    val lots: List<FinancialLotEntity>,
)
