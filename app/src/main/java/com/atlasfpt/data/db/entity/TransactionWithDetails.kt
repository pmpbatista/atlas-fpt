package com.atlasfpt.data.db.entity

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
    val labels: List<LabelEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TransactionPersonCrossRef::class,
            parentColumn = "transactionId",
            entityColumn = "personId"
        )
    )
    val persons: List<PersonEntity>
)
