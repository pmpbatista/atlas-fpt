package com.atlasfpt.domain.model

import java.time.LocalDate

data class Dividend(
    val id: Long,
    val payDate: LocalDate,
    val grossAmount: Double,
    val note: String? = null,
)
