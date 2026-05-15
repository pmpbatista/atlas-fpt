package com.atlasfpt.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atlasfpt.data.db.entity.AssetEntity
import com.atlasfpt.data.db.entity.FinancialHoldingEntity
import com.atlasfpt.data.db.entity.FinancialLotEntity
import com.atlasfpt.data.db.entity.RealEstateDetailsEntity
import com.atlasfpt.domain.model.AssetType
import com.atlasfpt.domain.model.EnergyRating
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AssetCascadeTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun deletingFinancialAssetCascadesHoldingAndLots() = runTest {
        val assetId = insertFinancialAsset()
        db.financialDao().insertHolding(
            FinancialHoldingEntity(
                assetId = assetId,
                ticker = "VWCE.DE",
                displayName = "Vanguard FTSE All-World",
                latestPrice = 100.0,
                latestPriceAt = 0L,
            )
        )
        db.financialDao().insertLot(
            FinancialLotEntity(
                assetId = assetId,
                purchaseDate = LocalDate.of(2026, 1, 10),
                quantity = 5.0,
                pricePerUnit = 95.0,
            )
        )
        db.financialDao().insertLot(
            FinancialLotEntity(
                assetId = assetId,
                purchaseDate = LocalDate.of(2026, 2, 10),
                quantity = 3.0,
                pricePerUnit = 98.0,
            )
        )
        assertNotNull(db.financialDao().getHolding(assetId))
        assertEquals(2, db.financialDao().countLots(assetId))

        db.assetDao().deleteById(assetId)

        assertNull(
            "FK CASCADE should remove the holding when the asset is deleted",
            db.financialDao().getHolding(assetId),
        )
        assertEquals(
            "FK CASCADE should remove all lots when the asset is deleted",
            0,
            db.financialDao().countLots(assetId),
        )
    }

    @Test
    fun deletingRealEstateAssetCascadesDetails() = runTest {
        val assetId = insertRealEstateAsset()
        db.realEstateDao().insertDetails(
            RealEstateDetailsEntity(
                assetId = assetId,
                cost = 250_000.0,
                investedCapital = 50_000.0,
                debtAmount = 200_000.0,
                outstandingDebt = 180_000.0,
                interestType = null,
                fixedRate = null,
                referenceRate = null,
                spread = null,
                creditEndDate = null,
                district = "Lisboa",
                council = "Lisboa",
                parish = "Estrela",
                sizeM2 = 90.0,
                energyRating = EnergyRating.B,
            )
        )
        assertNotNull(db.realEstateDao().getWithDetails(assetId)?.details)

        db.assetDao().deleteById(assetId)

        assertNull(
            "FK CASCADE should remove real_estate_details when the asset is deleted",
            db.realEstateDao().getWithDetails(assetId),
        )
    }

    private suspend fun insertFinancialAsset(): Long = db.assetDao().insert(
        AssetEntity(
            type = AssetType.FINANCIAL,
            name = "VWCE",
            currencyCode = "EUR",
            currentValue = 0.0,
            currentValueUpdatedAt = 0L,
            purchaseDate = LocalDate.of(2026, 1, 10),
            notes = null,
        )
    )

    private suspend fun insertRealEstateAsset(): Long = db.assetDao().insert(
        AssetEntity(
            type = AssetType.REAL_ESTATE,
            name = "Lisbon flat",
            currencyCode = "EUR",
            currentValue = 250_000.0,
            currentValueUpdatedAt = 0L,
            purchaseDate = LocalDate.of(2024, 6, 1),
            notes = null,
        )
    )
}
