package com.spendtrack.data.db

import androidx.room.TypeConverter
import com.spendtrack.domain.model.AssetType
import com.spendtrack.domain.model.CategoryType
import com.spendtrack.domain.model.EnergyRating
import com.spendtrack.domain.model.InterestType
import com.spendtrack.domain.model.RecurringFrequency
import com.spendtrack.domain.model.ReferenceRate
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

    @TypeConverter
    fun fromAssetType(value: String?): AssetType? = value?.let { AssetType.valueOf(it) }

    @TypeConverter
    fun toAssetType(type: AssetType?): String? = type?.name

    @TypeConverter
    fun fromInterestType(value: String?): InterestType? = value?.let { InterestType.valueOf(it) }

    @TypeConverter
    fun toInterestType(type: InterestType?): String? = type?.name

    @TypeConverter
    fun fromReferenceRate(value: String?): ReferenceRate? = value?.let { ReferenceRate.valueOf(it) }

    @TypeConverter
    fun toReferenceRate(rate: ReferenceRate?): String? = rate?.name

    @TypeConverter
    fun fromEnergyRating(value: String?): EnergyRating? = value?.let { EnergyRating.valueOf(it) }

    @TypeConverter
    fun toEnergyRating(rating: EnergyRating?): String? = rating?.name
}
