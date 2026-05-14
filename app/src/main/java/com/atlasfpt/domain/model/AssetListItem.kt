package com.atlasfpt.domain.model

data class AssetListItem(
    val id: Long,
    val type: AssetType,
    val name: String,
    val currentValue: Double,
    val currencyCode: String,
    val outstandingDebt: Double?,
) {
    val equity: Double get() = currentValue - (outstandingDebt ?: 0.0)
}
