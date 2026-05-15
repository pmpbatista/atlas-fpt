package com.atlasfpt.domain.model

import java.time.LocalDate

data class FinancialLot(
    val id: Long,
    val purchaseDate: LocalDate,
    val quantity: Double,
    val pricePerUnit: Double,
    val type: LotType = LotType.BUY,
)
