package com.atlasfpt.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.atlasfpt.domain.model.CategoryType

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconRes: String,
    val color: Int,
    val type: CategoryType
)
