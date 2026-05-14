package com.atlasfpt.data.db.entity

import com.atlasfpt.domain.model.AssetType
import com.atlasfpt.domain.model.Category
import com.atlasfpt.domain.model.FinancialAsset
import com.atlasfpt.domain.model.FinancialLot
import com.atlasfpt.domain.model.Label
import com.atlasfpt.domain.model.Person
import com.atlasfpt.domain.model.RealEstateAsset
import com.atlasfpt.domain.model.RecurringRule
import com.atlasfpt.domain.model.Transaction
import java.time.Instant

fun TransactionWithDetails.toDomain(): Transaction = Transaction(
    id = transaction.id,
    amount = transaction.amount,
    type = transaction.type,
    category = category.toDomain(),
    date = transaction.date,
    note = transaction.note,
    photoUri = transaction.photoUri,
    labels = labels.map { it.toDomain() },
    persons = persons.map { it.toDomain() },
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

fun PersonEntity.toDomain(): Person = Person(id = id, name = name)

fun Person.toEntity(): PersonEntity = PersonEntity(id = id, name = name)

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

fun AssetWithRealEstate.toRealEstateDomain(): RealEstateAsset {
    val d = requireNotNull(details) { "real_estate_details missing for asset ${asset.id}" }
    require(asset.type == AssetType.REAL_ESTATE) {
        "asset ${asset.id} is ${asset.type}, not REAL_ESTATE"
    }
    return RealEstateAsset(
        id = asset.id,
        name = asset.name,
        currencyCode = asset.currencyCode,
        currentValue = asset.currentValue,
        currentValueUpdatedAt = Instant.ofEpochMilli(asset.currentValueUpdatedAt),
        purchaseDate = requireNotNull(asset.purchaseDate) {
            "purchaseDate is required for real estate"
        },
        notes = asset.notes,
        cost = d.cost,
        investedCapital = d.investedCapital,
        debtAmount = d.debtAmount,
        outstandingDebt = d.outstandingDebt,
        interestType = d.interestType,
        fixedRate = d.fixedRate,
        referenceRate = d.referenceRate,
        spread = d.spread,
        creditEndDate = d.creditEndDate,
        district = d.district,
        council = d.council,
        parish = d.parish,
        sizeM2 = d.sizeM2,
        energyRating = d.energyRating
    )
}

fun RealEstateAsset.toAssetEntity(currentValueUpdatedAtMillis: Long): AssetEntity = AssetEntity(
    id = id,
    type = AssetType.REAL_ESTATE,
    name = name,
    currencyCode = currencyCode,
    currentValue = currentValue,
    currentValueUpdatedAt = currentValueUpdatedAtMillis,
    purchaseDate = purchaseDate,
    notes = notes
)

fun RealEstateAsset.toDetailsEntity(assetId: Long): RealEstateDetailsEntity =
    RealEstateDetailsEntity(
        assetId = assetId,
        cost = cost,
        investedCapital = investedCapital,
        debtAmount = debtAmount,
        outstandingDebt = outstandingDebt,
        interestType = interestType,
        fixedRate = fixedRate,
        referenceRate = referenceRate,
        spread = spread,
        creditEndDate = creditEndDate,
        district = district,
        council = council,
        parish = parish,
        sizeM2 = sizeM2,
        energyRating = energyRating
    )

fun FinancialLotEntity.toDomain(): FinancialLot = FinancialLot(
    id = id,
    purchaseDate = purchaseDate,
    quantity = quantity,
    pricePerUnit = pricePerUnit,
)

fun FinancialLot.toEntity(assetId: Long): FinancialLotEntity = FinancialLotEntity(
    id = id,
    assetId = assetId,
    purchaseDate = purchaseDate,
    quantity = quantity,
    pricePerUnit = pricePerUnit,
)

fun AssetWithFinancial.toFinancialDomain(): FinancialAsset {
    val h = requireNotNull(holding) { "financial_holdings missing for asset ${asset.id}" }
    require(asset.type == AssetType.FINANCIAL) {
        "asset ${asset.id} is ${asset.type}, not FINANCIAL"
    }
    return FinancialAsset(
        id = asset.id,
        name = asset.name,
        ticker = h.ticker,
        displayName = h.displayName,
        currencyCode = asset.currencyCode,
        latestPrice = h.latestPrice,
        latestPriceAt = h.latestPriceAt?.let { Instant.ofEpochMilli(it) },
        notes = asset.notes,
        lots = lots.sortedBy { it.purchaseDate }.map { it.toDomain() },
    )
}
