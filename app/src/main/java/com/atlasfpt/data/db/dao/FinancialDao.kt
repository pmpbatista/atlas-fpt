package com.atlasfpt.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.atlasfpt.data.db.entity.AssetWithFinancial
import com.atlasfpt.data.db.entity.FinancialHoldingEntity
import com.atlasfpt.data.db.entity.FinancialLotEntity

@Dao
interface FinancialDao {

    @Transaction
    @Query("SELECT * FROM assets WHERE id = :id LIMIT 1")
    suspend fun getWithDetails(id: Long): AssetWithFinancial?

    @Query("SELECT assetId, ticker FROM financial_holdings")
    suspend fun getAllTickers(): List<TickerRow>

    @Query("SELECT * FROM financial_holdings WHERE assetId = :assetId LIMIT 1")
    suspend fun getHolding(assetId: Long): FinancialHoldingEntity?

    @Query("SELECT COUNT(*) FROM financial_holdings WHERE ticker = :ticker LIMIT 1")
    suspend fun countByTicker(ticker: String): Int

    @Query("SELECT COALESCE(SUM(quantity), 0.0) FROM financial_lots WHERE assetId = :assetId")
    suspend fun sumLotQuantity(assetId: Long): Double

    @Query("SELECT MIN(purchaseDate) FROM financial_lots WHERE assetId = :assetId")
    suspend fun earliestLotDate(assetId: Long): String?

    @Query("SELECT * FROM financial_lots WHERE id = :lotId LIMIT 1")
    suspend fun getLot(lotId: Long): FinancialLotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHolding(holding: FinancialHoldingEntity)

    @Update
    suspend fun updateHolding(holding: FinancialHoldingEntity)

    @Query("UPDATE financial_holdings SET latestPrice = :price, latestPriceAt = :at WHERE assetId = :assetId")
    suspend fun updateLatestPrice(assetId: Long, price: Double, at: Long)

    @Insert
    suspend fun insertLot(lot: FinancialLotEntity): Long

    @Update
    suspend fun updateLot(lot: FinancialLotEntity)

    @Delete
    suspend fun deleteLot(lot: FinancialLotEntity)

    @Query("SELECT COUNT(*) FROM financial_lots WHERE assetId = :assetId")
    suspend fun countLots(assetId: Long): Int
}

data class TickerRow(val assetId: Long, val ticker: String)
