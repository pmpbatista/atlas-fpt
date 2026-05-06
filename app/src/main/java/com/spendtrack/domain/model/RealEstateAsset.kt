package com.spendtrack.domain.model

import java.time.Instant
import java.time.LocalDate

data class RealEstateAsset(
    val id: Long,
    val name: String,
    val currencyCode: String,
    val currentValue: Double,
    val currentValueUpdatedAt: Instant,
    val purchaseDate: LocalDate,
    val notes: String?,
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
