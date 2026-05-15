package com.atlasfpt.data.repository

import androidx.room.withTransaction
import com.atlasfpt.data.db.AppDatabase
import com.atlasfpt.data.db.dao.AssetDao
import com.atlasfpt.data.db.dao.FinancialDao
import com.atlasfpt.data.db.entity.AssetEntity
import com.atlasfpt.data.db.entity.FinancialHoldingEntity
import com.atlasfpt.data.db.entity.toFinancialDomain
import com.atlasfpt.data.db.entity.toEntity
import com.atlasfpt.domain.model.AssetType
import com.atlasfpt.domain.model.FinancialAsset
import com.atlasfpt.domain.model.FinancialLot
import com.atlasfpt.domain.model.LotType
import com.atlasfpt.domain.model.TickerQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinancialRepository @Inject constructor(
    private val db: AppDatabase,
    private val assetDao: AssetDao,
    private val financialDao: FinancialDao,
) {

    suspend fun getById(id: Long): FinancialAsset? = withContext(Dispatchers.IO) {
        financialDao.getWithDetails(id)?.toFinancialDomain()
    }

    suspend fun isTickerAlreadyTracked(ticker: String): Boolean = withContext(Dispatchers.IO) {
        financialDao.countByTicker(ticker) > 0
    }

    /**
     * Creates a new financial asset (parent + holding + first lot) in one transaction.
     * Sets `assets.current_value` to `quote.price * lot.quantity` and `assets.purchase_date`
     * to the lot's date.
     */
    suspend fun createAssetWithFirstLot(
        name: String,
        notes: String?,
        quote: TickerQuote,
        firstLot: FinancialLot,
    ): Long = withContext(Dispatchers.IO) {
        if (financialDao.countByTicker(quote.ticker) > 0) {
            throw IllegalStateException("Asset for ${quote.ticker} already exists")
        }
        val nowMillis = Instant.now().toEpochMilli()
        db.withTransaction {
            val parentId = assetDao.insert(
                AssetEntity(
                    id = 0,
                    type = AssetType.FINANCIAL,
                    name = name,
                    currencyCode = quote.currencyCode,
                    currentValue = quote.price * firstLot.quantity,
                    currentValueUpdatedAt = nowMillis,
                    purchaseDate = firstLot.purchaseDate,
                    notes = notes,
                )
            )
            financialDao.insertHolding(
                FinancialHoldingEntity(
                    assetId = parentId,
                    ticker = quote.ticker,
                    displayName = quote.displayName,
                    latestPrice = quote.price,
                    latestPriceAt = nowMillis,
                )
            )
            financialDao.insertLot(firstLot.toEntity(parentId))
            parentId
        }
    }

    /**
     * Adds a lot to an existing financial asset, then recomputes the parent's current_value
     * and purchase_date in the same transaction.
     */
    suspend fun addLot(assetId: Long, lot: FinancialLot): Long = withContext(Dispatchers.IO) {
        db.withTransaction {
            val parent = assetDao.getById(assetId)
                ?: throw IllegalStateException("Asset $assetId no longer exists")
            val lotId = financialDao.insertLot(lot.toEntity(assetId))
            validateRunningNetInTxn(assetId)
            recomputeAssetSnapshotInTxn(assetId)
            lotId
        }
    }

    /** Update an existing lot, then recompute parent snapshot. */
    suspend fun updateLot(assetId: Long, lot: FinancialLot) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val existing = financialDao.getLot(lot.id)
                ?: throw IllegalStateException("Lot ${lot.id} no longer exists")
            require(existing.assetId == assetId) { "Lot ${lot.id} does not belong to asset $assetId" }
            financialDao.updateLot(lot.toEntity(assetId))
            validateRunningNetInTxn(assetId)
            recomputeAssetSnapshotInTxn(assetId)
        }
    }

    /**
     * Walks the asset's lots in (date, id) order and confirms the running net quantity
     * never goes negative. Throws [IllegalArgumentException] otherwise — the surrounding
     * `withTransaction` rolls back, so the offending row never commits.
     */
    private suspend fun validateRunningNetInTxn(assetId: Long) {
        var running = 0.0
        var firstBadDate: java.time.LocalDate? = null
        for (entity in financialDao.getAllLotsForAsset(assetId)) {
            running += if (entity.lotType == LotType.BUY) entity.quantity else -entity.quantity
            if (running < -1e-9 && firstBadDate == null) firstBadDate = entity.purchaseDate
        }
        if (firstBadDate != null) {
            throw IllegalArgumentException(
                "Sale exceeds shares held on $firstBadDate"
            )
        }
    }

    /**
     * Deletes a lot. If it was the last lot, the asset is also deleted (cascades to holding).
     * Returns true if the entire asset was deleted as a result.
     */
    suspend fun deleteLot(assetId: Long, lotId: Long): Boolean = withContext(Dispatchers.IO) {
        db.withTransaction {
            val lot = financialDao.getLot(lotId) ?: return@withTransaction false
            financialDao.deleteLot(lot)
            val remaining = financialDao.countLots(assetId)
            if (remaining == 0) {
                assetDao.deleteById(assetId) // cascades to holding
                true
            } else {
                recomputeAssetSnapshotInTxn(assetId)
                false
            }
        }
    }

    /**
     * Called from PriceRepository after a successful Yahoo fetch. Updates the holding's
     * latest price + recomputes the parent's current_value, all in one transaction.
     */
    suspend fun applyPriceUpdate(assetId: Long, newPrice: Double) = withContext(Dispatchers.IO) {
        val now = Instant.now().toEpochMilli()
        db.withTransaction {
            financialDao.updateLatestPrice(assetId, newPrice, now)
            val totalQty = financialDao.sumLotQuantity(assetId)
            assetDao.updateCurrentValue(assetId, newPrice * totalQty, now)
        }
    }

    /** Helper: must be called inside a withTransaction block. */
    private suspend fun recomputeAssetSnapshotInTxn(assetId: Long) {
        val totalQty = financialDao.sumLotQuantity(assetId)
        val price = financialDao.getHolding(assetId)?.latestPrice ?: 0.0
        val now = Instant.now().toEpochMilli()
        assetDao.updateCurrentValue(assetId, price * totalQty, now)
        financialDao.earliestLotDate(assetId)?.let { dateStr ->
            assetDao.updatePurchaseDate(assetId, LocalDate.parse(dateStr))
        }
    }
}
