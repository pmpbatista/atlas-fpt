package com.spendtrack.data.db.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class TransactionWithDetails(
    @Embedded val transaction: TransactionEntity,
    @Relation(
        parentColumn = "categoryId",
        entityColumn = "id"
    )
    val category: CategoryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TransactionLabelCrossRef::class,
            parentColumn = "transactionId",
            entityColumn = "labelId"
        )
    )
    val labels: List<LabelEntity>
)
