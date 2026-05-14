package com.atlasfpt.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "transaction_label_cross_ref",
    primaryKeys = ["transactionId", "labelId"],
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LabelEntity::class,
            parentColumns = ["id"],
            childColumns = ["labelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("labelId")]
)
data class TransactionLabelCrossRef(
    val transactionId: Long,
    val labelId: Long
)
