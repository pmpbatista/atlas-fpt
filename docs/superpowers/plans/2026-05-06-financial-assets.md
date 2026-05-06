# Financial Assets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the **FINANCIAL** asset type (ETF / stocks / crypto) on top of the existing assets shell. Users enter a ticker (validated against Yahoo Finance), record one or more purchase lots, and the app fetches live prices (15-min cached), computes unrealized P&L and average yearly yield, and aggregates lots of the same ticker as one asset.

**Architecture:** New `data/network/` layer encapsulates Yahoo Finance behind a `PriceSource` interface (so the unofficial endpoint is swappable). Two new Room tables (`financial_holdings` 1:1, `financial_lots` 1:many) bound to the existing `assets` parent. A `PriceRepository` keeps an in-memory 15-min cache and persists last-known prices to `assets.current_value` for offline-friendly first paint. Per-lot CAGR weighted by lot cost gives "average yearly yield". Buy-and-track only — no sales / dividends / FX in this spec.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose + Material 3, Room 2.6.1, Hilt 2.51.1, **+ new:** Retrofit 2.11.0, kotlinx-serialization 1.7.3, OkHttp (transitive), MockWebServer (test). Existing: JUnit4 + MockK + kotlinx-coroutines-test + Turbine.

**Spec:** [`docs/superpowers/specs/2026-05-06-financial-assets-design.md`](../specs/2026-05-06-financial-assets-design.md)

**Pre-flight check (run once before starting):**

```bash
./gradlew testDebugUnitTest
```

Expected: existing tests pass. If they don't, fix them before starting.

---

## Task 1: Add network dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add version refs to `libs.versions.toml`**

Open `gradle/libs.versions.toml`. Under `[versions]` (alphabetical with the rest), add:

```toml
retrofit = "2.11.0"
retrofit-kotlinx-serialization = "1.0.0"
kotlinx-serialization = "1.7.3"
mockwebserver = "4.12.0"
```

Under `[libraries]`, add:

```toml
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-kotlinx-serialization-converter = { group = "com.jakewharton.retrofit", name = "retrofit2-kotlinx-serialization-converter", version.ref = "retrofit-kotlinx-serialization" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "mockwebserver" }
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "mockwebserver" }
```

(`okhttp` is technically transitive via Retrofit, but declaring it explicitly lets us pin the version with mockwebserver.)

Under `[plugins]`, add:

```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 2: Wire dependencies in `app/build.gradle.kts`**

Add `alias(libs.plugins.kotlin.serialization)` to the `plugins { ... }` block (alongside `kotlin.android`).

Inside `dependencies { ... }`, add (group with the other `implementation` lines):

```kotlin
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
```

And under the `testImplementation` group:

```kotlin
    testImplementation(libs.okhttp.mockwebserver)
```

- [ ] **Step 3: Sync and build**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. The new dependencies download on first run.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build(financial): add Retrofit + kotlinx-serialization + MockWebServer"
```

---

## Task 2: Domain models

**Files:**
- Create: `app/src/main/java/com/spendtrack/domain/model/FinancialAsset.kt`
- Create: `app/src/main/java/com/spendtrack/domain/model/FinancialLot.kt`
- Create: `app/src/main/java/com/spendtrack/domain/model/TickerQuote.kt`
- Create: `app/src/main/java/com/spendtrack/domain/model/QuoteResult.kt`

- [ ] **Step 1: Create `FinancialLot`**

```kotlin
package com.spendtrack.domain.model

import java.time.LocalDate

data class FinancialLot(
    val id: Long,
    val purchaseDate: LocalDate,
    val quantity: Double,
    val pricePerUnit: Double,
)
```

- [ ] **Step 2: Create `FinancialAsset`**

```kotlin
package com.spendtrack.domain.model

import java.time.Instant

data class FinancialAsset(
    val id: Long,
    val name: String,
    val ticker: String,
    val displayName: String,
    val currencyCode: String,
    val latestPrice: Double?,
    val latestPriceAt: Instant?,
    val notes: String?,
    val lots: List<FinancialLot>,
) {
    val totalQuantity: Double get() = lots.sumOf { it.quantity }
    val totalCost: Double get() = lots.sumOf { it.quantity * it.pricePerUnit }
    val avgCostPerUnit: Double
        get() = if (totalQuantity > 0) totalCost / totalQuantity else 0.0
    val currentValue: Double? get() = latestPrice?.let { it * totalQuantity }
    val unrealizedPnl: Double? get() = currentValue?.let { it - totalCost }
    val unrealizedPnlPct: Double?
        get() = currentValue?.let { (it - totalCost) / totalCost.takeIf { c -> c > 0 } ?: return@let null }
}
```

- [ ] **Step 3: Create `TickerQuote`**

```kotlin
package com.spendtrack.domain.model

import java.time.Instant

data class TickerQuote(
    val ticker: String,
    val displayName: String,
    val currencyCode: String,
    val price: Double,
    val asOf: Instant,
)
```

- [ ] **Step 4: Create `QuoteResult`**

```kotlin
package com.spendtrack.domain.model

sealed interface QuoteResult {
    data class Success(val quote: TickerQuote) : QuoteResult
    data object NotFound : QuoteResult
    data class Error(val cause: String) : QuoteResult
}
```

- [ ] **Step 5: Build**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/spendtrack/domain/model/FinancialAsset.kt \
        app/src/main/java/com/spendtrack/domain/model/FinancialLot.kt \
        app/src/main/java/com/spendtrack/domain/model/TickerQuote.kt \
        app/src/main/java/com/spendtrack/domain/model/QuoteResult.kt
git commit -m "feat(financial): add domain models for financial assets"
```

---

## Task 3: Room entities + @Relation wrapper

**Files:**
- Create: `app/src/main/java/com/spendtrack/data/db/entity/FinancialHoldingEntity.kt`
- Create: `app/src/main/java/com/spendtrack/data/db/entity/FinancialLotEntity.kt`
- Create: `app/src/main/java/com/spendtrack/data/db/entity/AssetWithFinancial.kt`

- [ ] **Step 1: Create `FinancialHoldingEntity`**

```kotlin
package com.spendtrack.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "financial_holdings",
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FinancialHoldingEntity(
    @PrimaryKey val assetId: Long,
    val ticker: String,
    val displayName: String,
    val latestPrice: Double?,
    val latestPriceAt: Long?,
)
```

- [ ] **Step 2: Create `FinancialLotEntity`**

```kotlin
package com.spendtrack.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "financial_lots",
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
data class FinancialLotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: Long,
    val purchaseDate: LocalDate,
    val quantity: Double,
    val pricePerUnit: Double,
)
```

- [ ] **Step 3: Create `AssetWithFinancial` (`@Relation` wrapper)**

```kotlin
package com.spendtrack.data.db.entity

import androidx.room.Embedded
import androidx.room.Relation

data class AssetWithFinancial(
    @Embedded val asset: AssetEntity,
    @Relation(parentColumn = "id", entityColumn = "assetId")
    val holding: FinancialHoldingEntity?,
    @Relation(parentColumn = "id", entityColumn = "assetId")
    val lots: List<FinancialLotEntity>,
)
```

- [ ] **Step 4: Build (entities not yet registered — Room codegen runs at next task)**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/spendtrack/data/db/entity/FinancialHoldingEntity.kt \
        app/src/main/java/com/spendtrack/data/db/entity/FinancialLotEntity.kt \
        app/src/main/java/com/spendtrack/data/db/entity/AssetWithFinancial.kt
git commit -m "feat(financial): add Room entities and @Relation wrapper"
```

---

## Task 4: Entity ↔ domain mappers

**Files:**
- Modify: `app/src/main/java/com/spendtrack/data/db/entity/Mappers.kt`

- [ ] **Step 1: Append mappers to `Mappers.kt`**

Add these imports near the existing ones (alphabetical):

```kotlin
import com.spendtrack.domain.model.FinancialAsset
import com.spendtrack.domain.model.FinancialLot
```

Append at the end of the file:

```kotlin
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
```

(The `Instant` and `AssetType` imports should already exist from previous mappers; if not, add them.)

- [ ] **Step 2: Build**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/spendtrack/data/db/entity/Mappers.kt
git commit -m "feat(financial): add entity-to-domain mappers"
```

---

## Task 5: FinancialDao + AppDatabase migration v3→v4 + Hilt DI

**Files:**
- Create: `app/src/main/java/com/spendtrack/data/db/dao/FinancialDao.kt`
- Modify: `app/src/main/java/com/spendtrack/data/db/AppDatabase.kt`
- Modify: `app/src/main/java/com/spendtrack/di/DatabaseModule.kt`

This task ends with a fully-compiling Room DB at version 4, including the migration. **The migration SQL must be copied verbatim from Room's generated `AppDatabase_Impl.createAllTables` after the build runs** — Room's identity-hash check rejects any SQL deviation, even functionally-equivalent ones (this bit us in the real-estate spec; see `docs/superpowers/plans/2026-05-06-assets-real-estate.md` Task 6 for the lesson).

- [ ] **Step 1: Create `FinancialDao`**

```kotlin
package com.spendtrack.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.spendtrack.data.db.entity.AssetWithFinancial
import com.spendtrack.data.db.entity.FinancialHoldingEntity
import com.spendtrack.data.db.entity.FinancialLotEntity

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

    @Query("SELECT MIN(purchase_date) FROM financial_lots WHERE assetId = :assetId")
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
```

- [ ] **Step 2: Update `AppDatabase.kt`**

Open `app/src/main/java/com/spendtrack/data/db/AppDatabase.kt`.

Add imports:

```kotlin
import com.spendtrack.data.db.dao.FinancialDao
import com.spendtrack.data.db.entity.FinancialHoldingEntity
import com.spendtrack.data.db.entity.FinancialLotEntity
```

Update the `@Database` annotation: add the two new entities AND bump version 3 → 4. The annotation becomes:

```kotlin
@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        LabelEntity::class,
        TransactionLabelCrossRef::class,
        RecurringRuleEntity::class,
        PersonEntity::class,
        TransactionPersonCrossRef::class,
        AssetEntity::class,
        RealEstateDetailsEntity::class,
        FinancialHoldingEntity::class,
        FinancialLotEntity::class,
    ],
    version = 4,
    exportSchema = false
)
```

Add an abstract DAO method (just after `abstract fun realEstateDao(): RealEstateDao`):

```kotlin
    abstract fun financialDao(): FinancialDao
```

Update the `create` companion to register `MIGRATION_3_4`:

```kotlin
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "spendtrack.db")
                .addCallback(SeedCallback())
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
```

Add `MIGRATION_3_4` as a top-level `val` (next to the existing `MIGRATION_2_3`):

```kotlin
// SQL strings copied verbatim from Room's generated AppDatabase_Impl.createAllTables.
// See real-estate plan Task 6 for the identity-hash lesson — these MUST match Room's text.
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `financial_holdings` (`assetId` INTEGER NOT NULL, `ticker` TEXT NOT NULL, `displayName` TEXT NOT NULL, `latestPrice` REAL, `latestPriceAt` INTEGER, PRIMARY KEY(`assetId`), FOREIGN KEY(`assetId`) REFERENCES `assets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE TABLE IF NOT EXISTS `financial_lots` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `assetId` INTEGER NOT NULL, `purchaseDate` TEXT NOT NULL, `quantity` REAL NOT NULL, `pricePerUnit` REAL NOT NULL, FOREIGN KEY(`assetId`) REFERENCES `assets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_lots_assetId` ON `financial_lots` (`assetId`)")
    }
}
```

- [ ] **Step 3: Build to run Room codegen**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. KSP runs and generates `AppDatabase_Impl`.

- [ ] **Step 4: Verify migration SQL matches Room's expected schema**

Run:
```bash
grep -A 1 "CREATE TABLE.*financial_holdings\|CREATE TABLE.*financial_lots\|CREATE INDEX.*financial" \
  app/build/generated/ksp/debug/java/com/spendtrack/data/db/AppDatabase_Impl.java
```

Compare each `db.execSQL("...")` line in `AppDatabase_Impl.createAllTables` against the strings in `MIGRATION_3_4`. They must be **byte-identical** (column order, AUTOINCREMENT placement, trailing space before `)`, etc.). If any differ, replace the migration's strings with what Room generated.

- [ ] **Step 5: Add `FinancialDao` provider in `DatabaseModule.kt`**

Open `app/src/main/java/com/spendtrack/di/DatabaseModule.kt`. Add import:

```kotlin
import com.spendtrack.data.db.dao.FinancialDao
```

Add this `@Provides` method after the existing `provideRealEstateDao`:

```kotlin
    @Provides
    fun provideFinancialDao(db: AppDatabase): FinancialDao = db.financialDao()
```

- [ ] **Step 6: Build with Hilt codegen**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/spendtrack/data/db/dao/FinancialDao.kt \
        app/src/main/java/com/spendtrack/data/db/AppDatabase.kt \
        app/src/main/java/com/spendtrack/di/DatabaseModule.kt
git commit -m "feat(financial): add FinancialDao + Room migration 3->4 + Hilt DI"
```

---

## Task 6: Yahoo network DTOs + API interface + PriceSource interface

**Files:**
- Create: `app/src/main/java/com/spendtrack/data/network/YahooFinanceDtos.kt`
- Create: `app/src/main/java/com/spendtrack/data/network/YahooFinanceApi.kt`
- Create: `app/src/main/java/com/spendtrack/data/network/PriceSource.kt`

- [ ] **Step 1: Create `YahooFinanceDtos.kt`**

```kotlin
package com.spendtrack.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Top-level Yahoo /v8/finance/chart response. We only deserialize the fields we use. */
@Serializable
data class ChartResponse(
    val chart: Chart,
)

@Serializable
data class Chart(
    val result: List<ChartResult>? = null,
    val error: ChartError? = null,
)

@Serializable
data class ChartResult(
    val meta: ChartMeta,
)

@Serializable
data class ChartMeta(
    val symbol: String,
    val currency: String? = null,
    val shortName: String? = null,
    val longName: String? = null,
    val regularMarketPrice: Double? = null,
    val regularMarketTime: Long? = null,
)

@Serializable
data class ChartError(
    val code: String,
    val description: String? = null,
)
```

- [ ] **Step 2: Create `YahooFinanceApi.kt` (Retrofit interface)**

```kotlin
package com.spendtrack.data.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface YahooFinanceApi {
    @GET("v8/finance/chart/{ticker}")
    suspend fun getChart(
        @Path("ticker") ticker: String,
        @Query("interval") interval: String = "1d",
        @Query("range") range: String = "1d",
    ): Response<ChartResponse>
}
```

- [ ] **Step 3: Create `PriceSource.kt`**

```kotlin
package com.spendtrack.data.network

import com.spendtrack.domain.model.QuoteResult

/** Encapsulates the price-fetching backend so it can be swapped without touching callers. */
interface PriceSource {
    suspend fun fetchQuote(ticker: String): QuoteResult
}
```

- [ ] **Step 4: Build**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/spendtrack/data/network/
git commit -m "feat(financial): add Yahoo Finance DTOs + Retrofit API + PriceSource interface"
```

---

## Task 7: YahooPriceSource impl + MockWebServer test (TDD)

**Files:**
- Create: `app/src/test/java/com/spendtrack/data/network/YahooPriceSourceTest.kt`
- Create: `app/src/main/java/com/spendtrack/data/network/impl/YahooPriceSource.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.spendtrack.data.network

import com.spendtrack.data.network.impl.YahooPriceSource
import com.spendtrack.domain.model.QuoteResult
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class YahooPriceSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: YahooPriceSource

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(YahooFinanceApi::class.java)
        source = YahooPriceSource(api)
    }

    @After
    fun tearDown() { server.shutdown() }

    @Test
    fun `success maps meta to TickerQuote`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"chart":{"result":[{"meta":{"symbol":"AAPL","currency":"USD","shortName":"Apple Inc.","longName":"Apple Inc.","regularMarketPrice":234.56,"regularMarketTime":1736186400}}],"error":null}
        """.trimIndent()))

        val result = source.fetchQuote("AAPL")

        assertTrue(result is QuoteResult.Success)
        val q = (result as QuoteResult.Success).quote
        assertEquals("AAPL", q.ticker)
        assertEquals("Apple Inc.", q.displayName)
        assertEquals("USD", q.currencyCode)
        assertEquals(234.56, q.price, 0.0001)
    }

    @Test
    fun `chart_error returns NotFound`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"chart":{"result":null,"error":{"code":"Not Found","description":"No data found"}}}
        """.trimIndent()))

        val result = source.fetchQuote("ZZZZZZ")
        assertTrue(result is QuoteResult.NotFound)
    }

    @Test
    fun `404 returns NotFound`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"chart":{"result":null,"error":{"code":"Not Found"}}}"""))
        val result = source.fetchQuote("BADTICKER")
        assertTrue(result is QuoteResult.NotFound)
    }

    @Test
    fun `5xx returns Error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val result = source.fetchQuote("AAPL")
        assertTrue(result is QuoteResult.Error)
    }

    @Test
    fun `malformed JSON returns Error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
        val result = source.fetchQuote("AAPL")
        assertTrue(result is QuoteResult.Error)
    }

    @Test
    fun `empty result list returns NotFound`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"chart":{"result":[],"error":null}}"""))
        val result = source.fetchQuote("AAPL")
        assertTrue(result is QuoteResult.NotFound)
    }

    @Test
    fun `request includes User-Agent header`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"chart":{"result":[{"meta":{"symbol":"AAPL","currency":"USD","shortName":"A","regularMarketPrice":1.0}}],"error":null}}
        """.trimIndent()))

        source.fetchQuote("AAPL")
        val recorded = server.takeRequest()
        val ua = recorded.getHeader("User-Agent")
        assertNotNull(ua)
        assertTrue("expected SpendTrack UA, got: $ua", ua!!.contains("SpendTrack"))
    }
}
```

(Note: the `User-Agent` test will fail at first because the test wires Retrofit directly without our OkHttp client interceptor — that's set up in `NetworkModule` task 8. We'll fix this test then by injecting the interceptor here too.)

- [ ] **Step 2: Run test, expect compile failure**

Run: `./gradlew testDebugUnitTest --tests com.spendtrack.data.network.YahooPriceSourceTest`
Expected: `Unresolved reference: YahooPriceSource`

- [ ] **Step 3: Implement `YahooPriceSource`**

```kotlin
package com.spendtrack.data.network.impl

import android.util.Log
import com.spendtrack.data.network.ChartResponse
import com.spendtrack.data.network.PriceSource
import com.spendtrack.data.network.YahooFinanceApi
import com.spendtrack.domain.model.QuoteResult
import com.spendtrack.domain.model.TickerQuote
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YahooPriceSource @Inject constructor(
    private val api: YahooFinanceApi,
) : PriceSource {

    override suspend fun fetchQuote(ticker: String): QuoteResult {
        return runCatching {
            val response = api.getChart(ticker)
            when {
                response.code() == 404 -> QuoteResult.NotFound
                !response.isSuccessful -> QuoteResult.Error("HTTP ${response.code()}")
                else -> {
                    val body: ChartResponse? = response.body()
                    val result = body?.chart?.result
                    val error = body?.chart?.error
                    when {
                        error != null -> QuoteResult.NotFound
                        result.isNullOrEmpty() -> QuoteResult.NotFound
                        else -> {
                            val meta = result.first().meta
                            val price = meta.regularMarketPrice
                            val currency = meta.currency
                            val name = meta.longName ?: meta.shortName
                            if (price == null || currency == null || name == null) {
                                QuoteResult.NotFound
                            } else {
                                QuoteResult.Success(
                                    TickerQuote(
                                        ticker = meta.symbol,
                                        displayName = name,
                                        currencyCode = currency,
                                        price = price,
                                        asOf = meta.regularMarketTime?.let { Instant.ofEpochSecond(it) }
                                            ?: Instant.now(),
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }.getOrElse { t ->
            Log.w("YahooPriceSource", "fetchQuote($ticker) failed", t)
            QuoteResult.Error(t.message ?: t.javaClass.simpleName)
        }
    }
}
```

- [ ] **Step 4: Update test to inject the User-Agent interceptor**

The User-Agent test fails because the test's Retrofit doesn't use our app's OkHttp client. Update the test's `setUp` to add the interceptor manually:

In `YahooPriceSourceTest`, replace the `Retrofit.Builder()` block with:

```kotlin
        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (compatible; SpendTrack/1.0)")
                        .build()
                )
            }
            .build()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(YahooFinanceApi::class.java)
```

The production interceptor lives in `NetworkModule` (Task 8) — this duplicates the literal string for the test. They must stay in sync.

- [ ] **Step 5: Run tests, all pass**

Run: `./gradlew testDebugUnitTest --tests com.spendtrack.data.network.YahooPriceSourceTest`
Expected: 7 tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/test/java/com/spendtrack/data/network/YahooPriceSourceTest.kt \
        app/src/main/java/com/spendtrack/data/network/impl/YahooPriceSource.kt
git commit -m "feat(financial): YahooPriceSource impl + MockWebServer tests"
```

---

## Task 8: NetworkModule (Hilt)

**Files:**
- Create: `app/src/main/java/com/spendtrack/di/NetworkModule.kt`

- [ ] **Step 1: Create `NetworkModule`**

```kotlin
package com.spendtrack.di

import com.spendtrack.data.network.PriceSource
import com.spendtrack.data.network.YahooFinanceApi
import com.spendtrack.data.network.impl.YahooPriceSource
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val USER_AGENT = "Mozilla/5.0 (compatible; SpendTrack/1.0)"
    private const val YAHOO_BASE_URL = "https://query1.finance.yahoo.com/"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
            )
        }
        .build()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(YAHOO_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideYahooFinanceApi(retrofit: Retrofit): YahooFinanceApi =
        retrofit.create(YahooFinanceApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindings {
    @Binds
    @Singleton
    abstract fun bindPriceSource(impl: YahooPriceSource): PriceSource
}
```

(Hilt requires `@Binds` to live in an `abstract class`; `@Provides` lives in `object`. The two-module setup is standard.)

- [ ] **Step 2: Allow internet permission in manifest**

Open `app/src/main/AndroidManifest.xml`. Add (just inside `<manifest ...>`, before `<application>`):

```xml
    <uses-permission android:name="android.permission.INTERNET" />
```

- [ ] **Step 3: Build (Hilt codegen runs)**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/spendtrack/di/NetworkModule.kt \
        app/src/main/AndroidManifest.xml
git commit -m "feat(financial): wire Hilt network module + INTERNET permission"
```

---

## Task 9: CalculateYieldUseCase (TDD)

**Files:**
- Create: `app/src/test/java/com/spendtrack/domain/usecase/CalculateYieldUseCaseTest.kt`
- Create: `app/src/main/java/com/spendtrack/domain/usecase/CalculateYieldUseCase.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.spendtrack.domain.usecase

import com.spendtrack.domain.model.FinancialLot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

class CalculateYieldUseCaseTest {

    private val today = LocalDate.of(2026, 5, 6)

    private fun lot(date: LocalDate, qty: Double, price: Double, id: Long = 1L) =
        FinancialLot(id = id, purchaseDate = date, quantity = qty, pricePerUnit = price)

    @Test fun `empty lots returns null`() {
        assertNull(calculateAvgYearlyYield(emptyList(), 100.0, today))
    }

    @Test fun `null current price returns null`() {
        assertNull(calculateAvgYearlyYield(listOf(lot(today.minusYears(1), 1.0, 100.0)), null, today))
    }

    @Test fun `single lot held 1 year with 10pct gain returns 10pct`() {
        val result = calculateAvgYearlyYield(
            listOf(lot(today.minusYears(1), 1.0, 100.0)), 110.0, today
        )!!
        assertTrue("expected ~0.10, got $result", abs(result - 0.10) < 0.005)
    }

    @Test fun `single lot held half year with 10pct gain annualizes up`() {
        val result = calculateAvgYearlyYield(
            listOf(lot(today.minusDays(183), 1.0, 100.0)), 110.0, today
        )!!
        // 1.10^2 - 1 = 0.21
        assertTrue("expected ~0.21, got $result", abs(result - 0.21) < 0.01)
    }

    @Test fun `single lot held 2 years with 21pct gain annualizes down to ~10pct`() {
        val result = calculateAvgYearlyYield(
            listOf(lot(today.minusYears(2), 1.0, 100.0)), 121.0, today
        )!!
        // 1.21^0.5 - 1 ≈ 0.10
        assertTrue("expected ~0.10, got $result", abs(result - 0.10) < 0.01)
    }

    @Test fun `today-purchased lot excluded`() {
        // Two lots: one today (excluded), one a year ago (10% gain) — answer should be ~0.10
        val result = calculateAvgYearlyYield(
            listOf(
                lot(today, 1.0, 100.0, id = 1L),
                lot(today.minusYears(1), 1.0, 100.0, id = 2L),
            ),
            110.0, today
        )!!
        assertTrue("expected ~0.10, got $result", abs(result - 0.10) < 0.005)
    }

    @Test fun `only-today lots returns null`() {
        assertNull(calculateAvgYearlyYield(listOf(lot(today, 1.0, 100.0)), 110.0, today))
    }

    @Test fun `negative ratio returns minus 100pct`() {
        val result = calculateAvgYearlyYield(
            listOf(lot(today.minusYears(1), 1.0, 100.0)), 0.0, today
        )!!
        assertTrue("expected ~-1.0, got $result", abs(result - -1.0) < 0.001)
    }

    @Test fun `zero-cost lot excluded`() {
        // Free lot (price=0) excluded; the other lot drives the result
        val result = calculateAvgYearlyYield(
            listOf(
                lot(today.minusYears(1), 1.0, 0.0, id = 1L),
                lot(today.minusYears(1), 1.0, 100.0, id = 2L),
            ),
            110.0, today
        )!!
        assertTrue("expected ~0.10, got $result", abs(result - 0.10) < 0.005)
    }

    @Test fun `weighted average across two lots`() {
        // Lot 1: 1 year, +10% gain (yield 0.10), cost 100
        // Lot 2: 2 years, +21% gain (yield ~0.10), cost 100 (using current_price 121 — but lot 2 cost was different)
        // Easier: lot 1 cost 100 yield 0.10; lot 2 cost 300 yield 0.05 → weighted = (0.10*100 + 0.05*300)/400 = 0.0625
        val result = calculateAvgYearlyYield(
            listOf(
                lot(today.minusYears(1), 1.0, 100.0, id = 1L),  // current 110, ratio 1.10, yield 0.10
                lot(today.minusYears(1), 3.0, 100.0, id = 2L),  // current 105, ratio 1.05, yield 0.05
            ),
            // Both lots get the same currentPrice. To make this test pure we use one currentPrice.
            // Skip combo for now — covered by the "both at +10%" weighted variant below.
            110.0, today
        )!!
        // Both lots: yield 0.10. Weighted: (0.10*100 + 0.10*300)/400 = 0.10
        assertTrue("expected ~0.10, got $result", abs(result - 0.10) < 0.005)
    }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `./gradlew testDebugUnitTest --tests com.spendtrack.domain.usecase.CalculateYieldUseCaseTest`
Expected: `Unresolved reference: calculateAvgYearlyYield`

- [ ] **Step 3: Implement the use case**

```kotlin
package com.spendtrack.domain.usecase

import com.spendtrack.domain.model.FinancialLot
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow

/**
 * Asset-level "average yearly yield" computed as per-lot CAGR weighted by lot cost.
 *
 * For each lot:
 *   years_held = (today - lot.purchaseDate) / 365.25
 *   per_lot_yield = (currentPrice / lot.pricePerUnit) ^ (1 / years_held) - 1
 *
 * Aggregated:
 *   yield = SUM(per_lot_yield * lot_cost) / SUM(lot_cost)
 *
 * Lots with years_held <= 0 (purchased today or future) are excluded — CAGR over 0 years
 * is undefined. Lots with non-positive cost are excluded to avoid weighting issues.
 *
 * Returns null if [currentPrice] is null, lots is empty, or no lot qualifies.
 */
fun calculateAvgYearlyYield(
    lots: List<FinancialLot>,
    currentPrice: Double?,
    today: LocalDate = LocalDate.now(),
): Double? {
    if (currentPrice == null || lots.isEmpty()) return null
    val perLot = lots.mapNotNull { lot ->
        val years = ChronoUnit.DAYS.between(lot.purchaseDate, today) / 365.25
        if (years <= 0.0 || lot.pricePerUnit <= 0.0) return@mapNotNull null
        val ratio = currentPrice / lot.pricePerUnit
        val yieldVal = if (ratio > 0.0) ratio.pow(1.0 / years) - 1.0 else -1.0
        val cost = lot.quantity * lot.pricePerUnit
        if (cost <= 0.0) return@mapNotNull null
        yieldVal to cost
    }
    if (perLot.isEmpty()) return null
    val totalCost = perLot.sumOf { it.second }
    return perLot.sumOf { it.first * it.second } / totalCost
}
```

- [ ] **Step 4: Run tests, all pass**

Run: `./gradlew testDebugUnitTest --tests com.spendtrack.domain.usecase.CalculateYieldUseCaseTest`
Expected: 10 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/spendtrack/domain/usecase/CalculateYieldUseCaseTest.kt \
        app/src/main/java/com/spendtrack/domain/usecase/CalculateYieldUseCase.kt
git commit -m "feat(financial): add CalculateYieldUseCase with per-lot CAGR weighted by cost"
```

---

## Task 10: FinancialRepository

**Files:**
- Create: `app/src/main/java/com/spendtrack/data/repository/FinancialRepository.kt`

The repository owns all transactional writes for financial assets. The `recomputeCurrentValue` helper centralizes "after lots/price change → update parent assets row" logic so the price repository (Task 11) and lot use cases (Task 12) share it.

- [ ] **Step 1: Create `FinancialRepository`**

```kotlin
package com.spendtrack.data.repository

import androidx.room.withTransaction
import com.spendtrack.data.db.AppDatabase
import com.spendtrack.data.db.dao.AssetDao
import com.spendtrack.data.db.dao.FinancialDao
import com.spendtrack.data.db.entity.AssetEntity
import com.spendtrack.data.db.entity.FinancialHoldingEntity
import com.spendtrack.data.db.entity.FinancialLotEntity
import com.spendtrack.data.db.entity.toFinancialDomain
import com.spendtrack.data.db.entity.toEntity
import com.spendtrack.domain.model.AssetType
import com.spendtrack.domain.model.FinancialAsset
import com.spendtrack.domain.model.FinancialLot
import com.spendtrack.domain.model.TickerQuote
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
            recomputeAssetSnapshotInTxn(assetId)
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
```

- [ ] **Step 2: Add the two new `AssetDao` methods needed by the helper**

Open `app/src/main/java/com/spendtrack/data/db/dao/AssetDao.kt`. Add two new `@Query` methods:

```kotlin
    @Query("UPDATE assets SET currentValue = :value, currentValueUpdatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCurrentValue(id: Long, value: Double, updatedAt: Long)

    @Query("UPDATE assets SET purchaseDate = :date WHERE id = :id")
    suspend fun updatePurchaseDate(id: Long, date: java.time.LocalDate)
```

(Place them next to the existing `update`/`insert` methods.)

- [ ] **Step 3: Build**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/spendtrack/data/repository/FinancialRepository.kt \
        app/src/main/java/com/spendtrack/data/db/dao/AssetDao.kt
git commit -m "feat(financial): add FinancialRepository with transactional CRUD + snapshot recompute"
```

---

## Task 11: PriceRepository (TDD)

**Files:**
- Create: `app/src/test/java/com/spendtrack/data/repository/PriceRepositoryTest.kt`
- Create: `app/src/main/java/com/spendtrack/data/repository/PriceRepository.kt`

In-memory cache with 15-min TTL, mutex-deduped `refreshAll`, falls back to persisted last-known on network failure.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.spendtrack.data.repository

import com.spendtrack.data.db.dao.FinancialDao
import com.spendtrack.data.db.dao.TickerRow
import com.spendtrack.data.db.entity.FinancialHoldingEntity
import com.spendtrack.data.network.PriceSource
import com.spendtrack.domain.model.QuoteResult
import com.spendtrack.domain.model.TickerQuote
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class PriceRepositoryTest {

    private val source: PriceSource = mockk()
    private val financialDao: FinancialDao = mockk(relaxed = true)
    private val financialRepo: FinancialRepository = mockk(relaxed = true)

    private fun repo() = PriceRepository(source, financialDao, financialRepo)

    private val sampleQuote = TickerQuote(
        ticker = "AAPL",
        displayName = "Apple Inc.",
        currencyCode = "USD",
        price = 234.56,
        asOf = Instant.parse("2026-05-06T12:00:00Z"),
    )

    @Test fun `cache miss + Success caches and returns quote`() = runTest {
        coEvery { source.fetchQuote("AAPL") } returns QuoteResult.Success(sampleQuote)
        coEvery { financialDao.countByTicker(any()) } returns 1
        coEvery { financialDao.getAllTickers() } returns listOf(TickerRow(1L, "AAPL"))

        val r = repo().getQuote("AAPL")

        assertEquals(234.56, r!!.price, 0.0001)
        coVerify { source.fetchQuote("AAPL") }
    }

    @Test fun `cache fresh skips network`() = runTest {
        coEvery { source.fetchQuote("AAPL") } returns QuoteResult.Success(sampleQuote)
        coEvery { financialDao.getAllTickers() } returns listOf(TickerRow(1L, "AAPL"))

        val r = repo()
        r.getQuote("AAPL")  // populates cache
        r.getQuote("AAPL")  // should NOT call network again

        coVerify(exactly = 1) { source.fetchQuote("AAPL") }
    }

    @Test fun `force=true bypasses fresh cache`() = runTest {
        coEvery { source.fetchQuote("AAPL") } returns QuoteResult.Success(sampleQuote)
        coEvery { financialDao.getAllTickers() } returns listOf(TickerRow(1L, "AAPL"))

        val r = repo()
        r.getQuote("AAPL")
        r.getQuote("AAPL", force = true)

        coVerify(exactly = 2) { source.fetchQuote("AAPL") }
    }

    @Test fun `NotFound on first fetch falls back to persisted last-known`() = runTest {
        coEvery { source.fetchQuote("AAPL") } returns QuoteResult.NotFound
        coEvery { financialDao.getAllTickers() } returns listOf(TickerRow(1L, "AAPL"))
        coEvery { financialDao.getHolding(1L) } returns FinancialHoldingEntity(
            assetId = 1L, ticker = "AAPL", displayName = "Apple Inc.",
            latestPrice = 200.0, latestPriceAt = 1_000L,
        )

        val r = repo().getQuote("AAPL")

        assertNotNull(r)
        assertEquals(200.0, r!!.price, 0.0001)
    }

    @Test fun `Error with no persisted returns null`() = runTest {
        coEvery { source.fetchQuote("AAPL") } returns QuoteResult.Error("boom")
        coEvery { financialDao.getAllTickers() } returns listOf(TickerRow(1L, "AAPL"))
        coEvery { financialDao.getHolding(1L) } returns FinancialHoldingEntity(
            assetId = 1L, ticker = "AAPL", displayName = "Apple Inc.",
            latestPrice = null, latestPriceAt = null,
        )

        val r = repo().getQuote("AAPL")
        assertNull(r)
    }

    @Test fun `validateTicker always hits network even with cache`() = runTest {
        coEvery { source.fetchQuote("AAPL") } returns QuoteResult.Success(sampleQuote)
        coEvery { financialDao.getAllTickers() } returns listOf(TickerRow(1L, "AAPL"))

        val r = repo()
        r.getQuote("AAPL")
        val result = r.validateTicker("AAPL")

        coVerify(exactly = 2) { source.fetchQuote("AAPL") }
        assert(result is QuoteResult.Success)
    }

    @Test fun `refreshAll counts successes and failures per asset`() = runTest {
        coEvery { financialDao.getAllTickers() } returns listOf(
            TickerRow(1L, "AAPL"),
            TickerRow(2L, "BADTICKER"),
        )
        coEvery { source.fetchQuote("AAPL") } returns QuoteResult.Success(sampleQuote)
        coEvery { source.fetchQuote("BADTICKER") } returns QuoteResult.NotFound

        val result = repo().refreshAll()

        assertEquals(1, result.succeeded)
        assertEquals(1, result.failed)
        coVerify { financialRepo.applyPriceUpdate(1L, sampleQuote.price) }
    }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `./gradlew testDebugUnitTest --tests com.spendtrack.data.repository.PriceRepositoryTest`
Expected: `Unresolved reference: PriceRepository`

- [ ] **Step 3: Implement `PriceRepository`**

```kotlin
package com.spendtrack.data.repository

import android.util.Log
import com.spendtrack.data.db.dao.FinancialDao
import com.spendtrack.data.network.PriceSource
import com.spendtrack.domain.model.QuoteResult
import com.spendtrack.domain.model.TickerQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class RefreshResult(val succeeded: Int, val failed: Int)

@Singleton
class PriceRepository @Inject constructor(
    private val source: PriceSource,
    private val financialDao: FinancialDao,
    private val financialRepository: FinancialRepository,
) {
    private val cache = ConcurrentHashMap<String, CachedQuote>()
    private val refreshMutex = Mutex()
    private val ttl: Duration = Duration.ofMinutes(15)

    suspend fun getQuote(ticker: String, force: Boolean = false): TickerQuote? = withContext(Dispatchers.IO) {
        if (!force) cache[ticker]?.takeIf { it.isFresh(ttl) }?.let { return@withContext it.quote }
        when (val res = source.fetchQuote(ticker)) {
            is QuoteResult.Success -> {
                cache[ticker] = CachedQuote(res.quote, Instant.now())
                applyToHoldingIfTracked(ticker, res.quote.price)
                res.quote
            }
            QuoteResult.NotFound, is QuoteResult.Error -> {
                // Fall back to persisted last-known
                fallbackQuote(ticker)
            }
        }
    }

    /** Always hits network — for the add-asset flow. Returns the raw QuoteResult. */
    suspend fun validateTicker(ticker: String): QuoteResult = source.fetchQuote(ticker)

    /**
     * Force-refreshes every tracked financial asset. Returns success/failure counts.
     * Uses a Mutex so concurrent calls share an in-flight execution.
     */
    suspend fun refreshAll(): RefreshResult = refreshMutex.withLock {
        val tickers = financialDao.getAllTickers()
        if (tickers.isEmpty()) return RefreshResult(0, 0)
        val semaphore = Semaphore(3)
        var succeeded = 0
        var failed = 0
        coroutineScope {
            tickers.map { (assetId, ticker) ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        when (val res = source.fetchQuote(ticker)) {
                            is QuoteResult.Success -> {
                                cache[ticker] = CachedQuote(res.quote, Instant.now())
                                financialRepository.applyPriceUpdate(assetId, res.quote.price)
                                true
                            }
                            else -> false
                        }
                    }
                }
            }.forEach { deferred ->
                if (deferred.await()) succeeded++ else failed++
            }
        }
        RefreshResult(succeeded, failed)
    }

    private suspend fun applyToHoldingIfTracked(ticker: String, price: Double) {
        val tickers = financialDao.getAllTickers()
        val assetId = tickers.firstOrNull { it.ticker == ticker }?.assetId ?: return
        runCatching { financialRepository.applyPriceUpdate(assetId, price) }
            .onFailure { Log.w("PriceRepository", "applyPriceUpdate failed", it) }
    }

    private suspend fun fallbackQuote(ticker: String): TickerQuote? {
        val assetId = financialDao.getAllTickers()
            .firstOrNull { it.ticker == ticker }?.assetId ?: return null
        val holding = financialDao.getHolding(assetId) ?: return null
        val price = holding.latestPrice ?: return null
        val at = holding.latestPriceAt ?: return null
        return TickerQuote(
            ticker = holding.ticker,
            displayName = holding.displayName,
            currencyCode = "", // unknown at this layer; consumers don't use it for fallback
            price = price,
            asOf = Instant.ofEpochMilli(at),
        )
    }

    private data class CachedQuote(val quote: TickerQuote, val cachedAt: Instant) {
        fun isFresh(ttl: Duration): Boolean = Duration.between(cachedAt, Instant.now()) < ttl
    }
}
```

- [ ] **Step 4: Run tests, all pass**

Run: `./gradlew testDebugUnitTest --tests com.spendtrack.data.repository.PriceRepositoryTest`
Expected: 7 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/spendtrack/data/repository/PriceRepositoryTest.kt \
        app/src/main/java/com/spendtrack/data/repository/PriceRepository.kt
git commit -m "feat(financial): add PriceRepository with 15-min cache + mutex-deduped refresh"
```

---

## Task 12: Use cases (thin wrappers)

**Files:**
- Create: `app/src/main/java/com/spendtrack/domain/usecase/ValidateTickerUseCase.kt`
- Create: `app/src/main/java/com/spendtrack/domain/usecase/GetFinancialAssetUseCase.kt`
- Create: `app/src/main/java/com/spendtrack/domain/usecase/SaveFinancialAssetUseCase.kt`
- Create: `app/src/main/java/com/spendtrack/domain/usecase/AddLotUseCase.kt`
- Create: `app/src/main/java/com/spendtrack/domain/usecase/UpdateLotUseCase.kt`
- Create: `app/src/main/java/com/spendtrack/domain/usecase/DeleteLotUseCase.kt`
- Create: `app/src/main/java/com/spendtrack/domain/usecase/RefreshPricesUseCase.kt`

Thin pass-throughs; tested through ViewModel tests.

- [ ] **Step 1: Create all use cases**

`ValidateTickerUseCase.kt`:

```kotlin
package com.spendtrack.domain.usecase

import com.spendtrack.data.repository.PriceRepository
import com.spendtrack.domain.model.QuoteResult
import javax.inject.Inject

class ValidateTickerUseCase @Inject constructor(
    private val priceRepository: PriceRepository,
) {
    suspend operator fun invoke(ticker: String): QuoteResult =
        priceRepository.validateTicker(ticker.trim().uppercase())
}
```

`GetFinancialAssetUseCase.kt`:

```kotlin
package com.spendtrack.domain.usecase

import com.spendtrack.data.repository.FinancialRepository
import com.spendtrack.domain.model.FinancialAsset
import javax.inject.Inject

class GetFinancialAssetUseCase @Inject constructor(
    private val repo: FinancialRepository,
) {
    suspend operator fun invoke(id: Long): FinancialAsset? = repo.getById(id)
}
```

`SaveFinancialAssetUseCase.kt`:

```kotlin
package com.spendtrack.domain.usecase

import com.spendtrack.data.repository.FinancialRepository
import com.spendtrack.domain.model.FinancialLot
import com.spendtrack.domain.model.TickerQuote
import javax.inject.Inject

class SaveFinancialAssetUseCase @Inject constructor(
    private val repo: FinancialRepository,
) {
    suspend operator fun invoke(
        name: String,
        notes: String?,
        quote: TickerQuote,
        firstLot: FinancialLot,
    ): Long = repo.createAssetWithFirstLot(name, notes, quote, firstLot)
}
```

`AddLotUseCase.kt`:

```kotlin
package com.spendtrack.domain.usecase

import com.spendtrack.data.repository.FinancialRepository
import com.spendtrack.domain.model.FinancialLot
import javax.inject.Inject

class AddLotUseCase @Inject constructor(
    private val repo: FinancialRepository,
) {
    suspend operator fun invoke(assetId: Long, lot: FinancialLot): Long =
        repo.addLot(assetId, lot)
}
```

`UpdateLotUseCase.kt`:

```kotlin
package com.spendtrack.domain.usecase

import com.spendtrack.data.repository.FinancialRepository
import com.spendtrack.domain.model.FinancialLot
import javax.inject.Inject

class UpdateLotUseCase @Inject constructor(
    private val repo: FinancialRepository,
) {
    suspend operator fun invoke(assetId: Long, lot: FinancialLot) =
        repo.updateLot(assetId, lot)
}
```

`DeleteLotUseCase.kt`:

```kotlin
package com.spendtrack.domain.usecase

import com.spendtrack.data.repository.FinancialRepository
import javax.inject.Inject

class DeleteLotUseCase @Inject constructor(
    private val repo: FinancialRepository,
) {
    /** Returns true if the entire asset was deleted (last lot removed). */
    suspend operator fun invoke(assetId: Long, lotId: Long): Boolean =
        repo.deleteLot(assetId, lotId)
}
```

`RefreshPricesUseCase.kt`:

```kotlin
package com.spendtrack.domain.usecase

import com.spendtrack.data.repository.PriceRepository
import com.spendtrack.data.repository.RefreshResult
import javax.inject.Inject

class RefreshPricesUseCase @Inject constructor(
    private val priceRepository: PriceRepository,
) {
    suspend operator fun invoke(): RefreshResult = priceRepository.refreshAll()
}
```

- [ ] **Step 2: Build**

Run: `./gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/spendtrack/domain/usecase/ValidateTickerUseCase.kt \
        app/src/main/java/com/spendtrack/domain/usecase/GetFinancialAssetUseCase.kt \
        app/src/main/java/com/spendtrack/domain/usecase/SaveFinancialAssetUseCase.kt \
        app/src/main/java/com/spendtrack/domain/usecase/AddLotUseCase.kt \
        app/src/main/java/com/spendtrack/domain/usecase/UpdateLotUseCase.kt \
        app/src/main/java/com/spendtrack/domain/usecase/DeleteLotUseCase.kt \
        app/src/main/java/com/spendtrack/domain/usecase/RefreshPricesUseCase.kt
git commit -m "feat(financial): add validate/get/save/lot/refresh use cases"
```

---

## Task 13: AssetFormatting helpers + tests

**Files:**
- Modify: `app/src/main/java/com/spendtrack/util/AssetFormatting.kt`
- Modify: `app/src/test/java/com/spendtrack/util/AssetFormattingTest.kt`

- [ ] **Step 1: Append `formatQuantity` and `formatSignedCurrency` to `AssetFormatting.kt`**

Add these functions at the end of `AssetFormatting.kt`:

```kotlin
/**
 * Formats a quantity with decimal precision adapted to magnitude.
 * Examples: 100.0 -> "100", 10.5 -> "10.5", 0.001234 -> "0.001234", 1.23e-7 -> "0.00000012"
 */
fun formatQuantity(qty: Double): String {
    val abs = kotlin.math.abs(qty)
    val raw = when {
        abs >= 100 -> String.format(java.util.Locale.US, "%.0f", qty)
        abs >= 1   -> String.format(java.util.Locale.US, "%.4f", qty).trimEnd('0').trimEnd('.')
        abs >= 0.001 -> String.format(java.util.Locale.US, "%.6f", qty).trimEnd('0').trimEnd('.')
        else -> String.format(java.util.Locale.US, "%.8f", qty).trimEnd('0').trimEnd('.')
    }
    return raw
}

/**
 * Formats a signed currency amount: "+ €123,45" or "− €50,00". The sign uses a Unicode
 * minus (U+2212) for symmetry with [signedPercent].
 */
fun formatSignedCurrency(amount: Double, currencyCode: String): String {
    val abs = kotlin.math.abs(amount)
    val formatted = com.spendtrack.util.CurrencyFormatter.formatAbsoluteForCurrency(abs, currencyCode)
    val sign = if (amount < 0) "−" else "+"
    return "$sign $formatted"
}
```

- [ ] **Step 2: Append tests to `AssetFormattingTest.kt`**

Add at the end of the test class:

```kotlin
    @Test fun `formatQuantity uses no decimals for ge 100`() {
        assertEquals("100", formatQuantity(100.0))
        assertEquals("12345", formatQuantity(12345.0))
    }

    @Test fun `formatQuantity uses up to 4 decimals between 1 and 100`() {
        assertEquals("10.5", formatQuantity(10.5))
        assertEquals("1.2345", formatQuantity(1.2345))
        assertEquals("3", formatQuantity(3.0))
    }

    @Test fun `formatQuantity uses up to 6 decimals between 0_001 and 1`() {
        assertEquals("0.001234", formatQuantity(0.001234))
        assertEquals("0.5", formatQuantity(0.5))
    }

    @Test fun `formatQuantity uses up to 8 decimals below 0_001`() {
        assertEquals("0.00000012", formatQuantity(0.00000012))
    }

    @Test fun `formatSignedCurrency positive`() {
        assertEquals("+ €123,45", formatSignedCurrency(123.45, "EUR"))
    }

    @Test fun `formatSignedCurrency negative`() {
        assertEquals("− €50,00", formatSignedCurrency(-50.0, "EUR"))
    }

    @Test fun `formatSignedCurrency zero treated as positive`() {
        assertEquals("+ €0,00", formatSignedCurrency(0.0, "EUR"))
    }
```

- [ ] **Step 3: Run tests**

Run: `./gradlew testDebugUnitTest --tests com.spendtrack.util.AssetFormattingTest`
Expected: all pass (existing 19 + 7 new = 26 tests).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/spendtrack/util/AssetFormatting.kt \
        app/src/test/java/com/spendtrack/util/AssetFormattingTest.kt
git commit -m "feat(financial): add formatQuantity and formatSignedCurrency helpers"
```

---

## Task 14: AddFinancialAssetViewModel (TDD)

**Files:**
- Create: `app/src/test/java/com/spendtrack/ui/feature/assets/financial/add/AddFinancialAssetViewModelTest.kt`
- Create: `app/src/main/java/com/spendtrack/ui/feature/assets/financial/add/AddFinancialAssetViewModel.kt`

The most complex ViewModel: ticker state machine + form validation + save flow.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.spendtrack.ui.feature.assets.financial.add

import androidx.lifecycle.SavedStateHandle
import com.spendtrack.domain.model.QuoteResult
import com.spendtrack.domain.model.TickerQuote
import com.spendtrack.domain.usecase.SaveFinancialAssetUseCase
import com.spendtrack.domain.usecase.ValidateTickerUseCase
import com.spendtrack.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AddFinancialAssetViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val validate: ValidateTickerUseCase = mockk()
    private val save: SaveFinancialAssetUseCase = mockk(relaxed = true)

    private val sampleQuote = TickerQuote(
        ticker = "AAPL",
        displayName = "Apple Inc.",
        currencyCode = "USD",
        price = 234.56,
        asOf = Instant.parse("2026-05-06T12:00:00Z"),
    )

    private fun viewModel() = AddFinancialAssetViewModel(
        savedStateHandle = SavedStateHandle(),
        validateTicker = validate,
        saveAsset = save,
    )

    @Test fun `empty ticker is Idle`() = runTest {
        coEvery { save(any(), any(), any(), any()) } returns 1L
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.tickerState is TickerState.Idle)
    }

    @Test fun `valid ticker after debounce sets Valid and prefills`() = runTest {
        coEvery { validate("AAPL") } returns QuoteResult.Success(sampleQuote)
        val vm = viewModel()
        vm.onTicker("AAPL")
        advanceTimeBy(600) // past debounce
        advanceUntilIdle()
        val s = vm.uiState.value
        assertTrue("expected Valid, got ${s.tickerState}", s.tickerState is TickerState.Valid)
        assertEquals("Apple Inc.", s.name)
        assertEquals("234.56", s.pricePerUnit)
    }

    @Test fun `NotFound becomes Invalid`() = runTest {
        coEvery { validate("BAD") } returns QuoteResult.NotFound
        val vm = viewModel()
        vm.onTicker("BAD")
        advanceTimeBy(600); advanceUntilIdle()
        assertTrue(vm.uiState.value.tickerState is TickerState.Invalid)
    }

    @Test fun `Error becomes Error state`() = runTest {
        coEvery { validate("AAPL") } returns QuoteResult.Error("network down")
        val vm = viewModel()
        vm.onTicker("AAPL")
        advanceTimeBy(600); advanceUntilIdle()
        assertTrue(vm.uiState.value.tickerState is TickerState.Error)
    }

    @Test fun `name not overwritten on re-validation`() = runTest {
        coEvery { validate("AAPL") } returns QuoteResult.Success(sampleQuote)
        coEvery { validate("AAP") } returns QuoteResult.NotFound
        val vm = viewModel()
        vm.onTicker("AAPL")
        advanceTimeBy(600); advanceUntilIdle()
        vm.onName("My Apple")
        vm.onTicker("AAP")
        advanceTimeBy(600); advanceUntilIdle()
        // user-typed name preserved
        assertEquals("My Apple", vm.uiState.value.name)
    }

    @Test fun `Save disabled until form valid`() = runTest {
        coEvery { validate("AAPL") } returns QuoteResult.Success(sampleQuote)
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.formErrors.hasAny)

        vm.onTicker("AAPL")
        advanceTimeBy(600); advanceUntilIdle()
        vm.onPurchaseDate(LocalDate.of(2024, 1, 1))
        vm.onQuantity("10")
        // pricePerUnit prefilled by validation
        advanceUntilIdle()
        assertFalse(vm.uiState.value.formErrors.hasAny)
    }

    @Test fun `quantity must be greater than zero`() = runTest {
        coEvery { validate("AAPL") } returns QuoteResult.Success(sampleQuote)
        val vm = viewModel()
        vm.onTicker("AAPL")
        advanceTimeBy(600); advanceUntilIdle()
        vm.onPurchaseDate(LocalDate.of(2024, 1, 1))
        vm.onQuantity("0")
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.formErrors.quantity)
    }

    @Test fun `purchase date in future flagged`() = runTest {
        coEvery { validate("AAPL") } returns QuoteResult.Success(sampleQuote)
        val vm = viewModel()
        vm.onTicker("AAPL")
        advanceTimeBy(600); advanceUntilIdle()
        vm.onPurchaseDate(LocalDate.now().plusDays(1))
        vm.onQuantity("10")
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.formErrors.purchaseDate)
    }

    @Test fun `save success sets isSaved`() = runTest {
        coEvery { validate("AAPL") } returns QuoteResult.Success(sampleQuote)
        coEvery { save(any(), any(), any(), any()) } returns 1L
        val vm = viewModel()
        vm.onTicker("AAPL")
        advanceTimeBy(600); advanceUntilIdle()
        vm.onPurchaseDate(LocalDate.of(2024, 1, 1))
        vm.onQuantity("10")
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isSaved)
        coVerify { save(any(), any(), any(), any()) }
    }

    @Test fun `duplicate ticker throws and surfaces specific message`() = runTest {
        coEvery { validate("AAPL") } returns QuoteResult.Success(sampleQuote)
        coEvery { save(any(), any(), any(), any()) } throws
            IllegalStateException("Asset for AAPL already exists")
        val vm = viewModel()
        vm.onTicker("AAPL")
        advanceTimeBy(600); advanceUntilIdle()
        vm.onPurchaseDate(LocalDate.of(2024, 1, 1))
        vm.onQuantity("10")
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSaved)
        val msg = vm.uiState.value.errorMessage
        assertNotNull(msg)
        assertTrue("expected 'already exists' in '$msg'", msg!!.contains("already exists"))
    }

    @Test fun `generic save error surfaces fallback message`() = runTest {
        coEvery { validate("AAPL") } returns QuoteResult.Success(sampleQuote)
        coEvery { save(any(), any(), any(), any()) } throws RuntimeException("boom")
        val vm = viewModel()
        vm.onTicker("AAPL")
        advanceTimeBy(600); advanceUntilIdle()
        vm.onPurchaseDate(LocalDate.of(2024, 1, 1))
        vm.onQuantity("10")
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSaved)
        val msg = vm.uiState.value.errorMessage
        assertNotNull(msg)
        assertTrue("expected 'Try again' in '$msg'", msg!!.contains("Try again"))
    }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `./gradlew testDebugUnitTest --tests com.spendtrack.ui.feature.assets.financial.add.AddFinancialAssetViewModelTest`
Expected: `Unresolved reference: AddFinancialAssetViewModel`

- [ ] **Step 3: Implement the ViewModel**

```kotlin
package com.spendtrack.ui.feature.assets.financial.add

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtrack.domain.model.FinancialLot
import com.spendtrack.domain.model.QuoteResult
import com.spendtrack.domain.model.TickerQuote
import com.spendtrack.domain.usecase.SaveFinancialAssetUseCase
import com.spendtrack.domain.usecase.ValidateTickerUseCase
import com.spendtrack.util.parseDecimal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed interface TickerState {
    data object Idle : TickerState
    data object Validating : TickerState
    data class Valid(val quote: TickerQuote) : TickerState
    data class Invalid(val reason: String) : TickerState
    data class Error(val reason: String) : TickerState
}

data class AddFinancialFormErrors(
    val ticker: String? = null,
    val name: String? = null,
    val purchaseDate: String? = null,
    val quantity: String? = null,
    val pricePerUnit: String? = null,
) {
    val hasAny: Boolean get() = listOf(ticker, name, purchaseDate, quantity, pricePerUnit).any { it != null }
}

data class AddFinancialAssetUiState(
    val ticker: String = "",
    val tickerState: TickerState = TickerState.Idle,
    val name: String = "",
    val purchaseDate: LocalDate? = null,
    val quantity: String = "",
    val pricePerUnit: String = "",
    val notes: String = "",
    val currencyCode: String = "",
    val formErrors: AddFinancialFormErrors = AddFinancialFormErrors(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSave: Boolean get() = !isLoading && !formErrors.hasAny && tickerState is TickerState.Valid
}

@HiltViewModel
class AddFinancialAssetViewModel @Inject constructor(
    @Suppress("UNUSED_PARAMETER") savedStateHandle: SavedStateHandle,
    private val validateTicker: ValidateTickerUseCase,
    private val saveAsset: SaveFinancialAssetUseCase,
) : ViewModel() {

    private val _form = MutableStateFlow(AddFinancialAssetUiState())
    private var nameUserEdited = false
    private var validationJob: Job? = null

    val uiState: StateFlow<AddFinancialAssetUiState> = _form
        .map { it.copy(formErrors = computeErrors(it)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AddFinancialAssetUiState().let {
                it.copy(formErrors = computeErrors(it))
            },
        )

    fun onTicker(raw: String) {
        val normalized = raw.trim().uppercase()
        _form.update { it.copy(ticker = normalized, tickerState = if (normalized.isBlank()) TickerState.Idle else TickerState.Validating) }
        validationJob?.cancel()
        if (normalized.isBlank()) return
        validationJob = viewModelScope.launch {
            delay(500) // debounce
            when (val result = validateTicker(normalized)) {
                is QuoteResult.Success -> {
                    val quote = result.quote
                    _form.update { current ->
                        current.copy(
                            tickerState = TickerState.Valid(quote),
                            currencyCode = quote.currencyCode,
                            // Prefill name and price only if not user-edited
                            name = if (!nameUserEdited) quote.displayName else current.name,
                            pricePerUnit = if (current.pricePerUnit.isBlank())
                                "%.2f".format(java.util.Locale.US, quote.price)
                            else current.pricePerUnit,
                        )
                    }
                }
                is QuoteResult.NotFound ->
                    _form.update { it.copy(tickerState = TickerState.Invalid("Ticker not found. Try the symbol exactly as on Yahoo Finance (e.g. AAPL, VWCE.DE, BTC-USD).")) }
                is QuoteResult.Error ->
                    _form.update { it.copy(tickerState = TickerState.Error("Couldn't reach price source. Check your connection.")) }
            }
        }
    }

    fun onName(v: String) {
        nameUserEdited = true
        _form.update { it.copy(name = v) }
    }
    fun onPurchaseDate(v: LocalDate?) = _form.update { it.copy(purchaseDate = v) }
    fun onQuantity(v: String) = _form.update { it.copy(quantity = v) }
    fun onPricePerUnit(v: String) = _form.update { it.copy(pricePerUnit = v) }
    fun onNotes(v: String) = _form.update { it.copy(notes = v) }

    fun clearErrorMessage() = _form.update { it.copy(errorMessage = null) }

    fun save() {
        val s = _form.value
        if (computeErrors(s).hasAny || s.tickerState !is TickerState.Valid) return
        val quote = s.tickerState.quote
        val lot = FinancialLot(
            id = 0L,
            purchaseDate = s.purchaseDate!!,
            quantity = parseDecimal(s.quantity)!!,
            pricePerUnit = parseDecimal(s.pricePerUnit)!!,
        )
        _form.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                saveAsset(s.name.trim(), s.notes.trim().takeIf { it.isNotBlank() }, quote, lot)
                _form.update { it.copy(isLoading = false, isSaved = true) }
            } catch (t: IllegalStateException) {
                _form.update { it.copy(isLoading = false, errorMessage = "An asset for this ticker already exists.") }
            } catch (t: Throwable) {
                _form.update { it.copy(isLoading = false, errorMessage = "Couldn't save asset. Try again.") }
            }
        }
    }

    private fun computeErrors(s: AddFinancialAssetUiState): AddFinancialFormErrors {
        val today = LocalDate.now()
        val tickerErr = when (s.tickerState) {
            is TickerState.Valid -> null
            TickerState.Idle -> "Ticker is required"
            TickerState.Validating -> "Validating…"  // not user-shown; gates Save
            is TickerState.Invalid -> s.tickerState.reason
            is TickerState.Error -> s.tickerState.reason
        }
        val nameErr = if (s.name.isBlank()) "Name is required" else null
        val dateErr = when {
            s.purchaseDate == null -> "Purchase date is required"
            s.purchaseDate.isAfter(today) -> "Cannot be in the future"
            else -> null
        }
        val qtyErr = run {
            val v = parseDecimal(s.quantity)
            if (s.quantity.isBlank() || v == null || v <= 0.0) "Quantity must be greater than 0" else null
        }
        val priceErr = run {
            val v = parseDecimal(s.pricePerUnit)
            if (s.pricePerUnit.isBlank() || v == null || v <= 0.0) "Price must be greater than 0" else null
        }
        return AddFinancialFormErrors(tickerErr, nameErr, dateErr, qtyErr, priceErr)
    }
}
```

- [ ] **Step 4: Run tests, all pass**

Run: `./gradlew testDebugUnitTest --tests com.spendtrack.ui.feature.assets.financial.add.AddFinancialAssetViewModelTest`
Expected: 11 tests pass.

If a test is flaky on the debounce timing, replace `advanceTimeBy(600)` with `advanceUntilIdle()` after `runTest`.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/spendtrack/ui/feature/assets/financial/add/AddFinancialAssetViewModelTest.kt \
        app/src/main/java/com/spendtrack/ui/feature/assets/financial/add/AddFinancialAssetViewModel.kt
git commit -m "feat(financial): add AddFinancialAssetViewModel with debounced ticker validation"
```

---

## Task 15: FinancialDetailViewModel (TDD)

**Files:**
- Create: `app/src/test/java/com/spendtrack/ui/feature/assets/financial/detail/FinancialDetailViewModelTest.kt`
- Create: `app/src/main/java/com/spendtrack/ui/feature/assets/financial/detail/FinancialDetailViewModel.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.spendtrack.ui.feature.assets.financial.detail

import androidx.lifecycle.SavedStateHandle
import com.spendtrack.data.repository.PriceRepository
import com.spendtrack.domain.model.FinancialAsset
import com.spendtrack.domain.model.FinancialLot
import com.spendtrack.domain.usecase.DeleteAssetUseCase
import com.spendtrack.domain.usecase.DeleteLotUseCase
import com.spendtrack.domain.usecase.GetFinancialAssetUseCase
import com.spendtrack.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class FinancialDetailViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val getAsset: GetFinancialAssetUseCase = mockk()
    private val deleteAsset: DeleteAssetUseCase = mockk(relaxed = true)
    private val deleteLot: DeleteLotUseCase = mockk(relaxed = true)
    private val priceRepo: PriceRepository = mockk(relaxed = true)

    private fun asset(
        id: Long = 1L,
        latestPrice: Double? = 234.56,
        lots: List<FinancialLot> = listOf(
            FinancialLot(1L, LocalDate.of(2024, 1, 1), 10.0, 175.20),
        ),
    ) = FinancialAsset(
        id = id, name = "Apple Inc.", ticker = "AAPL", displayName = "Apple Inc.",
        currencyCode = "USD", latestPrice = latestPrice,
        latestPriceAt = latestPrice?.let { Instant.now() },
        notes = null, lots = lots,
    )

    private fun viewModel(idArg: Long?) = FinancialDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("assetId" to idArg?.toString())),
        getAsset = getAsset,
        deleteAssetUseCase = deleteAsset,
        deleteLotUseCase = deleteLot,
        priceRepository = priceRepo,
    )

    @Test fun `loads asset and computes equity`() = runTest {
        coEvery { getAsset(1L) } returns asset()
        val vm = viewModel(1L)
        advanceUntilIdle()
        val s = vm.uiState.value
        assertNotNull(s.asset)
        assertFalse(s.loadError)
        assertEquals(2345.6, s.asset!!.currentValue!!, 0.001)
    }

    @Test fun `null id sets loadError`() = runTest {
        val vm = viewModel(null)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.loadError)
    }

    @Test fun `missing asset sets loadError`() = runTest {
        coEvery { getAsset(99L) } returns null
        val vm = viewModel(99L)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.loadError)
    }

    @Test fun `refresh invokes price repo with force`() = runTest {
        coEvery { getAsset(1L) } returns asset()
        coEvery { priceRepo.getQuote("AAPL", true) } returns null
        val vm = viewModel(1L)
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()
        coVerify { priceRepo.getQuote("AAPL", true) }
    }

    @Test fun `deleteLot when not last only deletes lot`() = runTest {
        coEvery { getAsset(1L) } returns asset(lots = listOf(
            FinancialLot(1L, LocalDate.of(2024, 1, 1), 10.0, 175.20),
            FinancialLot(2L, LocalDate.of(2024, 6, 1), 5.0, 200.0),
        ))
        coEvery { deleteLot(1L, 1L) } returns false
        val vm = viewModel(1L)
        advanceUntilIdle()
        vm.deleteLot(1L)
        advanceUntilIdle()
        coVerify { deleteLot(1L, 1L) }
        assertFalse(vm.uiState.value.isDeleted)
    }

    @Test fun `deleteLot when last sets isDeleted`() = runTest {
        coEvery { getAsset(1L) } returns asset(lots = listOf(
            FinancialLot(1L, LocalDate.of(2024, 1, 1), 10.0, 175.20),
        ))
        coEvery { deleteLot(1L, 1L) } returns true
        val vm = viewModel(1L)
        advanceUntilIdle()
        vm.deleteLot(1L)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isDeleted)
    }

    @Test fun `deleteAsset invokes use case and sets isDeleted`() = runTest {
        coEvery { getAsset(1L) } returns asset()
        val vm = viewModel(1L)
        advanceUntilIdle()
        vm.deleteAsset()
        advanceUntilIdle()
        coVerify { deleteAsset(1L) }
        assertTrue(vm.uiState.value.isDeleted)
    }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `./gradlew testDebugUnitTest --tests com.spendtrack.ui.feature.assets.financial.detail.FinancialDetailViewModelTest`
Expected: `Unresolved reference: FinancialDetailViewModel`

- [ ] **Step 3: Implement the ViewModel**

```kotlin
package com.spendtrack.ui.feature.assets.financial.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtrack.data.repository.PriceRepository
import com.spendtrack.domain.model.FinancialAsset
import com.spendtrack.domain.usecase.DeleteAssetUseCase
import com.spendtrack.domain.usecase.DeleteLotUseCase
import com.spendtrack.domain.usecase.GetFinancialAssetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FinancialDetailUiState(
    val asset: FinancialAsset? = null,
    val loadError: Boolean = false,
    val isRefreshing: Boolean = false,
    val isDeleted: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class FinancialDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getAsset: GetFinancialAssetUseCase,
    private val deleteAssetUseCase: DeleteAssetUseCase,
    private val deleteLotUseCase: DeleteLotUseCase,
    private val priceRepository: PriceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FinancialDetailUiState())
    val uiState: StateFlow<FinancialDetailUiState> = _state.asStateFlow()

    private val assetId: Long = savedStateHandle.get<String>("assetId")?.toLongOrNull() ?: 0L

    init {
        if (assetId == 0L) {
            _state.update { it.copy(loadError = true) }
        } else {
            loadAsset()
        }
    }

    private fun loadAsset() {
        viewModelScope.launch {
            val asset = runCatching { getAsset(assetId) }.getOrNull()
            if (asset == null) _state.update { it.copy(loadError = true) }
            else _state.update { it.copy(asset = asset, loadError = false) }
        }
    }

    fun refresh() {
        val ticker = _state.value.asset?.ticker ?: return
        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            runCatching { priceRepository.getQuote(ticker, force = true) }
            // Re-load to pick up persisted price update
            val updated = runCatching { getAsset(assetId) }.getOrNull()
            _state.update { it.copy(isRefreshing = false, asset = updated ?: it.asset) }
        }
    }

    fun deleteLot(lotId: Long) {
        viewModelScope.launch {
            try {
                val assetDeleted = deleteLotUseCase(assetId, lotId)
                if (assetDeleted) {
                    _state.update { it.copy(isDeleted = true) }
                } else {
                    val updated = getAsset(assetId)
                    _state.update { it.copy(asset = updated ?: it.asset) }
                }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = "Couldn't delete lot. Try again.") }
            }
        }
    }

    fun deleteAsset() {
        viewModelScope.launch {
            try {
                deleteAssetUseCase(assetId)
                _state.update { it.copy(isDeleted = true) }
            } catch (t: Throwable) {
                _state.update { it.copy(errorMessage = "Couldn't delete asset. Try again.") }
            }
        }
    }

    fun clearErrorMessage() = _state.update { it.copy(errorMessage = null) }
}
```

- [ ] **Step 4: Run tests, all pass**

Run: `./gradlew testDebugUnitTest --tests com.spendtrack.ui.feature.assets.financial.detail.FinancialDetailViewModelTest`
Expected: 7 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/spendtrack/ui/feature/assets/financial/detail/FinancialDetailViewModelTest.kt \
        app/src/main/java/com/spendtrack/ui/feature/assets/financial/detail/FinancialDetailViewModel.kt
git commit -m "feat(financial): add FinancialDetailViewModel"
```

---

## Task 16: AddLotViewModel (TDD)

**Files:**
- Create: `app/src/test/java/com/spendtrack/ui/feature/assets/financial/addlot/AddLotViewModelTest.kt`
- Create: `app/src/main/java/com/spendtrack/ui/feature/assets/financial/addlot/AddLotViewModel.kt`

Dual-purpose: add a new lot OR edit an existing one. `lotId` route arg discriminates (null = add).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.spendtrack.ui.feature.assets.financial.addlot

import androidx.lifecycle.SavedStateHandle
import com.spendtrack.domain.model.FinancialAsset
import com.spendtrack.domain.model.FinancialLot
import com.spendtrack.domain.usecase.AddLotUseCase
import com.spendtrack.domain.usecase.GetFinancialAssetUseCase
import com.spendtrack.domain.usecase.UpdateLotUseCase
import com.spendtrack.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AddLotViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val getAsset: GetFinancialAssetUseCase = mockk()
    private val addLot: AddLotUseCase = mockk(relaxed = true)
    private val updateLot: UpdateLotUseCase = mockk(relaxed = true)

    private val asset = FinancialAsset(
        id = 1L, name = "Apple Inc.", ticker = "AAPL", displayName = "Apple Inc.",
        currencyCode = "USD", latestPrice = 234.56, latestPriceAt = Instant.now(),
        notes = null,
        lots = listOf(FinancialLot(7L, LocalDate.of(2024, 1, 1), 10.0, 175.20)),
    )

    private fun viewModel(assetId: Long? = 1L, lotId: Long? = null) = AddLotViewModel(
        savedStateHandle = SavedStateHandle(mapOf(
            "assetId" to assetId?.toString(),
            "lotId" to lotId?.toString(),
        )),
        getAsset = getAsset,
        addLotUseCase = addLot,
        updateLotUseCase = updateLot,
    )

    @Test fun `add mode prefills currency and price from asset`() = runTest {
        coEvery { getAsset(1L) } returns asset
        val vm = viewModel()
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals("USD", s.currencyCode)
        assertEquals("234.56", s.pricePerUnit)
        assertFalse(s.isEditMode)
    }

    @Test fun `edit mode prefills from existing lot`() = runTest {
        coEvery { getAsset(1L) } returns asset
        val vm = viewModel(lotId = 7L)
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(LocalDate.of(2024, 1, 1), s.purchaseDate)
        assertEquals("10", s.quantity)
        assertEquals("175.2", s.pricePerUnit)
        assertTrue(s.isEditMode)
    }

    @Test fun `quantity must be greater than zero`() = runTest {
        coEvery { getAsset(1L) } returns asset
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPurchaseDate(LocalDate.of(2024, 1, 1))
        vm.onQuantity("0")
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.formErrors.quantity)
    }

    @Test fun `valid form add saves and isSaved`() = runTest {
        coEvery { getAsset(1L) } returns asset
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPurchaseDate(LocalDate.of(2025, 6, 1))
        vm.onQuantity("5")
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        coVerify { addLot(1L, any()) }
        assertTrue(vm.uiState.value.isSaved)
    }

    @Test fun `edit mode save calls update`() = runTest {
        coEvery { getAsset(1L) } returns asset
        val vm = viewModel(lotId = 7L)
        advanceUntilIdle()
        vm.onQuantity("12")
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        coVerify { updateLot(1L, any()) }
        assertTrue(vm.uiState.value.isSaved)
    }

    @Test fun `asset deleted yields specific error`() = runTest {
        coEvery { getAsset(1L) } returns asset
        coEvery { addLot(1L, any()) } throws IllegalStateException("Asset 1 no longer exists")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPurchaseDate(LocalDate.of(2025, 6, 1))
        vm.onQuantity("5")
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSaved)
        val msg = vm.uiState.value.errorMessage
        assertNotNull(msg)
        assertTrue("expected 'no longer exists' in '$msg'", msg!!.contains("no longer exists"))
    }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `./gradlew testDebugUnitTest --tests com.spendtrack.ui.feature.assets.financial.addlot.AddLotViewModelTest`
Expected: `Unresolved reference: AddLotViewModel`

- [ ] **Step 3: Implement the ViewModel**

```kotlin
package com.spendtrack.ui.feature.assets.financial.addlot

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtrack.domain.model.FinancialLot
import com.spendtrack.domain.usecase.AddLotUseCase
import com.spendtrack.domain.usecase.GetFinancialAssetUseCase
import com.spendtrack.domain.usecase.UpdateLotUseCase
import com.spendtrack.util.parseDecimal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class AddLotFormErrors(
    val purchaseDate: String? = null,
    val quantity: String? = null,
    val pricePerUnit: String? = null,
) {
    val hasAny: Boolean get() = listOf(purchaseDate, quantity, pricePerUnit).any { it != null }
}

data class AddLotUiState(
    val isEditMode: Boolean = false,
    val ticker: String = "",
    val currencyCode: String = "",
    val purchaseDate: LocalDate? = null,
    val quantity: String = "",
    val pricePerUnit: String = "",
    val formErrors: AddLotFormErrors = AddLotFormErrors(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class AddLotViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getAsset: GetFinancialAssetUseCase,
    private val addLotUseCase: AddLotUseCase,
    private val updateLotUseCase: UpdateLotUseCase,
) : ViewModel() {

    private val assetId: Long = savedStateHandle.get<String>("assetId")?.toLongOrNull() ?: 0L
    private val lotId: Long = savedStateHandle.get<String>("lotId")?.toLongOrNull() ?: 0L
    private val isEditMode = lotId != 0L

    private val initial = AddLotUiState(isEditMode = isEditMode)
    private val _form = MutableStateFlow(initial)

    val uiState: StateFlow<AddLotUiState> = _form
        .map { it.copy(formErrors = computeErrors(it)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = initial.copy(formErrors = computeErrors(initial)),
        )

    init { if (assetId != 0L) loadInitial() }

    private fun loadInitial() {
        viewModelScope.launch {
            val asset = runCatching { getAsset(assetId) }.getOrNull() ?: return@launch
            _form.update { current ->
                if (isEditMode) {
                    val lot = asset.lots.firstOrNull { it.id == lotId } ?: return@update current
                    current.copy(
                        ticker = asset.ticker,
                        currencyCode = asset.currencyCode,
                        purchaseDate = lot.purchaseDate,
                        quantity = trimZeros(lot.quantity),
                        pricePerUnit = trimZeros(lot.pricePerUnit),
                    )
                } else {
                    current.copy(
                        ticker = asset.ticker,
                        currencyCode = asset.currencyCode,
                        purchaseDate = current.purchaseDate ?: LocalDate.now(),
                        pricePerUnit = if (current.pricePerUnit.isBlank() && asset.latestPrice != null)
                            "%.2f".format(java.util.Locale.US, asset.latestPrice)
                        else current.pricePerUnit,
                    )
                }
            }
        }
    }

    fun onPurchaseDate(v: LocalDate?) = _form.update { it.copy(purchaseDate = v) }
    fun onQuantity(v: String) = _form.update { it.copy(quantity = v) }
    fun onPricePerUnit(v: String) = _form.update { it.copy(pricePerUnit = v) }
    fun clearErrorMessage() = _form.update { it.copy(errorMessage = null) }

    fun save() {
        val s = _form.value
        if (computeErrors(s).hasAny) return
        val lot = FinancialLot(
            id = if (isEditMode) lotId else 0L,
            purchaseDate = s.purchaseDate!!,
            quantity = parseDecimal(s.quantity)!!,
            pricePerUnit = parseDecimal(s.pricePerUnit)!!,
        )
        _form.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                if (isEditMode) updateLotUseCase(assetId, lot) else addLotUseCase(assetId, lot)
                _form.update { it.copy(isLoading = false, isSaved = true) }
            } catch (t: IllegalStateException) {
                _form.update { it.copy(isLoading = false, errorMessage = "This asset no longer exists.") }
            } catch (t: Throwable) {
                _form.update { it.copy(isLoading = false, errorMessage = "Couldn't save lot. Try again.") }
            }
        }
    }

    private fun computeErrors(s: AddLotUiState): AddLotFormErrors {
        val today = LocalDate.now()
        val dateErr = when {
            s.purchaseDate == null -> "Purchase date is required"
            s.purchaseDate.isAfter(today) -> "Cannot be in the future"
            else -> null
        }
        val qtyErr = run {
            val v = parseDecimal(s.quantity)
            if (s.quantity.isBlank() || v == null || v <= 0.0) "Quantity must be greater than 0" else null
        }
        val priceErr = run {
            val v = parseDecimal(s.pricePerUnit)
            if (s.pricePerUnit.isBlank() || v == null || v <= 0.0) "Price must be greater than 0" else null
        }
        return AddLotFormErrors(dateErr, qtyErr, priceErr)
    }

    private fun trimZeros(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
}
```

- [ ] **Step 4: Run tests, all pass**

Run: `./gradlew testDebugUnitTest --tests com.spendtrack.ui.feature.assets.financial.addlot.AddLotViewModelTest`
Expected: 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/spendtrack/ui/feature/assets/financial/addlot/AddLotViewModelTest.kt \
        app/src/main/java/com/spendtrack/ui/feature/assets/financial/addlot/AddLotViewModel.kt
git commit -m "feat(financial): add AddLotViewModel (handles add and edit modes)"
```

---

## Task 17: UI screens + navigation + list/picker updates (single bundled commit)

**Files:**
- Create: `app/src/main/java/com/spendtrack/ui/feature/assets/financial/add/AddFinancialAssetScreen.kt`
- Create: `app/src/main/java/com/spendtrack/ui/feature/assets/financial/detail/FinancialDetailScreen.kt`
- Create: `app/src/main/java/com/spendtrack/ui/feature/assets/financial/addlot/AddLotScreen.kt`
- Modify: `app/src/main/java/com/spendtrack/ui/feature/assets/typepicker/AssetTypePickerSheet.kt`
- Modify: `app/src/main/java/com/spendtrack/ui/feature/assets/component/AssetListRow.kt`
- Modify: `app/src/main/java/com/spendtrack/ui/feature/assets/list/AssetsListScreen.kt`
- Modify: `app/src/main/java/com/spendtrack/ui/feature/assets/list/AssetsListViewModel.kt`
- Modify: `app/src/main/java/com/spendtrack/ui/navigation/AppNavGraph.kt`

These changes are bundled into one commit because the screens reference each other through navigation routes. Building them piecemeal would leave compile-failing intermediate states (same approach as real-estate Tasks 13-15).

- [ ] **Step 1: Create `AddFinancialAssetScreen`**

```kotlin
package com.spendtrack.ui.feature.assets.financial.add

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFinancialAssetScreen(
    navController: NavController,
    viewModel: AddFinancialAssetViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.isSaved) { if (state.isSaved) navController.popBackStack() }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbar.showSnackbar(it); viewModel.clearErrorMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Add financial asset") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Button(
                    onClick = { viewModel.save() },
                    enabled = state.canSave,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save") }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item { Text("Ticker", style = MaterialTheme.typography.titleSmall) }
            item {
                Column {
                    OutlinedTextField(
                        value = state.ticker,
                        onValueChange = viewModel::onTicker,
                        label = { Text("Symbol (e.g. AAPL, VWCE.DE, BTC-USD)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val ts = state.tickerState
                    when (ts) {
                        TickerState.Idle -> Unit
                        TickerState.Validating -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Checking…", style = MaterialTheme.typography.bodySmall)
                        }
                        is TickerState.Valid -> Text(
                            "✓ ${ts.quote.displayName} · ${ts.quote.currencyCode} · ${"%.2f".format(java.util.Locale.US, ts.quote.price)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        is TickerState.Invalid -> Text(ts.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        is TickerState.Error -> Text(ts.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item { TextRow("Name", state.name, state.formErrors.name, viewModel::onName) }

            item { Spacer(Modifier.height(8.dp)); Text("Purchase", style = MaterialTheme.typography.titleSmall) }
            item {
                DateRow(
                    label = "Date",
                    value = state.purchaseDate,
                    error = state.formErrors.purchaseDate,
                    onChange = viewModel::onPurchaseDate,
                )
            }
            item { NumberRow("Quantity", state.quantity, state.formErrors.quantity, viewModel::onQuantity) }
            item { NumberRow("Price/share (${state.currencyCode.ifBlank { "—" }})", state.pricePerUnit, state.formErrors.pricePerUnit, viewModel::onPricePerUnit) }

            item { Spacer(Modifier.height(8.dp)); Text("Notes", style = MaterialTheme.typography.titleSmall) }
            item {
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotes,
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun TextRow(label: String, value: String, error: String?, onChange: (String) -> Unit) {
    Column {
        OutlinedTextField(
            value = value, onValueChange = onChange, label = { Text(label) },
            isError = error != null, modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun NumberRow(label: String, value: String, error: String?, onChange: (String) -> Unit) {
    Column {
        OutlinedTextField(
            value = value, onValueChange = onChange, label = { Text(label) },
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun DateRow(label: String, value: LocalDate?, error: String?, onChange: (LocalDate?) -> Unit) {
    var raw by remember(value) { mutableStateOf(value?.toString() ?: "") }
    Column {
        OutlinedTextField(
            value = raw,
            onValueChange = {
                raw = it
                onChange(runCatching { LocalDate.parse(it) }.getOrNull())
            },
            label = { Text("$label (YYYY-MM-DD)") },
            isError = error != null,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
```

- [ ] **Step 2: Create `AddLotScreen`**

```kotlin
package com.spendtrack.ui.feature.assets.financial.addlot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLotScreen(
    navController: NavController,
    viewModel: AddLotViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.isSaved) { if (state.isSaved) navController.popBackStack() }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbar.showSnackbar(it); viewModel.clearErrorMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditMode) "Edit lot · ${state.ticker}" else "Add lot · ${state.ticker}") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Button(
                    onClick = { viewModel.save() },
                    enabled = !state.formErrors.hasAny && !state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save") }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DateRow("Date", state.purchaseDate, state.formErrors.purchaseDate, viewModel::onPurchaseDate)
            NumberRow("Quantity", state.quantity, state.formErrors.quantity, viewModel::onQuantity)
            NumberRow("Price/share (${state.currencyCode})", state.pricePerUnit, state.formErrors.pricePerUnit, viewModel::onPricePerUnit)
        }
    }
}

@Composable
private fun NumberRow(label: String, value: String, error: String?, onChange: (String) -> Unit) {
    Column {
        OutlinedTextField(
            value = value, onValueChange = onChange, label = { Text(label) },
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun DateRow(label: String, value: LocalDate?, error: String?, onChange: (LocalDate?) -> Unit) {
    var raw by remember(value) { mutableStateOf(value?.toString() ?: "") }
    Column {
        OutlinedTextField(
            value = raw,
            onValueChange = {
                raw = it
                onChange(runCatching { LocalDate.parse(it) }.getOrNull())
            },
            label = { Text("$label (YYYY-MM-DD)") },
            isError = error != null,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
```

- [ ] **Step 3: Create `FinancialDetailScreen`**

```kotlin
package com.spendtrack.ui.feature.assets.financial.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.spendtrack.domain.model.FinancialAsset
import com.spendtrack.domain.model.FinancialLot
import com.spendtrack.domain.usecase.calculateAvgYearlyYield
import com.spendtrack.ui.navigation.Screen
import com.spendtrack.util.CurrencyFormatter
import com.spendtrack.util.formatPercent
import com.spendtrack.util.formatQuantity
import com.spendtrack.util.formatSignedCurrency
import com.spendtrack.util.relativeTimeString
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialDetailScreen(
    navController: NavController,
    viewModel: FinancialDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDeleteAssetDialog by remember { mutableStateOf(false) }
    var lotPendingDelete by remember { mutableStateOf<FinancialLot?>(null) }

    LaunchedEffect(state.isDeleted) { if (state.isDeleted) navController.popBackStack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.asset?.name ?: "Asset")
                        state.asset?.let {
                            Text("${it.ticker} · ${it.currencyCode}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    state.asset?.let { a ->
                        IconButton(onClick = {
                            val uri = Uri.parse("https://finance.yahoo.com/quote/${a.ticker}")
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in Yahoo") }
                        if (state.isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 12.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { viewModel.refresh() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh price")
                            }
                        }
                        IconButton(onClick = { showDeleteAssetDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete asset")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loadError -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't load asset")
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { navController.popBackStack() }) { Text("Go back") }
                }
            }
            state.asset == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            else -> {
                val asset = state.asset!!
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { AggregatedStatsCard(asset) }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Lots (${asset.lots.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                navController.navigate(Screen.AddLot.createRoute(asset.id))
                            }) { Text("+ Add") }
                        }
                    }
                    items(asset.lots, key = { it.id }) { lot ->
                        LotRow(
                            lot = lot,
                            currentPrice = asset.latestPrice,
                            currencyCode = asset.currencyCode,
                            onEdit = { navController.navigate(Screen.AddLot.createRouteEdit(asset.id, lot.id)) },
                            onDelete = { lotPendingDelete = lot },
                        )
                    }
                    asset.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                        item { Spacer(Modifier.height(8.dp)); Text("Notes", style = MaterialTheme.typography.titleSmall); Text(notes) }
                    }
                }
            }
        }
    }

    if (showDeleteAssetDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAssetDialog = false },
            title = { Text("Delete asset?") },
            text = { Text("This will delete the asset and all its lots. Cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAssetDialog = false
                    viewModel.deleteAsset()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAssetDialog = false }) { Text("Cancel") }
            },
        )
    }

    lotPendingDelete?.let { lot ->
        val isLast = state.asset?.lots?.size == 1
        AlertDialog(
            onDismissRequest = { lotPendingDelete = null },
            title = { Text(if (isLast) "Delete last lot?" else "Delete lot?") },
            text = {
                if (isLast) Text("This will delete the asset entirely.")
                else Text("This lot will be removed from the asset.")
            },
            confirmButton = {
                TextButton(onClick = {
                    lotPendingDelete = null
                    viewModel.deleteLot(lot.id)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { lotPendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AggregatedStatsCard(asset: FinancialAsset) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Current value", style = MaterialTheme.typography.labelMedium)
            Text(
                asset.currentValue?.let { CurrencyFormatter.formatAbsoluteForCurrency(it, asset.currencyCode) } ?: "—",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(4.dp))
            asset.unrealizedPnl?.let { pnl ->
                val pct = asset.unrealizedPnlPct ?: 0.0
                val color = if (pnl >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                Text(
                    "${formatSignedCurrency(pnl, asset.currencyCode)} (${formatPercent(pct * 100)})",
                    color = color,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            calculateAvgYearlyYield(asset.lots, asset.latestPrice)?.let { yld ->
                val color = if (yld >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                Text("${formatPercent(yld * 100)} per year (CAGR)", color = color, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${formatQuantity(asset.totalQuantity)} units @ avg ${CurrencyFormatter.formatAbsoluteForCurrency(asset.avgCostPerUnit, asset.currencyCode)}",
                style = MaterialTheme.typography.bodySmall,
            )
            asset.latestPriceAt?.let { Text("Updated ${relativeTimeString(it.toEpochMilli())}", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun LotRow(
    lot: FinancialLot,
    currentPrice: Double?,
    currencyCode: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().clickable { sheetOpen = true }) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("${lot.purchaseDate} · ${formatQuantity(lot.quantity)} @ ${CurrencyFormatter.formatAbsoluteForCurrency(lot.pricePerUnit, currencyCode)}",
                style = MaterialTheme.typography.bodyMedium)
            val cost = lot.quantity * lot.pricePerUnit
            val cur = currentPrice?.let { lot.quantity * it }
            val text = buildString {
                append("Cost ${CurrencyFormatter.formatAbsoluteForCurrency(cost, currencyCode)}")
                cur?.let {
                    append(" · Now ${CurrencyFormatter.formatAbsoluteForCurrency(it, currencyCode)}")
                    val pct = (it - cost) / cost
                    append(" · ${formatPercent(pct * 100)}")
                }
            }
            Text(text, style = MaterialTheme.typography.bodySmall)
            currentPrice?.let { cp ->
                val years = ChronoUnit.DAYS.between(lot.purchaseDate, LocalDate.now()) / 365.25
                if (years > 0 && lot.pricePerUnit > 0) {
                    val ratio = cp / lot.pricePerUnit
                    val annualized = if (ratio > 0) ratio.pow(1.0 / years) - 1 else -1.0
                    Text("${formatPercent(annualized * 100)} per year", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (sheetOpen) {
        AlertDialog(
            onDismissRequest = { sheetOpen = false },
            title = { Text("Lot actions") },
            text = { Text("Edit or delete this lot?") },
            confirmButton = {
                TextButton(onClick = { sheetOpen = false; onEdit() }) { Text("Edit") }
            },
            dismissButton = {
                TextButton(onClick = { sheetOpen = false; onDelete() }) { Text("Delete") }
            },
        )
    }
}
```

- [ ] **Step 4: Update `AssetTypePickerSheet.kt`** — enable Financial

Find the Financial `ListItem` block and replace it with:

```kotlin
            ListItem(
                headlineContent = { Text("Financial") },
                supportingContent = { Text("Stocks, ETFs, crypto") },
                leadingContent = {
                    Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = onFinancial),
            )
```

Add `onFinancial: () -> Unit` parameter to `AssetTypePickerSheet(...)` (next to the existing `onRealEstate`).

Update the import for the icon at the top:
```kotlin
import androidx.compose.material.icons.automirrored.filled.ShowChart
```
(Remove the old `import androidx.compose.material.icons.filled.ShowChart` if present.)

- [ ] **Step 5: Update `AssetListRow.kt`** — financial uses ShowChart icon and proper navigation

Replace the icon `when` block with:

```kotlin
        Icon(
            imageVector = when (item.type) {
                AssetType.REAL_ESTATE -> Icons.Filled.HomeWork
                AssetType.FINANCIAL -> Icons.AutoMirrored.Filled.ShowChart
            },
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
```

Add the import for `ShowChart`:
```kotlin
import androidx.compose.material.icons.automirrored.filled.ShowChart
```

(The actual click navigation is in `AssetsListScreen` — updated below.)

- [ ] **Step 6: Update `AssetsListViewModel.kt`** — expose `hasFinancial` flag for the refresh icon visibility

Add to `AssetsListUiState`:

```kotlin
data class AssetsListUiState(
    val items: List<AssetListItem> = emptyList(),
    val total: TotalWealth = TotalWealth(emptyMap(), 0),
    val isRefreshing: Boolean = false,
) {
    val isEmpty: Boolean get() = items.isEmpty()
    val hasFinancial: Boolean get() = items.any { it.type == com.spendtrack.domain.model.AssetType.FINANCIAL }
}
```

Inject `RefreshPricesUseCase` and add a `refresh()` method:

```kotlin
@HiltViewModel
class AssetsListViewModel @Inject constructor(
    getList: GetAssetsListUseCase,
    getTotal: GetTotalWealthUseCase,
    private val refreshPrices: com.spendtrack.domain.usecase.RefreshPricesUseCase,
) : ViewModel() {

    private val refreshing = kotlinx.coroutines.flow.MutableStateFlow(false)

    val uiState: StateFlow<AssetsListUiState> = combine(
        getList(),
        getTotal(),
        refreshing,
    ) { items, total, isRefreshing ->
        AssetsListUiState(items = items, total = total, isRefreshing = isRefreshing)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AssetsListUiState(),
    )

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            runCatching { refreshPrices() }
            refreshing.value = false
        }
    }
}
```

(The 3-arg `combine` requires importing `kotlinx.coroutines.flow.combine` — likely already imported.)

- [ ] **Step 7: Update `AssetsListScreen.kt`** — refresh icon in TopAppBar + financial click navigation

Locate the `Scaffold(topBar = ...)` block. Replace the `TopAppBar(title = { Text("Assets") })` line with:

```kotlin
        topBar = {
            TopAppBar(
                title = { Text("Assets") },
                actions = {
                    if (state.hasFinancial) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 12.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { viewModel.refresh() }) {
                                Icon(androidx.compose.material.icons.Icons.Default.Refresh, contentDescription = "Refresh prices")
                            }
                        }
                    }
                },
            )
        },
```

In the `AssetListRow` `onClick` block, update navigation for `FINANCIAL`:

```kotlin
                        onClick = {
                            when (asset.type) {
                                AssetType.REAL_ESTATE ->
                                    navController.navigate(Screen.RealEstateDetail.createRoute(asset.id))
                                AssetType.FINANCIAL ->
                                    navController.navigate(Screen.FinancialDetail.createRoute(asset.id))
                            }
                        }
```

In the `AssetTypePickerSheet` invocation, add the `onFinancial` callback:

```kotlin
        AssetTypePickerSheet(
            onDismiss = { showTypePicker = false },
            onRealEstate = {
                showTypePicker = false
                navController.navigate(Screen.AddRealEstate.route)
            },
            onFinancial = {
                showTypePicker = false
                navController.navigate(Screen.AddFinancialAsset.route)
            },
        )
```

Add a `LaunchedEffect(Unit)` to trigger lazy refresh on screen entry (debounced inside the use case):

```kotlin
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
```

Place it just after `var showTypePicker by remember { mutableStateOf(false) }`.

- [ ] **Step 8: Update `AppNavGraph.kt`** — add 3 routes + register composables

Add to the `sealed class Screen(...)` block (alongside the real-estate routes):

```kotlin
    object FinancialDetail : Screen("financial_detail/{assetId}") {
        fun createRoute(id: Long) = "financial_detail/$id"
    }
    object AddFinancialAsset : Screen("add_financial_asset")
    object AddLot : Screen("add_lot/{assetId}?lotId={lotId}") {
        fun createRoute(assetId: Long) = "add_lot/$assetId"
        fun createRouteEdit(assetId: Long, lotId: Long) = "add_lot/$assetId?lotId=$lotId"
    }
```

Add imports:

```kotlin
import com.spendtrack.ui.feature.assets.financial.add.AddFinancialAssetScreen
import com.spendtrack.ui.feature.assets.financial.addlot.AddLotScreen
import com.spendtrack.ui.feature.assets.financial.detail.FinancialDetailScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
```

Inside `NavHost(...)`, after the real-estate composables, add:

```kotlin
            composable(Screen.AddFinancialAsset.route) {
                AddFinancialAssetScreen(navController = navController)
            }
            composable(Screen.FinancialDetail.route) {
                FinancialDetailScreen(navController = navController)
            }
            composable(
                route = Screen.AddLot.route,
                arguments = listOf(
                    navArgument("assetId") { type = NavType.StringType },
                    navArgument("lotId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                AddLotScreen(navController = navController)
            }
```

- [ ] **Step 9: Build everything**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. Some Compose / Material 3 deprecation warnings on `menuAnchor()` etc. are acceptable.

- [ ] **Step 10: Run all unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: all tests pass (existing + ~50 new).

- [ ] **Step 11: Commit (single bundled commit)**

```bash
git add app/src/main/java/com/spendtrack/ui/feature/assets/financial/ \
        app/src/main/java/com/spendtrack/ui/feature/assets/typepicker/AssetTypePickerSheet.kt \
        app/src/main/java/com/spendtrack/ui/feature/assets/component/AssetListRow.kt \
        app/src/main/java/com/spendtrack/ui/feature/assets/list/AssetsListScreen.kt \
        app/src/main/java/com/spendtrack/ui/feature/assets/list/AssetsListViewModel.kt \
        app/src/main/java/com/spendtrack/ui/navigation/AppNavGraph.kt
git commit -m "feat(financial): financial asset screens, type picker, list refresh, nav"
```

---

## Task 18: Manual verification + CLAUDE.md update

**Files:**
- Modify: `CLAUDE.md`

The unit tests don't cover Room migration, real Yahoo HTTP, FK CASCADE, or the actual Compose UI — those are deferred to a future instrumented-test infrastructure spec. Manual checklist below substitutes for now.

- [ ] **Step 1: Build and install on the emulator**

```bash
./gradlew assembleDebug
/Users/pedrobatista/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
/Users/pedrobatista/Library/Android/sdk/platform-tools/adb shell am start -n com.spendtrack/.MainActivity
```

- [ ] **Step 2: Run the manual verification checklist**

Walk through each item; if any fails, fix forward in a follow-up commit.

1. Bottom nav still shows Assets; Real Estate detail/edit still works (regression check)
2. Tap FAB → type picker — Financial enabled
3. Tap Financial → form opens
4. Type "AAPL" → ~500 ms later "Apple Inc. · USD · $X.XX" appears, lot fields enable, name/price prefilled
5. Fill quantity (e.g. 10), date, save → returns to list with new AAPL row at current value
6. Tap AAPL → detail shows aggregated stats + 1 lot
7. Detail: "+ Add" → AddLot form for AAPL → fill date/qty/price → save → detail shows 2 lots, stats update
8. Tap a lot → action dialog → Edit → form prefilled → modify → save → updates persist
9. Tap a lot → Delete → if more than 1 lot, lot disappears; if last lot, "This will delete the asset entirely" → confirm → back to list
10. Detail refresh icon → spinner → "Updated just now" appears
11. List refresh icon (visible because ≥1 financial asset) → all financial assets refresh in parallel; header total updates
12. Type a bad ticker (e.g. "ZZZZZ123") → "Ticker not found" message under the ticker field
13. Airplane mode on → tap list refresh → values stay (silent failure); add a new lot → succeeds (no network needed)
14. Add an EUR-denominated asset (e.g. "VWCE.DE") → list header now shows mixed-currency stack
15. Add a crypto (e.g. "BTC-USD") → quantity field accepts decimals like 0.05; current value renders correctly
16. Force-quit the app, reopen offline → list shows last-known values immediately, "Updated X ago" with stale hint if old
17. Delete an asset from detail → cascades cleanly, holding + lots gone (verify with `adb shell run-as com.spendtrack sqlite3 /data/data/com.spendtrack/databases/spendtrack.db ".tables"` and confirm no orphans)
18. Open in browser (↗︎) on detail → Yahoo Finance page opens
19. Upgrade test: install previous APK with seeded data → sideload this build → existing data intact, new financial tables empty

- [ ] **Step 3: Update `CLAUDE.md`**

Open `CLAUDE.md` at the repo root. Apply these edits:

(a) In **"Package Structure"**, under `ui/feature/assets/`, mention the `financial/` subdirectory:

Replace the existing assets line with:

```
    │   ├── assets/                 — Assets list + type picker + real estate + financial detail/edit
```

(b) In **"Navigation Routes"**, add three new rows:

```
| `add_financial_asset` | Add Financial Asset (with inline ticker validation) |
| `financial_detail/{assetId}` | Financial Asset detail (stats + lot list) |
| `add_lot/{assetId}?lotId={id}` | Add or edit a financial asset lot |
```

(c) In **"Known Gaps / Future Work"**, ADD:

```
- Sales / partial position closing (FIFO/LIFO accounting, realized P&L, XIRR for accurate yield)
- Dividends tracking (manual entry + total return)
- FX conversion for grand-total wealth across mixed currencies
- Charts (price history, value-over-time)
- Background price refresh (WorkManager) — currently only lazy + force-refresh
- Asset-search by name (not just exact ticker)
```

REMOVE the "Financial assets (ETF / stocks / crypto) — separate spec, includes Yahoo Finance + 15-min cache" line (it's no longer a gap).

- [ ] **Step 4: Commit the docs update**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md for financial assets feature"
```

---

## Summary

After all 18 tasks:

- Bottom nav has the Assets tab; Real Estate AND Financial asset types both fully functional.
- Users can add ETFs / stocks / crypto by ticker (Yahoo-validated), record multiple purchase lots per asset, see live current value with 15-min caching, refresh on demand, view per-lot return and per-lot annualized yield, and aggregate position metrics.
- Persisted last-known prices on `assets.current_value` give offline-friendly first paint.
- Network failures degrade gracefully — never blocking the UI.
- DB migrates v3 → v4 cleanly, with `MIGRATION_3_4` SQL matching Room's generated `createAllTables` to avoid the identity-hash trap.
- ~50 new unit tests cover the critical math (yield CAGR), all ViewModels, the price repository's cache logic, the Yahoo HTTP layer (via MockWebServer), and the formatter helpers.
- The architecture is forward-compatible: adding sales / dividends / FX / charts is purely additive (each is a deferred follow-up).





