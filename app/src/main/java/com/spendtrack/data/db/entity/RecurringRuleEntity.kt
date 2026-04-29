package com.spendtrack.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.spendtrack.domain.model.RecurringFrequency
import java.time.LocalDate

@Entity(tableName = "recurring_rules")
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val frequency: RecurringFrequency,
    val interval: Int = 1,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val nextTriggerDate: LocalDate
)
