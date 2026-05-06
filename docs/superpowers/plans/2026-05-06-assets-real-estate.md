# Assets — Foundation & Real Estate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the assets feature shell (bottom-nav slot replacing Activity, list with totals header, type picker) and the real-estate asset type end-to-end (create / edit / delete / read-only detail), so the user can record properties with cost, capital, debt, location, and current valuation, and see net wealth aggregated across them.

**Architecture:** Parent `assets` Room table + per-type `real_estate_details` (1:1, ON DELETE CASCADE), mirroring the existing transactions/categories pattern. Per-type isolated domain models. Shared assets list uses a lightweight `AssetListItem` projection from a `LEFT JOIN`. Detail and edit are separate screens for typed assets.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose + Material 3, Room 2.6.1, Hilt 2.51.1, Navigation Compose 2.8.3. Tests: JUnit4 + MockK + kotlinx-coroutines-test + Turbine.

**Spec:** [`docs/superpowers/specs/2026-05-06-assets-real-estate-design.md`](../specs/2026-05-06-assets-real-estate-design.md)

**Pre-flight check (run once before starting):**

```bash
./gradlew testDebugUnitTest
```

Expected: existing tests pass. If they don't, fix them before starting (a pre-existing red build will obscure plan-level errors).

---

## Task 1: Domain enums

**Files:**
- Create: `app/src/main/java/com/spendtrack/domain/model/AssetType.kt`
- Create: `app/src/main/java/com/spendtrack/domain/model/InterestType.kt`
- Create: `app/src/main/java/com/spendtrack/domain/model/ReferenceRate.kt`
- Create: `app/src/main/java/com/spendtrack/domain/model/EnergyRating.kt`

- [ ] **Step 1: Create `AssetType`**

```kotlin
package com.spendtrack.domain.model

enum class AssetType { REAL_ESTATE, FINANCIAL }
```

- [ ] **Step 2: Create `InterestType`**

```kotlin
package com.spendtrack.domain.model

enum class InterestType { FIXED, VARIABLE }
```

- [ ] **Step 3: Create `ReferenceRate`**

```kotlin
package com.spendtrack.domain.model

enum class ReferenceRate(val label: String) {
    EURIBOR_1M("Euribor 1M"),
    EURIBOR_3M("Euribor 3M"),
    EURIBOR_6M("Euribor 6M"),
    EURIBOR_12M("Euribor 12M"),
}
```

- [ ] **Step 4: Create `EnergyRating`**

```kotlin
package com.spendtrack.domain.model

enum class EnergyRating(val label: String) {
    A_PLUS("A+"),
    A("A"),
    B("B"),
    B_MINUS("B-"),
    C("C"),
    D("D"),
    E("E"),
    F("F"),
}
```

- [ ] **Step 5: Build to verify compilation**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/spendtrack/domain/model/AssetType.kt \
        app/src/main/java/com/spendtrack/domain/model/InterestType.kt \
        app/src/main/java/com/spendtrack/domain/model/ReferenceRate.kt \
        app/src/main/java/com/spendtrack/domain/model/EnergyRating.kt
git commit -m "feat(assets): add asset type and real-estate enums"
```

---

## Task 2: Domain models

**Files:**
- Create: `app/src/main/java/com/spendtrack/domain/model/AssetListItem.kt`
- Create: `app/src/main/java/com/spendtrack/domain/model/TotalWealth.kt`
- Create: `app/src/main/java/com/spendtrack/domain/model/RealEstateAsset.kt`

- [ ] **Step 1: Create `AssetListItem`**

```kotlin
package com.spendtrack.domain.model

data class AssetListItem(
    val id: Long,
    val type: AssetType,
    val name: String,
    val currentValue: Double,
    val currencyCode: String,
    val outstandingDebt: Double?,
) {
    val equity: Double get() = currentValue - (outstandingDebt ?: 0.0)
}
```

- [ ] **Step 2: Create `TotalWealth`**

```kotlin
package com.spendtrack.domain.model

data class TotalWealth(
    val byCurrency: Map<String, Double>,
    val assetCount: Int,
) {
    val isMixedCurrency: Boolean get() = byCurrency.size > 1
    val isEmpty: Boolean get() = byCurrency.isEmpty()
}
```

- [ ] **Step 3: Create `RealEstateAsset`**

```kotlin
package com.spendtrack.domain.model

import java.time.Instant
import java.time.LocalDate

data class RealEstateAsset(
    val id: Long,
    val name: String,
    val currencyCode: String,
    val currentValue: Double,
    val currentValueUpdatedAt: Instant,
    val purchaseDate: LocalDate,
    val notes: String?,
    val cost: Double,
    val investedCapital: Double,
    val debtAmount: Double?,
    val outstandingDebt: Double?,
    val interestType: InterestType?,
    val fixedRate: Double?,
    val referenceRate: ReferenceRate?,
    val spread: Double?,
    val creditEndDate: LocalDate?,
    val district: String,
    val council: String,
    val parish: String,
    val sizeM2: Double,
    val energyRating: EnergyRating,
)
```

- [ ] **Step 4: Build**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/spendtrack/domain/model/AssetListItem.kt \
        app/src/main/java/com/spendtrack/domain/model/TotalWealth.kt \
        app/src/main/java/com/spendtrack/domain/model/RealEstateAsset.kt
git commit -m "feat(assets): add asset list, total wealth, and real-estate domain models"
```

---

## Task 3: Add enum type converters

**Files:**
- Modify: `app/src/main/java/com/spendtrack/data/db/Converters.kt`

The new entities will store `AssetType`, `InterestType`, `ReferenceRate`, and `EnergyRating` as TEXT. Add converters following the existing pattern.

- [ ] **Step 1: Replace `Converters.kt` contents**

```kotlin
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
```

- [ ] **Step 2: Build**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/spendtrack/data/db/Converters.kt
git commit -m "feat(assets): add type converters for asset enums"
```

---

## Task 4: Asset entities

**Files:**
- Create: `app/src/main/java/com/spendtrack/data/db/entity/AssetEntity.kt`
- Create: `app/src/main/java/com/spendtrack/data/db/entity/RealEstateDetailsEntity.kt`
- Create: `app/src/main/java/com/spendtrack/data/db/entity/AssetWithRealEstate.kt`

- [ ] **Step 1: Create `AssetEntity`**

```kotlin
package com.spendtrack.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.spendtrack.domain.model.AssetType
import java.time.LocalDate

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: AssetType,
    val name: String,
    val currencyCode: String,
    val currentValue: Double,
    val currentValueUpdatedAt: Long,
    val purchaseDate: LocalDate?,
    val notes: String?,
)
```

- [ ] **Step 2: Create `RealEstateDetailsEntity`**

```kotlin
package com.spendtrack.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.spendtrack.domain.model.EnergyRating
import com.spendtrack.domain.model.InterestType
import com.spendtrack.domain.model.ReferenceRate
import java.time.LocalDate

@Entity(
    tableName = "real_estate_details",
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("assetId")]
)
data class RealEstateDetailsEntity(
    @PrimaryKey val assetId: Long,
    val cost: Double,
    val investedCapital: Double,
    val debtAmount: Double?,
    val outstandingDebt: Double?,
    val interestType: InterestType?,
    val fixedRate: Double?,
    val referenceRate: ReferenceRate?,
    val spread: Double?,
    val creditEndDate: LocalDate?,
    val district: String,
    val council: String,
    val parish: String,
    val sizeM2: Double,
    val energyRating: EnergyRating,
)
```

- [ ] **Step 3: Create `AssetWithRealEstate` (`@Relation` wrapper)**

```kotlin
package com.spendtrack.data.db.entity

import androidx.room.Embedded
import androidx.room.Relation

data class AssetWithRealEstate(
    @Embedded val asset: AssetEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "assetId"
    )
    val details: RealEstateDetailsEntity?
)
```

- [ ] **Step 4: Build**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL` (entities not yet registered, so no Room codegen yet — that comes in Task 6)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/spendtrack/data/db/entity/AssetEntity.kt \
        app/src/main/java/com/spendtrack/data/db/entity/RealEstateDetailsEntity.kt \
        app/src/main/java/com/spendtrack/data/db/entity/AssetWithRealEstate.kt
git commit -m "feat(assets): add Room entities and @Relation wrapper for assets"
```

---

## Task 5: Entity ↔ domain mappers

**Files:**
- Modify: `app/src/main/java/com/spendtrack/data/db/entity/Mappers.kt`

Add mappers between the new entities and the domain models. Follow the file's existing top-level extension-function style.

- [ ] **Step 1: Append mappers to `Mappers.kt`**

Open `app/src/main/java/com/spendtrack/data/db/entity/Mappers.kt`. Add these imports at the top (alphabetized with the existing imports):

```kotlin
import com.spendtrack.domain.model.AssetType
import com.spendtrack.domain.model.RealEstateAsset
import java.time.Instant
```

Append the following functions to the end of the file:

```kotlin
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
```

- [ ] **Step 2: Build**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/spendtrack/data/db/entity/Mappers.kt
git commit -m "feat(assets): add entity-to-domain mappers"
```

---

## Task 6: DAOs

**Files:**
- Create: `app/src/main/java/com/spendtrack/data/db/dao/AssetDao.kt`
- Create: `app/src/main/java/com/spendtrack/data/db/dao/RealEstateDao.kt`

The list query returns the projection directly (Room maps columns to `AssetListItem` constructor). The detail query uses `@Transaction` + `@Relation` wrapper.

- [ ] **Step 1: Create `AssetDao`**

```kotlin
package com.spendtrack.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.spendtrack.data.db.entity.AssetEntity
import com.spendtrack.domain.model.AssetListItem
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {

    @Query("""
        SELECT a.id AS id,
               a.type AS type,
               a.name AS name,
               a.currentValue AS currentValue,
               a.currencyCode AS currencyCode,
               r.outstandingDebt AS outstandingDebt
        FROM assets a
        LEFT JOIN real_estate_details r ON r.assetId = a.id
        ORDER BY LOWER(a.name) ASC
    """)
    fun observeAssetList(): Flow<List<AssetListItem>>

    @Query("SELECT * FROM assets WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AssetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: AssetEntity): Long

    @Update
    suspend fun update(asset: AssetEntity)

    @Delete
    suspend fun delete(asset: AssetEntity)

    @Query("DELETE FROM assets WHERE id = :id")
    suspend fun deleteById(id: Long)
}
```

- [ ] **Step 2: Create `RealEstateDao`**

```kotlin
package com.spendtrack.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.spendtrack.data.db.entity.AssetWithRealEstate
import com.spendtrack.data.db.entity.RealEstateDetailsEntity

@Dao
interface RealEstateDao {

    @Transaction
    @Query("SELECT * FROM assets WHERE id = :id LIMIT 1")
    suspend fun getWithDetails(id: Long): AssetWithRealEstate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetails(details: RealEstateDetailsEntity)

    @Update
    suspend fun updateDetails(details: RealEstateDetailsEntity)
}
```

- [ ] **Step 3: Register entities and DAOs on `AppDatabase`**

Modify `app/src/main/java/com/spendtrack/data/db/AppDatabase.kt`:

Add these imports near the existing entity imports:

```kotlin
import com.spendtrack.data.db.dao.AssetDao
import com.spendtrack.data.db.dao.RealEstateDao
import com.spendtrack.data.db.entity.AssetEntity
import com.spendtrack.data.db.entity.RealEstateDetailsEntity
```

Update the `@Database` annotation: add the two new entities to the `entities` list, and bump `version = 1` to `version = 2`. The annotation becomes:

```kotlin
@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        LabelEntity::class,
        TransactionLabelCrossRef::class,
        RecurringRuleEntity::class,
        AssetEntity::class,
        RealEstateDetailsEntity::class,
    ],
    version = 2,
    exportSchema = false
)
```

Add abstract DAO methods inside the `AppDatabase` class body (just after `abstract fun recurringRuleDao(): RecurringRuleDao`):

```kotlin
    abstract fun assetDao(): AssetDao
    abstract fun realEstateDao(): RealEstateDao
```

Update the `create` companion to register the migration. Replace the existing `create` body with:

```kotlin
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "spendtrack.db")
                .addCallback(SeedCallback())
                .addMigrations(MIGRATION_1_2)
                .build()
```

Add this top-level migration definition above the `private class SeedCallback` declaration (i.e., still inside `AppDatabase.kt` but outside the class). The SQL must be **copied verbatim from Room's generated `AppDatabase_Impl.createAllTables`** — Room's runtime identity-hash check rejects any syntactic deviation, even when the resulting schema is functionally equivalent. After running an initial build, look up the strings under `app/build/generated/ksp/debug/java/com/spendtrack/data/db/AppDatabase_Impl.java`, find `createAllTables`, and copy the relevant `db.execSQL(...)` lines for the new tables and indices into the migration verbatim. Notably: the parent table uses inline `INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL`, but the child detail table uses a separate `PRIMARY KEY(\`assetId\`)` clause at the end of the column list (NOT inline) — Room will reject the inline form even though it produces an identical schema.

```kotlin
import androidx.room.migration.Migration

// SQL strings copied verbatim from Room's generated AppDatabase_Impl.createAllTables.
// Room's runtime identity-hash check rejects any deviation (e.g. inline `INTEGER PRIMARY KEY`
// vs trailing `PRIMARY KEY(col)`) — keep these in sync if entities change.
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `assets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `name` TEXT NOT NULL, `currencyCode` TEXT NOT NULL, `currentValue` REAL NOT NULL, `currentValueUpdatedAt` INTEGER NOT NULL, `purchaseDate` TEXT, `notes` TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `real_estate_details` (`assetId` INTEGER NOT NULL, `cost` REAL NOT NULL, `investedCapital` REAL NOT NULL, `debtAmount` REAL, `outstandingDebt` REAL, `interestType` TEXT, `fixedRate` REAL, `referenceRate` TEXT, `spread` REAL, `creditEndDate` TEXT, `district` TEXT NOT NULL, `council` TEXT NOT NULL, `parish` TEXT NOT NULL, `sizeM2` REAL NOT NULL, `energyRating` TEXT NOT NULL, PRIMARY KEY(`assetId`), FOREIGN KEY(`assetId`) REFERENCES `assets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_real_estate_details_assetId` ON `real_estate_details` (`assetId`)")
    }
}
```

Note: column names in the migration SQL match the Kotlin property names exactly (camelCase: `currencyCode`, `currentValueUpdatedAt`, `outstandingDebt`, `assetId`, `sizeM2`, etc.) because Room uses property names as column names by default and the entities don't override with `@ColumnInfo`.

- [ ] **Step 4: Build to run Room codegen**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. If Room reports a schema-mismatch error, the migration SQL doesn't match what Room expects from the entities — fix column types/nullability/index names against the error message before continuing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/spendtrack/data/db/dao/AssetDao.kt \
        app/src/main/java/com/spendtrack/data/db/dao/RealEstateDao.kt \
        app/src/main/java/com/spendtrack/data/db/AppDatabase.kt
git commit -m "feat(assets): add asset DAOs and Room migration 1->2"
```

---

## Task 7: Repositories

**Files:**
- Create: `app/src/main/java/com/spendtrack/data/repository/AssetRepository.kt`
- Create: `app/src/main/java/com/spendtrack/data/repository/RealEstateRepository.kt`

The real-estate repository performs save (insert or update) inside a Room `withTransaction` block, comparing the existing `currentValue` to bump `currentValueUpdatedAt` only when the valuation changes.

- [ ] **Step 1: Create `AssetRepository`**

```kotlin
package com.spendtrack.data.repository

import com.spendtrack.data.db.dao.AssetDao
import com.spendtrack.domain.model.AssetListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetRepository @Inject constructor(private val dao: AssetDao) {

    fun observeAssetList(): Flow<List<AssetListItem>> = dao.observeAssetList()

    suspend fun deleteById(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }
}
```

- [ ] **Step 2: Create `RealEstateRepository`**

```kotlin
package com.spendtrack.data.repository

import androidx.room.withTransaction
import com.spendtrack.data.db.AppDatabase
import com.spendtrack.data.db.dao.AssetDao
import com.spendtrack.data.db.dao.RealEstateDao
import com.spendtrack.data.db.entity.toAssetEntity
import com.spendtrack.data.db.entity.toDetailsEntity
import com.spendtrack.data.db.entity.toRealEstateDomain
import com.spendtrack.domain.model.RealEstateAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealEstateRepository @Inject constructor(
    private val db: AppDatabase,
    private val assetDao: AssetDao,
    private val realEstateDao: RealEstateDao,
) {

    suspend fun getById(id: Long): RealEstateAsset? = withContext(Dispatchers.IO) {
        realEstateDao.getWithDetails(id)?.toRealEstateDomain()
    }

    /**
     * Inserts (when [asset].id == 0L) or updates the parent + details rows in a single
     * Room transaction. Bumps `currentValueUpdatedAt` to "now" on insert, or on update only
     * when `currentValue` changed compared to the stored row.
     */
    suspend fun save(asset: RealEstateAsset): Long = withContext(Dispatchers.IO) {
        db.withTransaction {
            val nowMillis = Instant.now().toEpochMilli()
            if (asset.id == 0L) {
                val parentId = assetDao.insert(asset.toAssetEntity(nowMillis))
                realEstateDao.insertDetails(asset.toDetailsEntity(parentId))
                parentId
            } else {
                val existing = assetDao.getById(asset.id)
                    ?: throw IllegalStateException("Asset ${asset.id} no longer exists")
                val updatedAt =
                    if (existing.currentValue != asset.currentValue) nowMillis
                    else existing.currentValueUpdatedAt
                assetDao.update(asset.toAssetEntity(updatedAt))
                realEstateDao.updateDetails(asset.toDetailsEntity(asset.id))
                asset.id
            }
        }
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/spendtrack/data/repository/AssetRepository.kt \
        app/src/main/java/com/spendtrack/data/repository/RealEstateRepository.kt
git commit -m "feat(assets): add asset and real-estate repositories"
```

---

## Task 8: Hilt DI updates

**Files:**
- Modify: `app/src/main/java/com/spendtrack/di/DatabaseModule.kt`

Repositories don't need explicit module entries (the existing pattern uses `@Inject constructor` with `@Singleton`, resolved automatically by Hilt). Only DAOs need new `@Provides` methods.

- [ ] **Step 1: Add DAO providers to `DatabaseModule`**

Open `app/src/main/java/com/spendtrack/di/DatabaseModule.kt`. Add imports:

```kotlin
import com.spendtrack.data.db.dao.AssetDao
import com.spendtrack.data.db.dao.RealEstateDao
```

Add these `@Provides` methods after the existing `provideRecurringRuleDao`:

```kotlin
    @Provides
    fun provideAssetDao(db: AppDatabase): AssetDao = db.assetDao()

    @Provides
    fun provideRealEstateDao(db: AppDatabase): RealEstateDao = db.realEstateDao()
```

- [ ] **Step 2: Build (this also runs Hilt codegen)**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. If Hilt complains about a missing binding for `AppDatabase` when constructing `RealEstateRepository`, double-check `DatabaseModule.provideDatabase` exists (it should — pre-existing).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/spendtrack/di/DatabaseModule.kt
git commit -m "feat(assets): wire asset DAOs into Hilt"
```

---

## Task 9: CurrencyFormatter overload + pure helpers

**Files:**
- Modify: `app/src/main/java/com/spendtrack/util/CurrencyFormatter.kt`
- Create: `app/src/main/java/com/spendtrack/util/AssetFormatting.kt`
- Create: `app/src/test/java/com/spendtrack/util/AssetFormattingTest.kt`

The existing `CurrencyFormatter.formatAbsolute(amount, symbol)` only knows the user's app-wide symbol. We need to render an asset's value in *its own* currency. Add an overload that resolves the symbol from a 3-letter ISO code via `java.util.Currency`.

`AssetFormatting.kt` houses small helpers (`parseDecimal`, `relativeTimeString`, `monthsRemaining`, `describeInterest`) in one file because each is too small to warrant its own. Tests cover the testable ones.

- [ ] **Step 1: Add `formatAbsoluteForCurrency` overload**

Replace `app/src/main/java/com/spendtrack/util/CurrencyFormatter.kt` contents:

```kotlin
package com.spendtrack.util

import com.spendtrack.domain.model.TransactionType
import java.util.Currency
import java.util.Locale

object CurrencyFormatter {

    fun format(amount: Double, symbol: String, type: TransactionType): String {
        val formatted = formatAmount(amount)
        return if (type == TransactionType.EXPENSE) "-$symbol$formatted" else "$symbol$formatted"
    }

    fun formatAbsolute(amount: Double, symbol: String): String = "$symbol${formatAmount(amount)}"

    /**
     * Formats [amount] with the symbol resolved from a 3-letter ISO 4217 [currencyCode]
     * (e.g. "USD" -> "$", "EUR" -> "€"). Falls back to the code itself when JVM/Android
     * can't resolve the symbol on the device locale.
     */
    fun formatAbsoluteForCurrency(amount: Double, currencyCode: String): String {
        val symbol = runCatching {
            Currency.getInstance(currencyCode).getSymbol(Locale.getDefault())
        }.getOrDefault(currencyCode)
        return "$symbol${formatAmount(amount)}"
    }

    private fun formatAmount(amount: Double): String {
        return if (kotlin.math.abs(amount) >= 1000) {
            // Portuguese thousands: 1.471,00
            val s = String.format(Locale.US, "%,.2f", amount)
            s.replace(",", "X").replace(".", ",").replace("X", ".")
        } else {
            String.format(Locale.US, "%.2f", amount).replace(".", ",")
        }
    }
}
```

(The existing `formatAmount` was unsafe with negative >= 1000 — using `Math.abs` ensures the Portuguese thousand grouping renders for "underwater" net wealth like `-€1.234,56`. The Locale-explicit `String.format` keeps the format stable regardless of device locale.)

- [ ] **Step 2: Create `AssetFormatting.kt`**

```kotlin
package com.spendtrack.util

import com.spendtrack.domain.model.InterestType
import com.spendtrack.domain.model.RealEstateAsset
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Parses a decimal text input. Accepts both "." and "," as decimal separator
 * (Portuguese locale uses ","). Trims whitespace. Returns null on any other failure
 * — never throws into UI.
 */
fun parseDecimal(input: String): Double? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    return trimmed.replace(',', '.').toDoubleOrNull()
}

/**
 * Bucketed relative time: today / yesterday / N days / N months / over a year.
 */
fun relativeTimeString(epochMillis: Long, now: Instant = Instant.now()): String {
    val then = Instant.ofEpochMilli(epochMillis)
    val days = ChronoUnit.DAYS.between(then.atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
        now.atZone(java.time.ZoneId.systemDefault()).toLocalDate())
    return when {
        days <= 0L -> "today"
        days == 1L -> "yesterday"
        days < 30L -> "$days days ago"
        days < 365L -> {
            val months = (days / 30L).toInt()
            if (months == 1) "1 month ago" else "$months months ago"
        }
        else -> "over a year ago"
    }
}

/**
 * Human-readable remaining time on a credit. "Credit ended" when [endDate] is in the past
 * or today.
 */
fun monthsRemaining(endDate: LocalDate, today: LocalDate = LocalDate.now()): String {
    val months = ChronoUnit.MONTHS.between(today.withDayOfMonth(1), endDate.withDayOfMonth(1))
    return when {
        months <= 0L -> "Credit ended"
        months <= 12L -> if (months == 1L) "1 month remaining" else "$months months remaining"
        else -> {
            val years = months / 12L
            val rem = months % 12L
            if (rem == 0L) "$years years remaining"
            else "$years years $rem months remaining"
        }
    }
}

/**
 * Formats a percent with two decimals: 0.5 -> "0,50%", -0.2 -> "-0,20%".
 */
fun formatPercent(value: Double): String {
    val s = String.format(java.util.Locale.US, "%.2f", value).replace(".", ",")
    return "$s%"
}

private fun signedPercent(value: Double): String {
    return when {
        value > 0 -> "+ ${formatPercent(value)}"
        value < 0 -> "− ${formatPercent(-value)}"
        else -> formatPercent(0.0)
    }
}

fun describeInterest(asset: RealEstateAsset): String = when (asset.interestType) {
    null -> "Bought outright"
    InterestType.FIXED -> "Fixed ${formatPercent(asset.fixedRate ?: 0.0)}"
    InterestType.VARIABLE -> {
        val ref = asset.referenceRate?.label ?: "Variable"
        val spread = asset.spread ?: 0.0
        "$ref ${signedPercent(spread)}"
    }
}
```

- [ ] **Step 3: Write the failing tests**

```kotlin
package com.spendtrack.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class AssetFormattingTest {

    @Test
    fun `parseDecimal accepts dot decimal separator`() {
        assertEquals(1234.56, parseDecimal("1234.56")!!, 0.0001)
    }

    @Test
    fun `parseDecimal accepts comma decimal separator`() {
        assertEquals(1234.56, parseDecimal("1234,56")!!, 0.0001)
    }

    @Test
    fun `parseDecimal trims whitespace`() {
        assertEquals(42.0, parseDecimal("  42 ")!!, 0.0001)
    }

    @Test
    fun `parseDecimal returns null for empty string`() {
        assertNull(parseDecimal(""))
    }

    @Test
    fun `parseDecimal returns null for non-numeric`() {
        assertNull(parseDecimal("abc"))
    }

    @Test
    fun `parseDecimal accepts negative values`() {
        assertEquals(-0.5, parseDecimal("-0,5")!!, 0.0001)
    }

    @Test
    fun `relativeTimeString returns today for now`() {
        val now = Instant.parse("2026-05-06T12:00:00Z")
        val result = relativeTimeString(now.toEpochMilli(), now)
        assertEquals("today", result)
    }

    @Test
    fun `relativeTimeString returns yesterday for 1 day ago`() {
        // Both instants are at midnight UTC; the local-date diff is timezone-stable
        // because they're exactly 24h apart.
        val now = LocalDate.of(2026, 5, 6).atStartOfDay(ZoneOffset.UTC).toInstant()
        val past = LocalDate.of(2026, 5, 5).atStartOfDay(ZoneOffset.UTC).toInstant()
        assertEquals("yesterday", relativeTimeString(past.toEpochMilli(), now))
    }

    @Test
    fun `relativeTimeString returns months ago for 90 days`() {
        val now = LocalDate.of(2026, 5, 6).atStartOfDay(ZoneOffset.UTC).toInstant()
        val past = LocalDate.of(2026, 2, 5).atStartOfDay(ZoneOffset.UTC).toInstant()
        val result = relativeTimeString(past.toEpochMilli(), now)
        // 90 days / 30 = 3 months
        assertEquals("3 months ago", result)
    }

    @Test
    fun `relativeTimeString returns over a year ago for 400 days`() {
        val now = LocalDate.of(2026, 5, 6).atStartOfDay(ZoneOffset.UTC).toInstant()
        val past = LocalDate.of(2025, 4, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val result = relativeTimeString(past.toEpochMilli(), now)
        assertEquals("over a year ago", result)
    }

    @Test
    fun `monthsRemaining returns credit ended when past`() {
        val today = LocalDate.of(2026, 5, 6)
        val end = LocalDate.of(2025, 1, 1)
        assertEquals("Credit ended", monthsRemaining(end, today))
    }

    @Test
    fun `monthsRemaining returns months for under a year`() {
        val today = LocalDate.of(2026, 5, 6)
        val end = LocalDate.of(2026, 11, 6)
        assertEquals("6 months remaining", monthsRemaining(end, today))
    }

    @Test
    fun `monthsRemaining returns years and months for over a year`() {
        val today = LocalDate.of(2026, 5, 6)
        val end = LocalDate.of(2030, 8, 6)
        assertEquals("4 years 3 months remaining", monthsRemaining(end, today))
    }

    @Test
    fun `monthsRemaining returns whole years when no remainder`() {
        val today = LocalDate.of(2026, 5, 6)
        val end = LocalDate.of(2030, 5, 6)
        assertEquals("4 years remaining", monthsRemaining(end, today))
    }

    @Test
    fun `formatPercent uses comma decimal separator`() {
        assertEquals("3,20%", formatPercent(3.2))
        assertEquals("-0,50%", formatPercent(-0.5))
    }
}
```

- [ ] **Step 4: Run tests, verify they pass**

Run: `./gradlew testDebugUnitTest --tests com.spendtrack.util.AssetFormattingTest`
Expected: all assertions pass.

If `relativeTimeString returns yesterday for 1 day ago` is flaky due to timezone, replace its body with a direct equality on the function's computed days bucket using a fixed `now` and `past` that are exactly 86400000 millis apart and rely on the function's day-based math.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/spendtrack/util/CurrencyFormatter.kt \
        app/src/main/java/com/spendtrack/util/AssetFormatting.kt \
        app/src/test/java/com/spendtrack/util/AssetFormattingTest.kt
git commit -m "feat(assets): add per-currency formatter and asset formatting helpers"
```

---

## Task 10: GetTotalWealthUseCase (TDD)

**Files:**
- Create: `app/src/test/java/com/spendtrack/domain/usecase/GetTotalWealthUseCaseTest.kt`
- Create: `app/src/main/java/com/spendtrack/domain/usecase/GetTotalWealthUseCase.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.spendtrack.domain.usecase

import app.cash.turbine.test
import com.spendtrack.data.repository.AssetRepository
import com.spendtrack.domain.model.AssetListItem
import com.spendtrack.domain.model.AssetType
import com.spendtrack.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GetTotalWealthUseCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo: AssetRepository = mockk()

    private fun item(
        id: Long,
        currentValue: Double,
        currencyCode: String = "EUR",
        outstandingDebt: Double? = null
    ) = AssetListItem(
        id = id,
        type = AssetType.REAL_ESTATE,
        name = "asset $id",
        currentValue = currentValue,
        currencyCode = currencyCode,
        outstandingDebt = outstandingDebt
    )

    private fun useCase(items: List<AssetListItem>) = GetTotalWealthUseCase(repo).also {
        every { repo.observeAssetList() } returns flowOf(items)
    }

    @Test
    fun `empty list yields empty total`() = runTest {
        useCase(emptyList())().test {
            val w = awaitItem()
            assertTrue(w.isEmpty)
            assertEquals(0, w.assetCount)
            awaitComplete()
        }
    }

    @Test
    fun `single EUR no debt sums to current value`() = runTest {
        useCase(listOf(item(1, 100_000.0)))().test {
            val w = awaitItem()
            assertEquals(100_000.0, w.byCurrency["EUR"]!!, 0.0001)
            assertEquals(1, w.assetCount)
            assertFalse(w.isMixedCurrency)
            awaitComplete()
        }
    }

    @Test
    fun `single EUR with debt subtracts outstanding`() = runTest {
        useCase(listOf(item(1, 300_000.0, outstandingDebt = 100_000.0)))().test {
            val w = awaitItem()
            assertEquals(200_000.0, w.byCurrency["EUR"]!!, 0.0001)
            awaitComplete()
        }
    }

    @Test
    fun `multiple EUR assets sum equity`() = runTest {
        useCase(listOf(
            item(1, 300_000.0, outstandingDebt = 100_000.0),
            item(2, 50_000.0)
        ))().test {
            val w = awaitItem()
            assertEquals(250_000.0, w.byCurrency["EUR"]!!, 0.0001)
            assertEquals(2, w.assetCount)
            awaitComplete()
        }
    }

    @Test
    fun `mixed EUR and USD produces two entries`() = runTest {
        useCase(listOf(
            item(1, 100_000.0, currencyCode = "EUR"),
            item(2, 50_000.0, currencyCode = "USD")
        ))().test {
            val w = awaitItem()
            assertEquals(100_000.0, w.byCurrency["EUR"]!!, 0.0001)
            assertEquals(50_000.0, w.byCurrency["USD"]!!, 0.0001)
            assertTrue(w.isMixedCurrency)
            assertEquals(2, w.assetCount)
            awaitComplete()
        }
    }

    @Test
    fun `null outstanding debt treated as zero`() = runTest {
        useCase(listOf(item(1, 200_000.0, outstandingDebt = null)))().test {
            val w = awaitItem()
            assertEquals(200_000.0, w.byCurrency["EUR"]!!, 0.0001)
            awaitComplete()
        }
    }

    @Test
    fun `underwater asset produces negative entry`() = runTest {
        useCase(listOf(item(1, 100_000.0, outstandingDebt = 150_000.0)))().test {
            val w = awaitItem()
            assertEquals(-50_000.0, w.byCurrency["EUR"]!!, 0.0001)
            awaitComplete()
        }
    }
}
```

- [ ] **Step 2: Run test, verify it fails to compile**

Run: `./gradlew testDebugUnitTest --tests com.spendtrack.domain.usecase.GetTotalWealthUseCaseTest`
Expected: compile error — `Unresolved reference: GetTotalWealthUseCase`

- [ ] **Step 3: Implement the use case**

```kotlin
package com.spendtrack.domain.usecase

import com.spendtrack.data.repository.AssetRepository
import com.spendtrack.domain.model.TotalWealth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetTotalWealthUseCase @Inject constructor(
    private val repo: AssetRepository,
) {
    operator fun invoke(): Flow<TotalWealth> = repo.observeAssetList().map { items ->
        val byCurrency = items.groupBy { it.currencyCode }
            .mapValues { (_, list) -> list.sumOf { it.equity } }
        TotalWealth(byCurrency = byCurrency, assetCount = items.size)
    }
}
```

- [ ] **Step 4: Run tests, verify all pass**

Run: `./gradlew testDebugUnitTest --tests com.spendtrack.domain.usecase.GetTotalWealthUseCaseTest`
Expected: 7 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/spendtrack/domain/usecase/GetTotalWealthUseCaseTest.kt \
        app/src/main/java/com/spendtrack/domain/usecase/GetTotalWealthUseCase.kt
git commit -m "feat(assets): add GetTotalWealthUseCase with per-currency aggregation"
```

---

## Task 11: Remaining use cases

**Files:**
- Create: `app/src/main/java/com/spendtrack/domain/usecase/GetAssetsListUseCase.kt`
- Create: `app/src/main/java/com/spendtrack/domain/usecase/GetRealEstateUseCase.kt`
- Create: `app/src/main/java/com/spendtrack/domain/usecase/SaveRealEstateUseCase.kt`
- Create: `app/src/main/java/com/spendtrack/domain/usecase/DeleteAssetUseCase.kt`

These are all thin wrappers — no separate tests; they're exercised through ViewModel tests.

- [ ] **Step 1: Create `GetAssetsListUseCase`**

```kotlin
package com.spendtrack.domain.usecase

import com.spendtrack.data.repository.AssetRepository
import com.spendtrack.domain.model.AssetListItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAssetsListUseCase @Inject constructor(
    private val repo: AssetRepository,
) {
    operator fun invoke(): Flow<List<AssetListItem>> = repo.observeAssetList()
}
```

- [ ] **Step 2: Create `GetRealEstateUseCase`**

```kotlin
package com.spendtrack.domain.usecase

import com.spendtrack.data.repository.RealEstateRepository
import com.spendtrack.domain.model.RealEstateAsset
import javax.inject.Inject

class GetRealEstateUseCase @Inject constructor(
    private val repo: RealEstateRepository,
) {
    suspend operator fun invoke(id: Long): RealEstateAsset? = repo.getById(id)
}
```

- [ ] **Step 3: Create `SaveRealEstateUseCase`**

```kotlin
package com.spendtrack.domain.usecase

import com.spendtrack.data.repository.RealEstateRepository
import com.spendtrack.domain.model.RealEstateAsset
import javax.inject.Inject

class SaveRealEstateUseCase @Inject constructor(
    private val repo: RealEstateRepository,
) {
    suspend operator fun invoke(asset: RealEstateAsset): Long = repo.save(asset)
}
```

- [ ] **Step 4: Create `DeleteAssetUseCase`**

```kotlin
package com.spendtrack.domain.usecase

import com.spendtrack.data.repository.AssetRepository
import javax.inject.Inject

class DeleteAssetUseCase @Inject constructor(
    private val repo: AssetRepository,
) {
    suspend operator fun invoke(id: Long) = repo.deleteById(id)
}
```

- [ ] **Step 5: Build**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/spendtrack/domain/usecase/GetAssetsListUseCase.kt \
        app/src/main/java/com/spendtrack/domain/usecase/GetRealEstateUseCase.kt \
        app/src/main/java/com/spendtrack/domain/usecase/SaveRealEstateUseCase.kt \
        app/src/main/java/com/spendtrack/domain/usecase/DeleteAssetUseCase.kt
git commit -m "feat(assets): add list, get, save, delete use cases"
```

---

## Task 12: AssetsListViewModel (TDD)

**Files:**
- Create: `app/src/test/java/com/spendtrack/ui/feature/assets/list/AssetsListViewModelTest.kt`
- Create: `app/src/main/java/com/spendtrack/ui/feature/assets/list/AssetsListViewModel.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.spendtrack.ui.feature.assets.list

import app.cash.turbine.test
import com.spendtrack.domain.model.AssetListItem
import com.spendtrack.domain.model.AssetType
import com.spendtrack.domain.model.TotalWealth
import com.spendtrack.domain.usecase.GetAssetsListUseCase
import com.spendtrack.domain.usecase.GetTotalWealthUseCase
import com.spendtrack.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AssetsListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getList: GetAssetsListUseCase = mockk()
    private val getTotal: GetTotalWealthUseCase = mockk()

    private fun item(id: Long, currency: String = "EUR") = AssetListItem(
        id = id,
        type = AssetType.REAL_ESTATE,
        name = "asset $id",
        currentValue = 100_000.0,
        currencyCode = currency,
        outstandingDebt = null
    )

    @Test
    fun `empty list shows empty state`() = runTest {
        every { getList() } returns flowOf(emptyList())
        every { getTotal() } returns flowOf(TotalWealth(emptyMap(), 0))
        val vm = AssetsListViewModel(getList, getTotal)

        vm.uiState.test {
            val s = awaitItem()
            assertTrue(s.isEmpty)
            assertTrue(s.items.isEmpty())
            assertTrue(s.total.isEmpty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `populated list maps items 1 to 1`() = runTest {
        val items = listOf(item(1), item(2))
        every { getList() } returns flowOf(items)
        every { getTotal() } returns flowOf(TotalWealth(mapOf("EUR" to 200_000.0), 2))
        val vm = AssetsListViewModel(getList, getTotal)

        vm.uiState.test {
            // first emission may be initial empty, then loaded
            var s = awaitItem()
            while (s.items.size < 2) s = awaitItem()
            assertEquals(2, s.items.size)
            assertFalse(s.isEmpty)
            assertEquals(200_000.0, s.total.byCurrency["EUR"]!!, 0.0001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `mixed currencies flagged on total`() = runTest {
        val items = listOf(item(1, "EUR"), item(2, "USD"))
        every { getList() } returns flowOf(items)
        every { getTotal() } returns flowOf(
            TotalWealth(mapOf("EUR" to 100_000.0, "USD" to 50_000.0), 2)
        )
        val vm = AssetsListViewModel(getList, getTotal)

        vm.uiState.test {
            var s = awaitItem()
            while (!s.total.isMixedCurrency) s = awaitItem()
            assertTrue(s.total.isMixedCurrency)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run test, verify compile failure**

Run: `./gradlew testDebugUnitTest --tests com.spendtrack.ui.feature.assets.list.AssetsListViewModelTest`
Expected: `Unresolved reference: AssetsListViewModel`

- [ ] **Step 3: Implement `AssetsListViewModel`**

```kotlin
package com.spendtrack.ui.feature.assets.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtrack.domain.model.AssetListItem
import com.spendtrack.domain.model.TotalWealth
import com.spendtrack.domain.usecase.GetAssetsListUseCase
import com.spendtrack.domain.usecase.GetTotalWealthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AssetsListUiState(
    val items: List<AssetListItem> = emptyList(),
    val total: TotalWealth = TotalWealth(emptyMap(), 0),
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

@HiltViewModel
class AssetsListViewModel @Inject constructor(
    getList: GetAssetsListUseCase,
    getTotal: GetTotalWealthUseCase,
) : ViewModel() {

    val uiState: StateFlow<AssetsListUiState> = combine(
        getList(),
        getTotal()
    ) { items, total ->
        AssetsListUiState(items = items, total = total)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AssetsListUiState()
    )
}
```

- [ ] **Step 4: Run tests, verify all pass**

Run: `./gradlew testDebugUnitTest --tests com.spendtrack.ui.feature.assets.list.AssetsListViewModelTest`
Expected: 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/spendtrack/ui/feature/assets/list/AssetsListViewModelTest.kt \
        app/src/main/java/com/spendtrack/ui/feature/assets/list/AssetsListViewModel.kt
git commit -m "feat(assets): add AssetsListViewModel"
```

---

## Task 13: List screen + components

**Files:**
- Create: `app/src/main/java/com/spendtrack/ui/feature/assets/component/TotalWealthHeader.kt`
- Create: `app/src/main/java/com/spendtrack/ui/feature/assets/component/AssetListRow.kt`
- Create: `app/src/main/java/com/spendtrack/ui/feature/assets/list/AssetsListScreen.kt`

- [ ] **Step 1: Create `TotalWealthHeader`**

```kotlin
package com.spendtrack.ui.feature.assets.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spendtrack.domain.model.TotalWealth
import com.spendtrack.util.CurrencyFormatter

@Composable
fun TotalWealthHeader(total: TotalWealth, modifier: Modifier = Modifier) {
    if (total.isEmpty) return
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Net wealth", style = MaterialTheme.typography.labelMedium)
        total.byCurrency.forEach { (code, value) ->
            Text(
                CurrencyFormatter.formatAbsoluteForCurrency(value, code),
                style = MaterialTheme.typography.headlineMedium
            )
        }
        val subtitle = if (total.isMixedCurrency)
            "Across ${total.assetCount} assets · mixed currencies"
        else
            "Across ${total.assetCount} assets"
        Text(subtitle, style = MaterialTheme.typography.bodySmall)
    }
}
```

- [ ] **Step 2: Create `AssetListRow`**

```kotlin
package com.spendtrack.ui.feature.assets.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spendtrack.domain.model.AssetListItem
import com.spendtrack.domain.model.AssetType
import com.spendtrack.util.CurrencyFormatter

@Composable
fun AssetListRow(item: AssetListItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (item.type) {
                AssetType.REAL_ESTATE -> Icons.Filled.HomeWork
                AssetType.FINANCIAL -> Icons.Filled.HomeWork // placeholder until financial spec
            },
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            Text(
                CurrencyFormatter.formatAbsoluteForCurrency(item.equity, item.currencyCode),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            "Value " + CurrencyFormatter.formatAbsoluteForCurrency(item.currentValue, item.currencyCode),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

- [ ] **Step 3: Create `AssetsListScreen`**

```kotlin
package com.spendtrack.ui.feature.assets.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.spendtrack.domain.model.AssetType
import com.spendtrack.ui.feature.assets.component.AssetListRow
import com.spendtrack.ui.feature.assets.component.TotalWealthHeader
import com.spendtrack.ui.feature.assets.typepicker.AssetTypePickerSheet
import com.spendtrack.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsListScreen(
    navController: NavController,
    viewModel: AssetsListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showTypePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Assets") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showTypePicker = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add asset")
            }
        }
    ) { padding ->
        if (state.isEmpty) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No assets yet\nTap + to add one",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                item { TotalWealthHeader(total = state.total) }
                items(state.items, key = { it.id }) { asset ->
                    AssetListRow(
                        item = asset,
                        onClick = {
                            when (asset.type) {
                                AssetType.REAL_ESTATE ->
                                    navController.navigate(Screen.RealEstateDetail.createRoute(asset.id))
                                AssetType.FINANCIAL -> Unit // not navigable yet
                            }
                        }
                    )
                }
            }
        }
    }

    if (showTypePicker) {
        AssetTypePickerSheet(
            onDismiss = { showTypePicker = false },
            onRealEstate = {
                showTypePicker = false
                navController.navigate(Screen.AddRealEstate.route)
            }
        )
    }
}
```

- [ ] **Step 4: Build (will fail until type picker + navigation routes exist — that's OK; commit comes after Task 14 and 15)**

Skip the build for now. The screen references `AssetTypePickerSheet` and `Screen.RealEstateDetail` / `Screen.AddRealEstate` which haven't been created yet. We'll build at the end of Task 15.

- [ ] **Step 5: (deferred — combined commit at end of Task 15)**

---

## Task 14: AssetTypePickerSheet

**Files:**
- Create: `app/src/main/java/com/spendtrack/ui/feature/assets/typepicker/AssetTypePickerSheet.kt`

- [ ] **Step 1: Create the sheet**

```kotlin
package com.spendtrack.ui.feature.assets.typepicker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetTypePickerSheet(
    onDismiss: () -> Unit,
    onRealEstate: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                "Add asset",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            )
            ListItem(
                headlineContent = { Text("Real Estate") },
                leadingContent = { Icon(Icons.Filled.HomeWork, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onRealEstate)
            )
            ListItem(
                headlineContent = {
                    Text(
                        "Financial",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                supportingContent = { Text("Coming soon") },
                leadingContent = {
                    Icon(
                        Icons.Filled.ShowChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // not clickable: disabled in this spec
            )
        }
    }
}
```

- [ ] **Step 2: (build deferred — together with subsequent tasks)**

---

## Task 15: RealEstateDetailViewModel + screen + Navigation

**Files:**
- Create: `app/src/test/java/com/spendtrack/ui/feature/assets/realestate/detail/RealEstateDetailViewModelTest.kt`
- Create: `app/src/main/java/com/spendtrack/ui/feature/assets/realestate/detail/RealEstateDetailViewModel.kt`
- Create: `app/src/main/java/com/spendtrack/ui/feature/assets/realestate/detail/RealEstateDetailScreen.kt`
- Modify: `app/src/main/java/com/spendtrack/ui/navigation/AppNavGraph.kt`

This task ends with a successful build because all of List → Picker → Detail wiring is in place. The edit-pencil button routes to `Screen.EditRealEstate.route`, which is registered in the sealed class but has no `composable` entry until Task 17 — tapping it before then triggers a runtime route-not-found error, but compilation succeeds.

- [ ] **Step 1: Write the ViewModel test**

```kotlin
package com.spendtrack.ui.feature.assets.realestate.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.spendtrack.domain.model.EnergyRating
import com.spendtrack.domain.model.InterestType
import com.spendtrack.domain.model.RealEstateAsset
import com.spendtrack.domain.model.ReferenceRate
import com.spendtrack.domain.usecase.GetRealEstateUseCase
import com.spendtrack.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class RealEstateDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getUseCase: GetRealEstateUseCase = mockk()

    private fun asset(
        id: Long = 1L,
        currentValue: Double = 300_000.0,
        outstandingDebt: Double? = 100_000.0,
        interestType: InterestType? = InterestType.VARIABLE,
        fixedRate: Double? = null,
        referenceRate: ReferenceRate? = ReferenceRate.EURIBOR_12M,
        spread: Double? = 1.5,
        creditEndDate: LocalDate? = LocalDate.of(2050, 5, 6)
    ) = RealEstateAsset(
        id = id,
        name = "Lisbon flat",
        currencyCode = "EUR",
        currentValue = currentValue,
        currentValueUpdatedAt = Instant.parse("2026-05-06T12:00:00Z"),
        purchaseDate = LocalDate.of(2020, 1, 1),
        notes = null,
        cost = 250_000.0,
        investedCapital = 80_000.0,
        debtAmount = 200_000.0,
        outstandingDebt = outstandingDebt,
        interestType = interestType,
        fixedRate = fixedRate,
        referenceRate = referenceRate,
        spread = spread,
        creditEndDate = creditEndDate,
        district = "Lisboa",
        council = "Lisboa",
        parish = "Alvalade",
        sizeM2 = 85.0,
        energyRating = EnergyRating.B
    )

    private fun viewModel(savedId: Long?) = RealEstateDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("assetId" to savedId?.toString())),
        getRealEstate = getUseCase
    )

    @Test
    fun `loads asset on init and computes equity`() = runTest {
        coEvery { getUseCase(1L) } returns asset()
        val vm = viewModel(1L)
        advanceUntilIdle()

        vm.uiState.test {
            var s = awaitItem()
            while (s.asset == null) s = awaitItem()
            assertEquals(200_000.0, s.equity!!, 0.0001)
            assertFalse(s.loadError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `null id sets loadError`() = runTest {
        val vm = viewModel(null)
        advanceUntilIdle()

        vm.uiState.test {
            var s = awaitItem()
            while (!s.loadError) s = awaitItem()
            assertTrue(s.loadError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `missing asset sets loadError`() = runTest {
        coEvery { getUseCase(99L) } returns null
        val vm = viewModel(99L)
        advanceUntilIdle()

        vm.uiState.test {
            var s = awaitItem()
            while (!s.loadError) s = awaitItem()
            assertTrue(s.loadError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cash purchase has null equity contribution from debt`() = runTest {
        coEvery { getUseCase(1L) } returns asset(
            outstandingDebt = null,
            interestType = null,
            fixedRate = null,
            referenceRate = null,
            spread = null,
            creditEndDate = null
        )
        val vm = viewModel(1L)
        advanceUntilIdle()

        vm.uiState.test {
            var s = awaitItem()
            while (s.asset == null) s = awaitItem()
            // equity == currentValue when no debt
            assertEquals(300_000.0, s.equity!!, 0.0001)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run test, expect compile failure**

Run: `./gradlew testDebugUnitTest --tests com.spendtrack.ui.feature.assets.realestate.detail.RealEstateDetailViewModelTest`
Expected: `Unresolved reference: RealEstateDetailViewModel`

- [ ] **Step 3: Implement `RealEstateDetailViewModel`**

```kotlin
package com.spendtrack.ui.feature.assets.realestate.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtrack.domain.model.RealEstateAsset
import com.spendtrack.domain.usecase.GetRealEstateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RealEstateDetailUiState(
    val asset: RealEstateAsset? = null,
    val loadError: Boolean = false,
) {
    val equity: Double? get() = asset?.let { it.currentValue - (it.outstandingDebt ?: 0.0) }
}

@HiltViewModel
class RealEstateDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getRealEstate: GetRealEstateUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(RealEstateDetailUiState())
    val uiState: StateFlow<RealEstateDetailUiState> = _state.asStateFlow()

    init {
        val id = savedStateHandle.get<String>("assetId")?.toLongOrNull()
        if (id == null) {
            _state.update { it.copy(loadError = true) }
        } else {
            viewModelScope.launch {
                val asset = runCatching { getRealEstate(id) }.getOrNull()
                if (asset == null) {
                    _state.update { it.copy(loadError = true) }
                } else {
                    _state.update { it.copy(asset = asset) }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run tests, verify all pass**

Run: `./gradlew testDebugUnitTest --tests com.spendtrack.ui.feature.assets.realestate.detail.RealEstateDetailViewModelTest`
Expected: 4 tests pass.

- [ ] **Step 5: Implement `RealEstateDetailScreen`**

```kotlin
package com.spendtrack.ui.feature.assets.realestate.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.spendtrack.domain.model.RealEstateAsset
import com.spendtrack.ui.navigation.Screen
import com.spendtrack.util.CurrencyFormatter
import com.spendtrack.util.describeInterest
import com.spendtrack.util.monthsRemaining
import com.spendtrack.util.relativeTimeString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealEstateDetailScreen(
    navController: NavController,
    viewModel: RealEstateDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.asset?.name ?: "Property") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    state.asset?.let { a ->
                        IconButton(onClick = {
                            navController.navigate(Screen.EditRealEstate.createRoute(a.id))
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.loadError -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Couldn't load property")
            }
            state.asset == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading…")
            }
            else -> {
                val asset = state.asset!!
                val equity = state.equity!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
                ) {
                    item { ValuationCard(asset, equity) }
                    item { Spacer(Modifier.height(16.dp)); PurchaseSummary(asset) }
                    item { Spacer(Modifier.height(16.dp)); DebtSection(asset) }
                    item { Spacer(Modifier.height(16.dp)); PropertyDetails(asset) }
                    asset.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                        item { Spacer(Modifier.height(16.dp)); NotesSection(notes) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ValuationCard(asset: RealEstateAsset, equity: Double) {
    Card(modifier = Modifier.padding(bottom = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Current value", style = MaterialTheme.typography.labelMedium)
            Text(
                CurrencyFormatter.formatAbsoluteForCurrency(asset.currentValue, asset.currencyCode),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(4.dp))
            Text("Equity", style = MaterialTheme.typography.labelSmall)
            Text(
                CurrencyFormatter.formatAbsoluteForCurrency(equity, asset.currencyCode),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Updated ${relativeTimeString(asset.currentValueUpdatedAt.toEpochMilli())}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PurchaseSummary(asset: RealEstateAsset) {
    Column {
        Text("Purchase", style = MaterialTheme.typography.titleSmall)
        Text("Date: ${asset.purchaseDate}")
        Text("Cost: ${CurrencyFormatter.formatAbsoluteForCurrency(asset.cost, asset.currencyCode)}")
        Text("Invested capital: ${CurrencyFormatter.formatAbsoluteForCurrency(asset.investedCapital, asset.currencyCode)}")
    }
}

@Composable
private fun DebtSection(asset: RealEstateAsset) {
    Column {
        Text("Debt", style = MaterialTheme.typography.titleSmall)
        if (asset.debtAmount == null) {
            Text("Bought outright")
            return@Column
        }
        Text("Initial: ${CurrencyFormatter.formatAbsoluteForCurrency(asset.debtAmount, asset.currencyCode)}")
        asset.outstandingDebt?.let {
            Text("Outstanding: ${CurrencyFormatter.formatAbsoluteForCurrency(it, asset.currencyCode)}")
        }
        Text(describeInterest(asset))
        asset.creditEndDate?.let {
            Text("End date: $it")
            Text(monthsRemaining(it))
        }
    }
}

@Composable
private fun PropertyDetails(asset: RealEstateAsset) {
    Column {
        Text("Details", style = MaterialTheme.typography.titleSmall)
        Text("${asset.district} · ${asset.council} · ${asset.parish}")
        Text("Size: ${asset.sizeM2} m²")
        Text("Energy rating: ${asset.energyRating.label}")
    }
}

@Composable
private fun NotesSection(notes: String) {
    Column {
        Text("Notes", style = MaterialTheme.typography.titleSmall)
        Text(notes)
    }
}
```

- [ ] **Step 6: Update `AppNavGraph.kt` — add routes, replace bottom nav, remove Activity**

Open `app/src/main/java/com/spendtrack/ui/navigation/AppNavGraph.kt`. Make these edits:

(a) Replace these icon imports:

```kotlin
import androidx.compose.material.icons.filled.Search
```

with:

```kotlin
import androidx.compose.material.icons.filled.AccountBalance
```

(b) Add screen imports:

```kotlin
import com.spendtrack.ui.feature.assets.list.AssetsListScreen
import com.spendtrack.ui.feature.assets.realestate.detail.RealEstateDetailScreen
```

(c) Replace the entire `sealed class Screen` block with:

```kotlin
sealed class Screen(val route: String) {
    object Timeline : Screen("timeline")
    object Overview : Screen("overview")
    object Assets : Screen("assets")
    object Settings : Screen("settings")
    object AddTransaction : Screen("add_transaction")
    object EditTransaction : Screen("edit_transaction/{transactionId}") {
        fun createRoute(id: Long) = "edit_transaction/$id"
    }
    object Import : Screen("import")
    object RealEstateDetail : Screen("real_estate_detail/{assetId}") {
        fun createRoute(id: Long) = "real_estate_detail/$id"
    }
    object AddRealEstate : Screen("add_real_estate")
    object EditRealEstate : Screen("edit_real_estate/{assetId}") {
        fun createRoute(id: Long) = "edit_real_estate/$id"
    }
}
```

(d) Replace the `bottomNavItems` list with:

```kotlin
private val bottomNavItems = listOf(
    Triple(Screen.Timeline, Icons.Default.Home, "Timeline"),
    Triple(Screen.Overview, Icons.Default.BarChart, "Overview"),
    Triple(Screen.Assets, Icons.Default.AccountBalance, "Assets"),
    Triple(Screen.Settings, Icons.Default.MoreHoriz, "More"),
)
```

(e) In the `NavHost`, replace the existing `composable(Screen.Activity.route) { ActivityPlaceholder() }` block with:

```kotlin
            composable(Screen.Assets.route) {
                AssetsListScreen(navController = navController)
            }
            composable(Screen.RealEstateDetail.route) { backStack ->
                RealEstateDetailScreen(navController = navController)
            }
```

(Note: `RealEstateDetailScreen` reads `assetId` via `SavedStateHandle` injected into the ViewModel — no need to pull it from `backStack` here.)

(f) Delete the `private fun ActivityPlaceholder()` composable and any unused imports it relied on (`Box`, `Alignment` may still be used elsewhere — check before removing).

- [ ] **Step 7: Build everything (List screen + Picker + Detail screen + nav)**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. The edit-pencil button on detail navigates to `Screen.EditRealEstate.route`, which is registered in the sealed class but has no `composable` entry yet — that's added in Task 17. Tapping it before then will hit a runtime route-not-found error, but compile will succeed.

- [ ] **Step 8: Commit (covers Tasks 13, 14, 15)**

```bash
git add app/src/main/java/com/spendtrack/ui/feature/assets/component/TotalWealthHeader.kt \
        app/src/main/java/com/spendtrack/ui/feature/assets/component/AssetListRow.kt \
        app/src/main/java/com/spendtrack/ui/feature/assets/list/AssetsListScreen.kt \
        app/src/main/java/com/spendtrack/ui/feature/assets/typepicker/AssetTypePickerSheet.kt \
        app/src/test/java/com/spendtrack/ui/feature/assets/realestate/detail/RealEstateDetailViewModelTest.kt \
        app/src/main/java/com/spendtrack/ui/feature/assets/realestate/detail/RealEstateDetailViewModel.kt \
        app/src/main/java/com/spendtrack/ui/feature/assets/realestate/detail/RealEstateDetailScreen.kt \
        app/src/main/java/com/spendtrack/ui/navigation/AppNavGraph.kt
git commit -m "feat(assets): assets list, type picker, real-estate detail, nav update"
```

---

## Task 16: AddEditRealEstateViewModel (TDD)

**Files:**
- Create: `app/src/test/java/com/spendtrack/ui/feature/assets/realestate/edit/AddEditRealEstateViewModelTest.kt`
- Create: `app/src/main/java/com/spendtrack/ui/feature/assets/realestate/edit/AddEditRealEstateViewModel.kt`

This ViewModel is the most complex part of the spec. Form state stores raw input strings (so user typing "12," doesn't truncate). Validation is reactive. Save and delete mirror existing patterns.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.spendtrack.ui.feature.assets.realestate.edit

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.spendtrack.data.settings.AppSettings
import com.spendtrack.data.settings.SettingsRepository
import com.spendtrack.domain.model.EnergyRating
import com.spendtrack.domain.model.InterestType
import com.spendtrack.domain.model.RealEstateAsset
import com.spendtrack.domain.model.ReferenceRate
import com.spendtrack.domain.usecase.DeleteAssetUseCase
import com.spendtrack.domain.usecase.GetRealEstateUseCase
import com.spendtrack.domain.usecase.SaveRealEstateUseCase
import com.spendtrack.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AddEditRealEstateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val save: SaveRealEstateUseCase = mockk(relaxed = true)
    private val delete: DeleteAssetUseCase = mockk(relaxed = true)
    private val get: GetRealEstateUseCase = mockk()
    private val settingsRepository: SettingsRepository = mockk()

    private val existing = RealEstateAsset(
        id = 7L,
        name = "Existing flat",
        currencyCode = "USD",
        currentValue = 250_000.0,
        currentValueUpdatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        purchaseDate = LocalDate.of(2020, 1, 1),
        notes = "old",
        cost = 230_000.0,
        investedCapital = 80_000.0,
        debtAmount = 150_000.0,
        outstandingDebt = 120_000.0,
        interestType = InterestType.FIXED,
        fixedRate = 3.0,
        referenceRate = null,
        spread = null,
        creditEndDate = LocalDate.of(2050, 1, 1),
        district = "Lisboa",
        council = "Lisboa",
        parish = "Alvalade",
        sizeM2 = 85.0,
        energyRating = EnergyRating.B
    )

    @Before
    fun setUp() {
        coEvery { save(any()) } returns 42L
        every { settingsRepository.settings } returns MutableStateFlow(AppSettings())
    }

    private fun viewModel(assetId: Long? = null) = AddEditRealEstateViewModel(
        savedStateHandle = SavedStateHandle(mapOf("assetId" to assetId?.toString())),
        saveUseCase = save,
        deleteUseCase = delete,
        getUseCase = get,
        settingsRepository = settingsRepository
    )

    private fun fillValidForm(vm: AddEditRealEstateViewModel) {
        vm.onName("Lisbon flat")
        vm.onPurchaseDate(LocalDate.of(2020, 1, 1))
        vm.onCurrentValue("300000")
        vm.onCost("250000")
        vm.onInvestedCapital("80000")
        vm.onDebtAmount("200000")
        vm.onOutstandingDebt("180000")
        vm.onInterestType(InterestType.VARIABLE)
        vm.onReferenceRate(ReferenceRate.EURIBOR_12M)
        vm.onSpread("1.5")
        vm.onCreditEndDate(LocalDate.of(2050, 1, 1))
        vm.onDistrict("Lisboa")
        vm.onCouncil("Lisboa")
        vm.onParish("Alvalade")
        vm.onSizeM2("85")
        vm.onEnergyRating(EnergyRating.B)
    }

    @Test
    fun `empty form has all required errors`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.uiState.test {
            val s = awaitItem()
            assertTrue(s.formErrors.hasAny)
            assertNotNull(s.formErrors.name)
            assertNotNull(s.formErrors.purchaseDate)
            assertNotNull(s.formErrors.currentValue)
            assertNotNull(s.formErrors.cost)
            assertNotNull(s.formErrors.investedCapital)
            assertNotNull(s.formErrors.district)
            assertNotNull(s.formErrors.council)
            assertNotNull(s.formErrors.parish)
            assertNotNull(s.formErrors.sizeM2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `valid form has no errors`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        fillValidForm(vm)

        vm.uiState.test {
            var s = awaitItem()
            while (s.formErrors.hasAny) s = awaitItem()
            assertFalse(s.formErrors.hasAny)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `debt fields not required when debt amount blank`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onName("Lisbon flat")
        vm.onPurchaseDate(LocalDate.of(2020, 1, 1))
        vm.onCurrentValue("300000")
        vm.onCost("250000")
        vm.onInvestedCapital("80000")
        // skip debt entirely
        vm.onDistrict("Lisboa")
        vm.onCouncil("Lisboa")
        vm.onParish("Alvalade")
        vm.onSizeM2("85")
        vm.onEnergyRating(EnergyRating.B)

        vm.uiState.test {
            var s = awaitItem()
            while (s.formErrors.hasAny) s = awaitItem()
            assertNull(s.formErrors.outstandingDebt)
            assertNull(s.formErrors.creditEndDate)
            assertNull(s.formErrors.interestType)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `fixed interest does not require reference rate`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        fillValidForm(vm)
        vm.onInterestType(InterestType.FIXED)
        vm.onFixedRate("3,2")
        vm.onSpread("") // remove spread
        vm.onReferenceRate(null) // remove reference rate

        vm.uiState.test {
            var s = awaitItem()
            while (s.formErrors.hasAny) s = awaitItem()
            assertFalse(s.formErrors.hasAny)
            assertNull(s.formErrors.referenceRate)
            assertNull(s.formErrors.spread)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `variable interest requires reference rate and spread`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        fillValidForm(vm)
        vm.onReferenceRate(null)
        vm.onSpread("")

        vm.uiState.test {
            var s = awaitItem()
            while (s.formErrors.referenceRate == null) s = awaitItem()
            assertNotNull(s.formErrors.referenceRate)
            assertNotNull(s.formErrors.spread)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `purchase date in future flagged`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        fillValidForm(vm)
        vm.onPurchaseDate(LocalDate.now().plusDays(1))

        vm.uiState.test {
            var s = awaitItem()
            while (s.formErrors.purchaseDate == null) s = awaitItem()
            assertNotNull(s.formErrors.purchaseDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `credit end date before purchase date flagged`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        fillValidForm(vm)
        vm.onPurchaseDate(LocalDate.of(2020, 1, 1))
        vm.onCreditEndDate(LocalDate.of(2019, 1, 1))

        vm.uiState.test {
            var s = awaitItem()
            while (s.formErrors.creditEndDate == null) s = awaitItem()
            assertNotNull(s.formErrors.creditEndDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `decimal input accepts comma`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        fillValidForm(vm)
        vm.onCurrentValue("123,45")

        vm.uiState.test {
            var s = awaitItem()
            while (s.formErrors.hasAny) s = awaitItem()
            assertFalse(s.formErrors.hasAny)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `non-numeric current value flagged`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        fillValidForm(vm)
        vm.onCurrentValue("abc")

        vm.uiState.test {
            var s = awaitItem()
            while (s.formErrors.currentValue == null) s = awaitItem()
            assertNotNull(s.formErrors.currentValue)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `add saves and sets isSaved`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        fillValidForm(vm)
        vm.uiState.test {
            // wait for valid state
            var s = awaitItem()
            while (s.formErrors.hasAny) s = awaitItem()
            vm.save()
            advanceUntilIdle()
            // poll until isSaved
            var t = awaitItem()
            while (!t.isSaved) t = awaitItem()
            assertTrue(t.isSaved)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { save(any()) }
    }

    @Test
    fun `save error sets errorMessage and not isSaved`() = runTest {
        coEvery { save(any()) } throws IllegalStateException("boom")
        val vm = viewModel()
        advanceUntilIdle()
        fillValidForm(vm)
        vm.save()
        advanceUntilIdle()

        vm.uiState.test {
            val s = awaitItem()
            assertFalse(s.isSaved)
            assertNotNull(s.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `edit mode loads asset and currency is read only`() = runTest {
        coEvery { get(7L) } returns existing
        val vm = viewModel(7L)
        advanceUntilIdle()

        // Direct value reads — onCurrencyCode is a no-op in edit mode and does not
        // trigger an emission, so awaitItem() would hang.
        assertEquals("Existing flat", vm.uiState.value.name)
        assertEquals("USD", vm.uiState.value.currencyCode)
        assertTrue(vm.uiState.value.isEditMode)

        vm.onCurrencyCode("EUR")
        advanceUntilIdle()
        assertEquals("USD", vm.uiState.value.currencyCode)
    }

    @Test
    fun `delete in edit mode invokes use case`() = runTest {
        coEvery { get(7L) } returns existing
        val vm = viewModel(7L)
        advanceUntilIdle()
        vm.uiState.test {
            // wait for load
            var s = awaitItem()
            while (s.name != "Existing flat") s = awaitItem()
            vm.confirmDelete()
            advanceUntilIdle()
            var t = awaitItem()
            while (!t.isDeleted) t = awaitItem()
            assertTrue(t.isDeleted)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { delete(7L) }
    }
}
```

(Note: the test uses `io.mockk.every` for `settingsRepository.settings` inline because it's a non-suspend property; the `every0Settings` helper avoids polluting the import section.)

- [ ] **Step 2: Run tests, verify compile failure**

Run: `./gradlew testDebugUnitTest --tests com.spendtrack.ui.feature.assets.realestate.edit.AddEditRealEstateViewModelTest`
Expected: `Unresolved reference: AddEditRealEstateViewModel`

- [ ] **Step 3: Implement `AddEditRealEstateViewModel`**

```kotlin
package com.spendtrack.ui.feature.assets.realestate.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtrack.data.settings.SettingsRepository
import com.spendtrack.domain.model.EnergyRating
import com.spendtrack.domain.model.InterestType
import com.spendtrack.domain.model.RealEstateAsset
import com.spendtrack.domain.model.ReferenceRate
import com.spendtrack.domain.usecase.DeleteAssetUseCase
import com.spendtrack.domain.usecase.GetRealEstateUseCase
import com.spendtrack.domain.usecase.SaveRealEstateUseCase
import com.spendtrack.util.parseDecimal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class FormErrors(
    val name: String? = null,
    val purchaseDate: String? = null,
    val currentValue: String? = null,
    val cost: String? = null,
    val investedCapital: String? = null,
    val debtAmount: String? = null,
    val outstandingDebt: String? = null,
    val interestType: String? = null,
    val fixedRate: String? = null,
    val referenceRate: String? = null,
    val spread: String? = null,
    val creditEndDate: String? = null,
    val district: String? = null,
    val council: String? = null,
    val parish: String? = null,
    val sizeM2: String? = null,
) {
    val hasAny: Boolean
        get() = listOf(
            name, purchaseDate, currentValue, cost, investedCapital,
            debtAmount, outstandingDebt, interestType, fixedRate,
            referenceRate, spread, creditEndDate, district, council, parish, sizeM2
        ).any { it != null }
}

data class AddEditRealEstateUiState(
    val isEditMode: Boolean = false,
    val name: String = "",
    val currencyCode: String = "EUR",
    val currentValue: String = "",
    val purchaseDate: LocalDate? = null,
    val notes: String = "",
    val cost: String = "",
    val investedCapital: String = "",
    val debtAmount: String = "",
    val outstandingDebt: String = "",
    val interestType: InterestType? = null,
    val fixedRate: String = "",
    val referenceRate: ReferenceRate? = null,
    val spread: String = "",
    val creditEndDate: LocalDate? = null,
    val district: String = "",
    val council: String = "",
    val parish: String = "",
    val sizeM2: String = "",
    val energyRating: EnergyRating = EnergyRating.B,
    val formErrors: FormErrors = FormErrors(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class AddEditRealEstateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val saveUseCase: SaveRealEstateUseCase,
    private val deleteUseCase: DeleteAssetUseCase,
    private val getUseCase: GetRealEstateUseCase,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val editingId: Long = savedStateHandle.get<String>("assetId")?.toLongOrNull() ?: 0L
    private val isEditMode = editingId != 0L
    private var loadedCurrencyCode: String? = null

    private val initialState = AddEditRealEstateUiState(
        isEditMode = isEditMode,
        currencyCode = settingsRepository.settings.value.currencyCode
    )
    private val _form = MutableStateFlow(initialState)

    val uiState: StateFlow<AddEditRealEstateUiState> = _form
        .map { it.copy(formErrors = computeErrors(it)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = initialState.copy(formErrors = computeErrors(initialState))
        )

    init {
        if (isEditMode) loadAsset(editingId)
    }

    private fun loadAsset(id: Long) {
        viewModelScope.launch {
            val asset = runCatching { getUseCase(id) }.getOrNull() ?: return@launch
            loadedCurrencyCode = asset.currencyCode
            _form.update {
                it.copy(
                    name = asset.name,
                    currencyCode = asset.currencyCode,
                    currentValue = asset.currentValue.toCleanString(),
                    purchaseDate = asset.purchaseDate,
                    notes = asset.notes ?: "",
                    cost = asset.cost.toCleanString(),
                    investedCapital = asset.investedCapital.toCleanString(),
                    debtAmount = asset.debtAmount?.toCleanString() ?: "",
                    outstandingDebt = asset.outstandingDebt?.toCleanString() ?: "",
                    interestType = asset.interestType,
                    fixedRate = asset.fixedRate?.toCleanString() ?: "",
                    referenceRate = asset.referenceRate,
                    spread = asset.spread?.toCleanString() ?: "",
                    creditEndDate = asset.creditEndDate,
                    district = asset.district,
                    council = asset.council,
                    parish = asset.parish,
                    sizeM2 = asset.sizeM2.toCleanString(),
                    energyRating = asset.energyRating
                )
            }
        }
    }

    fun onName(v: String) = _form.update { it.copy(name = v) }
    fun onCurrencyCode(v: String) {
        if (isEditMode) return // immutable after creation
        _form.update { it.copy(currencyCode = v) }
    }
    fun onPurchaseDate(v: LocalDate?) = _form.update { it.copy(purchaseDate = v) }
    fun onCurrentValue(v: String) = _form.update { it.copy(currentValue = v) }
    fun onNotes(v: String) = _form.update { it.copy(notes = v) }
    fun onCost(v: String) = _form.update { it.copy(cost = v) }
    fun onInvestedCapital(v: String) = _form.update { it.copy(investedCapital = v) }
    fun onDebtAmount(v: String) = _form.update {
        val cleared = v.isBlank()
        it.copy(
            debtAmount = v,
            outstandingDebt = if (cleared) "" else it.outstandingDebt.ifBlank { v },
            interestType = if (cleared) null else it.interestType,
            fixedRate = if (cleared) "" else it.fixedRate,
            referenceRate = if (cleared) null else it.referenceRate,
            spread = if (cleared) "" else it.spread,
            creditEndDate = if (cleared) null else it.creditEndDate
        )
    }
    fun onOutstandingDebt(v: String) = _form.update { it.copy(outstandingDebt = v) }
    fun onInterestType(t: InterestType?) = _form.update { it.copy(interestType = t) }
    fun onFixedRate(v: String) = _form.update { it.copy(fixedRate = v) }
    fun onReferenceRate(r: ReferenceRate?) = _form.update { it.copy(referenceRate = r) }
    fun onSpread(v: String) = _form.update { it.copy(spread = v) }
    fun onCreditEndDate(v: LocalDate?) = _form.update { it.copy(creditEndDate = v) }
    fun onDistrict(v: String) = _form.update { it.copy(district = v) }
    fun onCouncil(v: String) = _form.update { it.copy(council = v) }
    fun onParish(v: String) = _form.update { it.copy(parish = v) }
    fun onSizeM2(v: String) = _form.update { it.copy(sizeM2 = v) }
    fun onEnergyRating(r: EnergyRating) = _form.update { it.copy(energyRating = r) }

    fun showDeleteConfirmation() = _form.update { it.copy(showDeleteConfirmation = true) }
    fun hideDeleteConfirmation() = _form.update { it.copy(showDeleteConfirmation = false) }

    fun save() {
        val state = _form.value
        if (computeErrors(state).hasAny) return
        _form.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val asset = state.toDomain(id = editingId)
                saveUseCase(asset)
                _form.update { it.copy(isLoading = false, isSaved = true) }
            } catch (t: IllegalStateException) {
                // RealEstateRepository.save throws this when the parent row was deleted
                // out from under us. Surface a specific message; user taps back manually.
                _form.update {
                    it.copy(isLoading = false, errorMessage = "This property no longer exists")
                }
            } catch (t: Throwable) {
                _form.update {
                    it.copy(isLoading = false, errorMessage = "Couldn't save property. Try again.")
                }
            }
        }
    }

    fun clearErrorMessage() {
        _form.update { it.copy(errorMessage = null) }
    }

    fun confirmDelete() {
        if (!isEditMode) return
        _form.update { it.copy(showDeleteConfirmation = false, isLoading = true) }
        viewModelScope.launch {
            try {
                deleteUseCase(editingId)
                _form.update { it.copy(isLoading = false, isDeleted = true) }
            } catch (t: Throwable) {
                _form.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Couldn't delete property. Try again."
                    )
                }
            }
        }
    }

    private fun computeErrors(s: AddEditRealEstateUiState): FormErrors {
        val today = LocalDate.now()
        val nameErr = if (s.name.isBlank()) "Name is required" else null
        val purchaseErr = when {
            s.purchaseDate == null -> "Purchase date is required"
            s.purchaseDate.isAfter(today) -> "Cannot be in the future"
            else -> null
        }
        fun amtErr(field: String, raw: String, requireAtLeastZero: Boolean = true): String? {
            if (raw.isBlank()) return "$field is required"
            val v = parseDecimal(raw) ?: return "$field is required"
            return if (requireAtLeastZero && v < 0) "$field must be ≥ 0" else null
        }
        val currentValueErr = amtErr("Current value", s.currentValue)
        val costErr = amtErr("Cost", s.cost)
        val capitalErr = amtErr("Invested capital", s.investedCapital)

        val hasDebt = s.debtAmount.isNotBlank()
        val debtAmountErr = if (hasDebt) {
            val v = parseDecimal(s.debtAmount)
            if (v == null || v <= 0) "Must be greater than 0" else null
        } else null

        val outstandingErr = if (hasDebt) amtErr("Outstanding debt", s.outstandingDebt) else null
        val interestTypeErr = if (hasDebt && s.interestType == null) "Pick fixed or variable" else null
        val fixedErr = if (hasDebt && s.interestType == InterestType.FIXED) {
            val v = parseDecimal(s.fixedRate)
            if (s.fixedRate.isBlank() || v == null) "Rate is required" else null
        } else null
        val referenceErr =
            if (hasDebt && s.interestType == InterestType.VARIABLE && s.referenceRate == null)
                "Pick a reference rate"
            else null
        val spreadErr = if (hasDebt && s.interestType == InterestType.VARIABLE) {
            val v = parseDecimal(s.spread)
            if (s.spread.isBlank() || v == null) "Spread is required" else null
        } else null
        val endDateErr = if (hasDebt) {
            when {
                s.creditEndDate == null -> "Credit end date is required"
                s.purchaseDate != null && !s.creditEndDate.isAfter(s.purchaseDate) ->
                    "Must be after purchase date"
                else -> null
            }
        } else null

        val districtErr = if (s.district.isBlank()) "Required" else null
        val councilErr = if (s.council.isBlank()) "Required" else null
        val parishErr = if (s.parish.isBlank()) "Required" else null
        val sizeErr = run {
            val v = parseDecimal(s.sizeM2)
            if (s.sizeM2.isBlank() || v == null || v <= 0)
                "Size must be greater than 0"
            else null
        }

        return FormErrors(
            name = nameErr,
            purchaseDate = purchaseErr,
            currentValue = currentValueErr,
            cost = costErr,
            investedCapital = capitalErr,
            debtAmount = debtAmountErr,
            outstandingDebt = outstandingErr,
            interestType = interestTypeErr,
            fixedRate = fixedErr,
            referenceRate = referenceErr,
            spread = spreadErr,
            creditEndDate = endDateErr,
            district = districtErr,
            council = councilErr,
            parish = parishErr,
            sizeM2 = sizeErr
        )
    }

    private fun AddEditRealEstateUiState.toDomain(id: Long): RealEstateAsset {
        val hasDebt = debtAmount.isNotBlank()
        return RealEstateAsset(
            id = id,
            name = name.trim(),
            currencyCode = currencyCode,
            currentValue = parseDecimal(currentValue)!!,
            currentValueUpdatedAt = Instant.now(),
            purchaseDate = purchaseDate!!,
            notes = notes.takeIf { it.isNotBlank() },
            cost = parseDecimal(cost)!!,
            investedCapital = parseDecimal(investedCapital)!!,
            debtAmount = if (hasDebt) parseDecimal(debtAmount) else null,
            outstandingDebt = if (hasDebt) parseDecimal(outstandingDebt) else null,
            interestType = if (hasDebt) interestType else null,
            fixedRate = if (hasDebt && interestType == InterestType.FIXED) parseDecimal(fixedRate) else null,
            referenceRate = if (hasDebt && interestType == InterestType.VARIABLE) referenceRate else null,
            spread = if (hasDebt && interestType == InterestType.VARIABLE) parseDecimal(spread) else null,
            creditEndDate = if (hasDebt) creditEndDate else null,
            district = district.trim(),
            council = council.trim(),
            parish = parish.trim(),
            sizeM2 = parseDecimal(sizeM2)!!,
            energyRating = energyRating
        )
    }
}

private fun Double.toCleanString(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString() else toString()
```

- [ ] **Step 4: Run tests, verify all pass**

Run: `./gradlew testDebugUnitTest --tests com.spendtrack.ui.feature.assets.realestate.edit.AddEditRealEstateViewModelTest`
Expected: 13 tests pass.

If any test is flaky on the "isSaved/isDeleted" timing, replace the polling pattern (`while (!s.isSaved) s = awaitItem()`) with `vm.uiState.value` reads after `advanceUntilIdle()`.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/spendtrack/ui/feature/assets/realestate/edit/AddEditRealEstateViewModelTest.kt \
        app/src/main/java/com/spendtrack/ui/feature/assets/realestate/edit/AddEditRealEstateViewModel.kt
git commit -m "feat(assets): add real-estate add/edit ViewModel with validation"
```

---

## Task 17: AddEditRealEstateScreen + nav wiring

**Files:**
- Create: `app/src/main/java/com/spendtrack/ui/feature/assets/realestate/edit/AddEditRealEstateScreen.kt`
- Modify: `app/src/main/java/com/spendtrack/ui/navigation/AppNavGraph.kt`

This is a long form. The screen renders all sections in a single scroll using `LazyColumn` for performance, but each section is its own `@Composable`. The save button at the bottom is rendered as a `Scaffold` `bottomBar`.

- [ ] **Step 1: Create `AddEditRealEstateScreen`**

```kotlin
package com.spendtrack.ui.feature.assets.realestate.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.spendtrack.domain.model.EnergyRating
import com.spendtrack.domain.model.InterestType
import com.spendtrack.domain.model.ReferenceRate
import androidx.compose.foundation.text.KeyboardOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditRealEstateScreen(
    navController: NavController,
    viewModel: AddEditRealEstateViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.isSaved, state.isDeleted) {
        if (state.isSaved || state.isDeleted) navController.popBackStack()
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }

    if (state.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteConfirmation() },
            title = { Text("Delete property?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteConfirmation() }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditMode) "Edit property" else "Add property") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isEditMode) {
                        IconButton(onClick = { viewModel.showDeleteConfirmation() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Button(
                    onClick = { viewModel.save() },
                    enabled = !state.formErrors.hasAny && !state.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save") }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            item { Text("Basics", style = MaterialTheme.typography.titleSmall) }
            item { TextRow("Name", state.name, state.formErrors.name, viewModel::onName) }
            item {
                CurrencyDropdown(
                    value = state.currencyCode,
                    enabled = !state.isEditMode,
                    onChange = viewModel::onCurrencyCode
                )
            }
            item {
                DateRow(
                    label = "Purchase date",
                    value = state.purchaseDate,
                    error = state.formErrors.purchaseDate,
                    onChange = viewModel::onPurchaseDate
                )
            }
            item { NumberRow("Current value", state.currentValue, state.formErrors.currentValue, viewModel::onCurrentValue) }

            item { Spacer(Modifier.height(8.dp)); Text("Money", style = MaterialTheme.typography.titleSmall) }
            item { NumberRow("Cost", state.cost, state.formErrors.cost, viewModel::onCost) }
            item { NumberRow("Invested capital", state.investedCapital, state.formErrors.investedCapital, viewModel::onInvestedCapital) }
            item { NumberRow("Debt amount (leave blank if cash)", state.debtAmount, state.formErrors.debtAmount, viewModel::onDebtAmount) }

            if (state.debtAmount.isNotBlank()) {
                item { Spacer(Modifier.height(8.dp)); Text("Debt details", style = MaterialTheme.typography.titleSmall) }
                item { NumberRow("Outstanding debt", state.outstandingDebt, state.formErrors.outstandingDebt, viewModel::onOutstandingDebt) }
                item {
                    InterestTypeRow(
                        selected = state.interestType,
                        error = state.formErrors.interestType,
                        onChange = viewModel::onInterestType
                    )
                }
                if (state.interestType == InterestType.FIXED) {
                    item { NumberRow("Fixed rate (%)", state.fixedRate, state.formErrors.fixedRate, viewModel::onFixedRate) }
                }
                if (state.interestType == InterestType.VARIABLE) {
                    item {
                        ReferenceRateDropdown(
                            value = state.referenceRate,
                            error = state.formErrors.referenceRate,
                            onChange = viewModel::onReferenceRate
                        )
                    }
                    item { NumberRow("Spread (%)", state.spread, state.formErrors.spread, viewModel::onSpread) }
                }
                item {
                    DateRow(
                        label = "Credit end date",
                        value = state.creditEndDate,
                        error = state.formErrors.creditEndDate,
                        onChange = viewModel::onCreditEndDate
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)); Text("Property", style = MaterialTheme.typography.titleSmall) }
            item { TextRow("District", state.district, state.formErrors.district, viewModel::onDistrict) }
            item { TextRow("Council", state.council, state.formErrors.council, viewModel::onCouncil) }
            item { TextRow("Parish", state.parish, state.formErrors.parish, viewModel::onParish) }
            item { NumberRow("Size (m²)", state.sizeM2, state.formErrors.sizeM2, viewModel::onSizeM2) }
            item {
                EnergyRatingDropdown(
                    value = state.energyRating,
                    onChange = viewModel::onEnergyRating
                )
            }

            item { Spacer(Modifier.height(8.dp)); Text("Notes", style = MaterialTheme.typography.titleSmall) }
            item {
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotes,
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item { Spacer(Modifier.height(80.dp)) } // breathing room above bottom bar
        }
    }
}

@Composable
private fun TextRow(label: String, value: String, error: String?, onChange: (String) -> Unit) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            isError = error != null,
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun NumberRow(label: String, value: String, error: String?, onChange: (String) -> Unit) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun DateRow(
    label: String,
    value: java.time.LocalDate?,
    error: String?,
    onChange: (java.time.LocalDate?) -> Unit
) {
    // Minimal date input: text field expecting YYYY-MM-DD. The user already encounters
    // this format on the Add Transaction screen via the existing date picker; keeping
    // this implementation simple here. A native DatePickerDialog can be added later.
    var raw by remember(value) { mutableStateOf(value?.toString() ?: "") }
    Column {
        OutlinedTextField(
            value = raw,
            onValueChange = {
                raw = it
                val parsed = runCatching { java.time.LocalDate.parse(it) }.getOrNull()
                onChange(parsed)
            },
            label = { Text("$label (YYYY-MM-DD)") },
            isError = error != null,
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyDropdown(
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit
) {
    val options = listOf("EUR", "USD", "GBP", "BRL", "CHF", "CAD", "AUD", "JPY")
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Currency") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { code ->
                DropdownMenuItem(
                    text = { Text(code) },
                    onClick = { onChange(code); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun InterestTypeRow(
    selected: InterestType?,
    error: String?,
    onChange: (InterestType) -> Unit
) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selected == InterestType.FIXED,
                onClick = { onChange(InterestType.FIXED) },
                label = { Text("Fixed") }
            )
            FilterChip(
                selected = selected == InterestType.VARIABLE,
                onClick = { onChange(InterestType.VARIABLE) },
                label = { Text("Variable") }
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReferenceRateDropdown(
    value: ReferenceRate?,
    error: String?,
    onChange: (ReferenceRate?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = value?.label ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Reference rate") },
                isError = error != null,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ReferenceRate.values().forEach { rate ->
                    DropdownMenuItem(
                        text = { Text(rate.label) },
                        onClick = { onChange(rate); expanded = false }
                    )
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnergyRatingDropdown(
    value: EnergyRating,
    onChange: (EnergyRating) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = value.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Energy rating") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            EnergyRating.values().forEach { rating ->
                DropdownMenuItem(
                    text = { Text(rating.label) },
                    onClick = { onChange(rating); expanded = false }
                )
            }
        }
    }
}

```

Note: the calls to `ExposedDropdownMenu(...)` inside each `ExposedDropdownMenuBox` resolve to the real Material 3 API (`androidx.compose.material3.ExposedDropdownMenu`) — no helper wrapper is needed. Make sure your imports include it explicitly:

```kotlin
import androidx.compose.material3.ExposedDropdownMenu
```

- [ ] **Step 2: Update `AppNavGraph.kt` — register the add/edit composable**

Add import:

```kotlin
import com.spendtrack.ui.feature.assets.realestate.edit.AddEditRealEstateScreen
```

Add inside the `NavHost` body, after the `RealEstateDetail` route:

```kotlin
            composable(Screen.AddRealEstate.route) {
                AddEditRealEstateScreen(navController = navController)
            }
            composable(Screen.EditRealEstate.route) {
                AddEditRealEstateScreen(navController = navController)
            }
```

The ViewModel reads `assetId` from `SavedStateHandle` — the route parameter `{assetId}` is automatically placed there by Navigation Compose.

- [ ] **Step 3: Build everything**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. If Compose complains about `menuAnchor` deprecation or experimental API, add `@OptIn(ExperimentalMaterial3Api::class)` at file level or accept the deprecation.

- [ ] **Step 4: Run all unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: all tests pass (existing transaction tests + new asset/use-case/VM/util tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/spendtrack/ui/feature/assets/realestate/edit/AddEditRealEstateScreen.kt \
        app/src/main/java/com/spendtrack/ui/navigation/AppNavGraph.kt
git commit -m "feat(assets): add real-estate add/edit form and wire to navigation"
```

---

## Task 18: Manual verification

**Files:** none (manual checks)

This task gates the spec being marked complete. The unit tests don't cover the Room migration, the actual on-device DAO joins, the cascade delete, or the navigation flow — these are deferred to instrumented tests in a future spec. The manual checklist below substitutes for now.

- [ ] **Step 1: Build and install debug APK on a connected emulator**

```bash
./gradlew assembleDebug
/Users/pedrobatista/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
/Users/pedrobatista/Library/Android/sdk/platform-tools/adb shell am start -n com.spendtrack/.MainActivity
```

- [ ] **Step 2: Run the manual verification checklist**

Walk through each item. If any fails, note which Task introduced the bug and fix forward in a follow-up commit.

1. Bottom nav shows **Assets** (account-balance icon) instead of **Activity**.
2. Tapping **Assets** opens an empty list with "No assets yet · Tap + to add one".
3. FAB opens a bottom sheet with **Real Estate** (enabled) and **Financial · Coming soon** (disabled).
4. Tapping **Real Estate** opens the Add Property form.
5. Submitting an empty form: Save is disabled.
6. Filling all required fields with debt left blank → Save enabled. Save returns to list. List shows the property; header shows "Net wealth" with `current_value`.
7. Add another property *with* debt (variable rate, EURIBOR_12M + 1.5%). Header now shows `sum(current_value − outstanding_debt)`.
8. Add a third property with `currency_code = USD`. Header now shows two lines: `€... · $...` with subtitle "mixed currencies".
9. Tapping a row opens the Detail screen. All fields render correctly. The interest description shows "Euribor 12M + 1,50%" or "Fixed 3,20%" or "Bought outright".
10. Pencil → Edit form pre-fills all fields. Currency dropdown is disabled. Change `current_value`, save → Detail screen now shows "Updated today".
11. Edit form → trash icon → AlertDialog → confirm → list returns without that asset.
12. Upgrade test: revert to a previous APK build (or sideload pre-Task-6 APK), seed it with a transaction, then sideload the new APK and confirm: existing transactions/categories intact, Assets section appears empty.

- [ ] **Step 3: Update `CLAUDE.md` with the new feature**

Open `CLAUDE.md` at the repo root. Make these additions:

(a) In the "Package Structure" tree, replace the `ui/feature/` block to include `assets/`:

```
    ├── feature/
    │   ├── addtransaction/         — AddTransactionScreen + ViewModel (dual-purpose: add and edit)
    │   ├── assets/                 — Assets list + type picker + real estate detail/edit
    │   ├── csvimport/              — ImportScreen + ViewModel (NOTE: package is csvimport, not import — reserved keyword)
    │   ├── overview/               — OverviewScreen + ViewModel
    │   ├── settings/               — SettingsScreen + ViewModel
    │   └── timeline/               — TimelineScreen + ViewModel
```

(b) In "Navigation Routes", remove the `activity` row and add:

```
| `assets` | Assets list (replaces Activity) |
| `real_estate_detail/{assetId}` | Real Estate detail (read-only) |
| `add_real_estate` | Add Real Estate |
| `edit_real_estate/{assetId}` | Edit Real Estate |
```

(c) In "Known Gaps / Future Work", remove the `Activity screen is a placeholder` line and add:

```
- Financial assets (ETF / stocks / crypto) — separate spec, includes Yahoo Finance + 15-min cache
- Linking transactions to assets (assetId FK on transactions)
- Live Euribor rate fetching for variable-rate real estate
- Photos for real-estate properties
- Instrumented tests (Room migration tests, DAO query tests, FK CASCADE verification, Compose UI tests)
```

- [ ] **Step 4: Commit the docs update**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md for assets/real-estate feature"
```

---

## Summary

After all 18 tasks:

- Bottom nav has an Assets slot (replacing the Activity placeholder).
- Users can add, view, edit, and delete real-estate assets with cost / invested capital / debt sub-record / location / energy rating / notes.
- The Assets list shows total wealth as net equity, per-currency.
- Real estate detail screen shows valuation, equity, debt summary (with Euribor reconstruction), property details, "updated X ago" freshness.
- Validation is reactive; save button disabled while invalid.
- Currency is locked after creation. CASCADE deletes the detail row.
- All ViewModels and the totals use case are unit-tested.
- Room migrates from v1 → v2 without losing existing transactions/categories data.
- The financial-assets spec has a clear seam to plug into (parent table, list query LEFT JOIN, type picker, navigation pattern).
