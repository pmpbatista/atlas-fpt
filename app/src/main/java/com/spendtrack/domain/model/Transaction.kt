package com.spendtrack.domain.model

import java.time.LocalDate

data class Transaction(
    val id: Long,
    val amount: Double,
    val type: TransactionType,
    val category: Category,
    val date: LocalDate,
    val note: String?,
    val photoUri: String?,
    val labels: List<Label>,
    val persons: List<Person>,
    val recurringRuleId: Long?,
    val isScheduled: Boolean
)
