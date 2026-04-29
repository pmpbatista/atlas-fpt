package com.spendtrack.data.db

import androidx.room.TypeConverter
import com.spendtrack.domain.model.CategoryType
import com.spendtrack.domain.model.RecurringFrequency
import com.spendtrack.domain.model.TransactionType
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter
    fun toLocalDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun fromTransactionType(value: String?): TransactionType? =
        value?.let { TransactionType.valueOf(it) }

    @TypeConverter
    fun toTransactionType(type: TransactionType?): String? = type?.name

    @TypeConverter
    fun fromCategoryType(value: String?): CategoryType? =
        value?.let { CategoryType.valueOf(it) }

    @TypeConverter
    fun toCategoryType(type: CategoryType?): String? = type?.name

    @TypeConverter
    fun fromRecurringFrequency(value: String?): RecurringFrequency? =
        value?.let { RecurringFrequency.valueOf(it) }

    @TypeConverter
    fun toRecurringFrequency(frequency: RecurringFrequency?): String? = frequency?.name
}
