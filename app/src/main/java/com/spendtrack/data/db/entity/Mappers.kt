package com.spendtrack.data.db.entity

import com.spendtrack.domain.model.Category
import com.spendtrack.domain.model.Label
import com.spendtrack.domain.model.RecurringRule
import com.spendtrack.domain.model.Transaction

fun TransactionWithDetails.toDomain(): Transaction = Transaction(
    id = transaction.id,
    amount = transaction.amount,
    type = transaction.type,
    category = category.toDomain(),
    date = transaction.date,
    note = transaction.note,
    photoUri = transaction.photoUri,
    labels = labels.map { it.toDomain() },
    recurringRuleId = transaction.recurringRuleId,
    isScheduled = transaction.isScheduled
)

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    iconRes = iconRes,
    color = color,
    type = type
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    iconRes = iconRes,
    color = color,
    type = type
)

fun LabelEntity.toDomain(): Label = Label(id = id, name = name)

fun Label.toEntity(): LabelEntity = LabelEntity(id = id, name = name)

fun RecurringRuleEntity.toDomain(): RecurringRule = RecurringRule(
    id = id,
    frequency = frequency,
    interval = interval,
    startDate = startDate,
    endDate = endDate,
    nextTriggerDate = nextTriggerDate
)

fun RecurringRule.toEntity(): RecurringRuleEntity = RecurringRuleEntity(
    id = id,
    frequency = frequency,
    interval = interval,
    startDate = startDate,
    endDate = endDate,
    nextTriggerDate = nextTriggerDate
)

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    amount = amount,
    type = type,
    categoryId = category.id,
    date = date,
    note = note,
    photoUri = photoUri,
    recurringRuleId = recurringRuleId,
    isScheduled = isScheduled
)
