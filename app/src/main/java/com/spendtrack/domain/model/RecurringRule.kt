package com.spendtrack.domain.model

import java.time.LocalDate

data class RecurringRule(
    val id: Long,
    val frequency: RecurringFrequency,
    val interval: Int,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val nextTriggerDate: LocalDate
)
