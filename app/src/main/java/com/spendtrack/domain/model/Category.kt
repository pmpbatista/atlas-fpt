package com.spendtrack.domain.model

data class Category(
    val id: Long,
    val name: String,
    val iconRes: String,
    val color: Int,
    val type: CategoryType
)
