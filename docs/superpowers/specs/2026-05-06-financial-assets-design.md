# Financial Assets — Design Spec

**Date:** 2026-05-06
**Status:** Draft
**Scope:** Spec #2 of 2 in the asset-tracking effort. Adds the **financial** asset type (ETF / stocks / crypto) on top of the assets shell shipped in [`2026-05-06-assets-real-estate-design.md`](2026-05-06-assets-real-estate-design.md). Live prices come from Yahoo Finance.

## Goal

Let the user track financial holdings — ETFs, stocks, crypto — under the existing Assets feature. For each holding the user enters a ticker (validated against Yahoo Finance) and one or more purchase lots. The app fetches the current price (15-min cached), computes unrealized P&L and average yearly yield, and aggregates lots of the same ticker as a single asset.

## Non-goals

- **Sales / position closing.** This spec is buy-and-track only. To "sell", the user deletes lots. No realized P&L, no FIFO/LIFO accounting, no XIRR.
- **Dividends.** No dividend events; yield is pure price appreciation.
- **FX conversion** for the totals header. The mixed-currency stack from real estate continues unchanged. FX is a deferred follow-up.
- **Charts / price history.** Only the latest price snapshot is persisted.
- **Background price refresh** (no `WorkManager` job). Refresh is lazy on screen open + explicit on a refresh button.
- **Compose UI tests, Room migration tests, FK CASCADE tests** — deferred to a future instrumented-test infrastructure spec.

## Decisions

### Scope and integration
- New asset type `FINANCIAL` becomes selectable in the existing `AssetTypePickerSheet`. The "Coming soon" state on the Financial row is removed.
- Reuses the parent `assets` table for shared fields; adds two new tables (`financial_holdings`, `financial_lots`).
- DB version bump **3 → 4**, with `MIGRATION_3_4` matching Room's generated `createAllTables` strings exactly (per the real-estate spec's lessons-learned about identity-hash mismatches).
- The Assets list view treats financial assets just like real estate: one row per asset, currency in native, equity contributing to the per-currency total.

### Position model
- Buy-and-track only. Positions can grow; they don't shrink except by deleting lots.
- A "lot" is one purchase: `{purchase_date, quantity, price_per_unit}`. An asset has 1..N lots.
- Aggregation for display is done from lot data: total quantity, weighted-average cost basis, total cost.

### Pricing
- **Source:** Yahoo Finance unofficial endpoint `https://query1.finance.yahoo.com/v8/finance/chart/{ticker}?interval=1d&range=1d`. No auth, free, fragile.
- **Defensive design:** all network calls go through a `PriceSource` interface so the implementation can be swapped (Alpha Vantage / Finnhub / etc.) without touching the rest of the app.
- **Cache:** 15-minute in-memory cache (`Map<ticker, CachedQuote>` in a `@Singleton PriceRepository`). On every successful fetch, the latest price is also persisted to `assets.current_value` + `financial_holdings.latest_price` so the app shows last-known values immediately on next launch (even offline).
- **Refresh triggers:** lazy on assets-list open (debounced, max once per app foreground session) + explicit refresh button in the list TopAppBar.
- **Network failures collapse** to "fall back to persisted last-known"; user only sees an error UI when explicitly requesting (force-refresh failure → snackbar; ticker validation failure → field error).

### Calculations
- **Current value** = `latest_price × SUM(lots.quantity)`.
- **Unrealized P&L** = `current_value − total_cost`. Percent = `(current_value − total_cost) / total_cost`.
- **Average yearly yield** = per-lot CAGR weighted by lot cost:
  - For each lot: `years_held = (today − lot.purchase_date) / 365.25`
  - Per-lot CAGR: `(current_price / lot.price)^(1/years_held) − 1`
  - Aggregated: `SUM(lot_yield × lot_cost) / SUM(lot_cost)`
  - Lots with `years_held ≤ 0` (purchased today) are excluded — CAGR over 0 years is undefined.
- **No total return**, no dividend tracking, no time-weighted return.

### UX
- **Inline ticker validation** on the add-asset screen (debounced 500 ms). Lot fields disabled until ticker validates. `name` and `pricePerUnit` prefilled from the resolved quote.
- **Detail screen** shows: aggregated stats card (current value, P&L, yield, "last updated X ago"), expandable lot list (per-lot return + annualized yield + edit/delete actions), notes, "+ Add lot" button, and TopAppBar actions (open in Yahoo browser, force-refresh, delete asset).
- **Add-lot screen** is a slim form (date / quantity / price) — no ticker field, no network call required.
- **Refresh button** on the assets list TopAppBar — visible only when at least one financial asset exists.

### Architecture
- **Data model:** `assets` (parent) + `financial_holdings` (1:1, ticker-level info + cached price) + `financial_lots` (1:many, individual purchases). FK `ON DELETE CASCADE` from both child tables to `assets.id`.
- **Network layer:** new `data/network/` package with `PriceSource` interface + `YahooPriceSource` Retrofit-based impl + DTOs.
- **Domain models:** `FinancialAsset` (typed model with computed getters), `FinancialLot`, `TickerQuote`, `QuoteResult` sealed type.
- **Use cases:** `ValidateTickerUseCase`, `GetFinancialAssetUseCase`, `SaveFinancialAssetUseCase` (creates asset + holding + first lot in one transaction), `AddLotUseCase`, `DeleteLotUseCase` (deletes the asset if the last lot was removed), `RefreshPricesUseCase`, `CalculateYieldUseCase` (pure-function, isolated for testing).
- **Repositories:** `FinancialRepository` (typed CRUD for holding + lots), `PriceRepository` (cache + fetch + persisted-update orchestration). Reuses existing `AssetRepository` for delete + list projection.

## Data model

### Room schema (DB version 3 → 4)

The parent `assets` table is unchanged. For financial assets:
- `assets.current_value` = `latest_price × SUM(lots.quantity)`. Recomputed and persisted on every price change AND on every lot change. Acts as the offline last-known value.
- `assets.current_value_updated_at` = `financial_holdings.latest_price_at`. Mirrored to the parent so the existing assets-list query stays unchanged.
- `assets.purchase_date` = the earliest lot's date. Updated when lots change.
- `assets.currency_code` = the currency Yahoo reports for the ticker. Frozen at asset creation.

**`financial_holdings`** (1:1 with `assets`, `ON DELETE CASCADE`)

| Column | Type | Notes |
|---|---|---|
| `asset_id` | `INTEGER PRIMARY KEY NOT NULL` (FK → `assets.id`) | |
| `ticker` | `TEXT NOT NULL` | Yahoo symbol exactly as resolved (e.g. `AAPL`, `VWCE.DE`, `BTC-USD`) |
| `display_name` | `TEXT NOT NULL` | Yahoo's `shortName` or `longName`, snapshotted at creation |
| `latest_price` | `REAL` | Nullable until first successful fetch; persisted last-known |
| `latest_price_at` | `INTEGER` | Epoch millis of `latest_price`; nullable when never fetched |

**`financial_lots`** (1:many with `assets.id`, `ON DELETE CASCADE`)

| Column | Type | Notes |
|---|---|---|
| `id` | `INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL` | |
| `asset_id` | `INTEGER NOT NULL` (FK → `assets.id`, indexed) | |
| `purchase_date` | `TEXT NOT NULL` | LocalDate via existing converter |
| `quantity` | `REAL NOT NULL` | Shares / units / coins. Doubles suffice for retail amounts |
| `price_per_unit` | `REAL NOT NULL` | In the asset's `currency_code` |

Index on `financial_lots.asset_id` for FK lookups. `financial_holdings.asset_id` is the PK so no separate index needed.

Migration `3 → 4` adds both tables. SQL strings copied verbatim from Room's generated `AppDatabase_Impl.createAllTables` (after a build) to avoid identity-hash mismatch.

### Domain models

```kotlin
data class FinancialAsset(
    val id: Long,
    val name: String,                  // user-overrideable; defaults to displayName
    val ticker: String,
    val displayName: String,
    val currencyCode: String,
    val latestPrice: Double?,          // null until first fetch succeeds
    val latestPriceAt: Instant?,
    val notes: String?,
    val lots: List<FinancialLot>,      // ordered by date asc
) {
    val totalQuantity: Double get() = lots.sumOf { it.quantity }
    val totalCost: Double     get() = lots.sumOf { it.quantity * it.pricePerUnit }
    val avgCostPerUnit: Double get() =
        if (totalQuantity > 0) totalCost / totalQuantity else 0.0
    val currentValue: Double? get() = latestPrice?.let { it * totalQuantity }
    val unrealizedPnl: Double? get() = currentValue?.let { it - totalCost }
    val unrealizedPnlPct: Double? get() = currentValue?.let { (it - totalCost) / totalCost }
}

data class FinancialLot(
    val id: Long,
    val purchaseDate: LocalDate,
    val quantity: Double,
    val pricePerUnit: Double,
)

data class TickerQuote(
    val ticker: String,            // canonical form Yahoo returns
    val displayName: String,
    val currencyCode: String,
    val price: Double,
    val asOf: Instant,
)

sealed interface QuoteResult {
    data class Success(val quote: TickerQuote) : QuoteResult
    data object NotFound : QuoteResult            // ticker doesn't exist on Yahoo (HTTP 404 / empty result)
    data class Error(val cause: String) : QuoteResult  // network / parse / 5xx
}
```

## Package layout (additions)

```
com.spendtrack/
├── data/
│   ├── db/
│   │   ├── AppDatabase.kt                          (version 3 → 4; add MIGRATION_3_4 + 2 entities + DAO)
│   │   ├── dao/
│   │   │   └── FinancialDao.kt                     (NEW — joined reads + holding/lot CRUD)
│   │   └── entity/
│   │       ├── FinancialHoldingEntity.kt           (NEW)
│   │       ├── FinancialLotEntity.kt               (NEW)
│   │       └── AssetWithFinancial.kt               (NEW — @Relation wrapper)
│   ├── network/                                    (NEW package)
│   │   ├── YahooFinanceApi.kt                      (Retrofit interface)
│   │   ├── YahooFinanceDtos.kt                     (kotlinx.serialization data classes)
│   │   ├── PriceSource.kt                          (interface, returns QuoteResult)
│   │   └── impl/
│   │       └── YahooPriceSource.kt                 (Retrofit-based impl)
│   └── repository/
│       ├── FinancialRepository.kt                  (NEW)
│       └── PriceRepository.kt                      (NEW — cache + orchestration)
├── di/
│   ├── DatabaseModule.kt                           (add @Provides for FinancialDao)
│   ├── NetworkModule.kt                            (NEW — Retrofit, OkHttp, kotlinx.serialization, PriceSource binding)
│   └── RepositoryModule.kt                         (unchanged)
├── domain/
│   ├── model/
│   │   ├── FinancialAsset.kt                       (NEW)
│   │   ├── FinancialLot.kt                         (NEW)
│   │   ├── TickerQuote.kt                          (NEW)
│   │   └── QuoteResult.kt                          (NEW — sealed type)
│   └── usecase/
│       ├── ValidateTickerUseCase.kt                (NEW)
│       ├── GetFinancialAssetUseCase.kt             (NEW)
│       ├── SaveFinancialAssetUseCase.kt            (NEW — creates asset + holding + first lot in one txn)
│       ├── AddLotUseCase.kt                        (NEW — appends a lot, recomputes current_value)
│       ├── DeleteLotUseCase.kt                     (NEW — deletes lot; cascades to delete asset if last)
│       ├── RefreshPricesUseCase.kt                 (NEW — force-refresh all financial)
│       └── CalculateYieldUseCase.kt                (NEW — pure function, unit-tested)
└── ui/feature/assets/
    └── financial/
        ├── add/
        │   ├── AddFinancialAssetScreen.kt          (NEW — first-purchase form with inline validation)
        │   └── AddFinancialAssetViewModel.kt       (NEW)
        ├── detail/
        │   ├── FinancialDetailScreen.kt            (NEW)
        │   └── FinancialDetailViewModel.kt         (NEW)
        └── addlot/
            ├── AddLotScreen.kt                     (NEW — date/quantity/price; also handles edit-lot mode)
            └── AddLotViewModel.kt                  (NEW)
```

Plus updates:
- `AssetTypePickerSheet.kt` — Financial row enabled, navigates to `AddFinancialAsset`.
- `AssetListRow.kt` — financial uses `Icons.AutoMirrored.Filled.ShowChart`; click navigates to `FinancialDetail`.
- `AssetsListScreen.kt` — TopAppBar gets a refresh icon (visible only when any financial asset exists).
- `AppNavGraph.kt` — three new routes (`add_financial_asset`, `financial_detail/{assetId}`, `add_lot/{assetId}` with optional `?lotId=`).

## Network layer

### Yahoo endpoint

```
GET https://query1.finance.yahoo.com/v8/finance/chart/{ticker}?interval=1d&range=1d
Headers: User-Agent: Mozilla/5.0 (compatible; SpendTrack/1.0)
```

Response (only fields we use):

```json
{
  "chart": {
    "result": [{
      "meta": {
        "symbol": "AAPL",
        "currency": "USD",
        "shortName": "Apple Inc.",
        "longName": "Apple Inc.",
        "regularMarketPrice": 234.56,
        "regularMarketTime": 1736186400
      }
    }],
    "error": null
  }
}
```

Failure modes:
- HTTP 404 / `chart.error` populated → ticker invalid → `QuoteResult.NotFound`
- HTTP 5xx, network exception, malformed JSON → `QuoteResult.Error(cause)`
- HTTP 200 with valid `meta` → `QuoteResult.Success(quote)`

### `PriceSource` interface

```kotlin
interface PriceSource {
    suspend fun fetchQuote(ticker: String): QuoteResult
}
```

Hilt binds `PriceSource` to `YahooPriceSource` in `NetworkModule`.

### `PriceRepository`

Singleton with in-memory cache:

```kotlin
@Singleton
class PriceRepository @Inject constructor(
    private val source: PriceSource,
    private val financialDao: FinancialDao,
    private val assetDao: AssetDao,
    private val db: AppDatabase,
) {
    private val cache = ConcurrentHashMap<String, CachedQuote>()
    private val refreshMutex = Mutex()
    private val ttl = Duration.ofMinutes(15)

    /**
     * Returns the latest quote, falling through:
     *   1. In-memory cache if < 15 min old (and !force)
     *   2. Network fetch on success: cache + persist + return
     *   3. Network failure: return persisted last-known
     *   4. No persisted, network down: null
     */
    suspend fun getQuote(ticker: String, force: Boolean = false): TickerQuote?

    /** Force-refresh all financial assets in parallel (Semaphore(3)). */
    suspend fun refreshAll(): RefreshResult

    /** Used by the add flow; always hits network (no cache check). */
    suspend fun validateTicker(ticker: String): QuoteResult = source.fetchQuote(ticker)
}

data class RefreshResult(val succeeded: Int, val failed: Int)
```

After every successful fetch, in a single `db.withTransaction`:
- `financial_holdings.latest_price` ← new price
- `financial_holdings.latest_price_at` ← `Instant.now()`
- `assets.current_value` ← `new_price × SUM(lots.quantity)`
- `assets.current_value_updated_at` ← `Instant.now()`

`refreshAll` uses a `Mutex` so concurrent calls await the in-flight one and share its result. Inside, `Semaphore(3)` caps per-fetch parallelism.

### Network library

Retrofit + kotlinx.serialization, both new dependencies. OkHttp pulled in transitively.

```kotlin
// build.gradle.kts (versions in libs.versions.toml)
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
plugins { kotlin("plugin.serialization") version "2.0.21" }
```

`NetworkModule` provides `OkHttpClient` (with `User-Agent` interceptor + 10s connect/read timeouts), `Retrofit`, `YahooFinanceApi`, and binds `PriceSource` to `YahooPriceSource`.

## Calculations

### Asset-level (FinancialAsset getters)

Trivial math; covered by unit tests via the ViewModel paths.

### Average yearly yield (`CalculateYieldUseCase`)

```kotlin
fun calculateAvgYearlyYield(
    lots: List<FinancialLot>,
    currentPrice: Double?,
    today: LocalDate = LocalDate.now()
): Double? {
    if (currentPrice == null || lots.isEmpty()) return null
    val perLot = lots.mapNotNull { lot ->
        val years = ChronoUnit.DAYS.between(lot.purchaseDate, today) / 365.25
        if (years <= 0.0 || lot.pricePerUnit <= 0.0) return@mapNotNull null
        val ratio = currentPrice / lot.pricePerUnit
        val yieldVal = if (ratio > 0) ratio.pow(1.0 / years) - 1.0 else -1.0
        val cost = lot.quantity * lot.pricePerUnit
        yieldVal to cost
    }
    if (perLot.isEmpty()) return null
    val totalCost = perLot.sumOf { it.second }
    return perLot.sumOf { it.first * it.second } / totalCost
}
```

Edge cases:
- Same-day lots excluded (years_held ≤ 0)
- All-same-day → returns null
- Negative ratio (price ≤ 0) → -100%/year
- Zero cost lot → excluded (avoids division by zero in weighting)

### Per-lot stats (FinancialDetailViewModel helpers)

Pure functions for `lotCost`, `lotCurrentValue`, `lotReturnAbs`, `lotReturnPct`, `lotAnnualizedYield`. Tested via ViewModel tests.

### Total wealth integration

`GetTotalWealthUseCase` is unchanged. Financial assets contribute via `assets.current_value` (with `outstandingDebt = null`, so `equity = currentValue`). The list query's `LEFT JOIN` to `real_estate_details` returns null for `outstanding_debt` on financial rows — already handled.

### Display helpers (extends `AssetFormatting.kt`)

- `formatQuantity(qty)` — adapts decimals to magnitude (100 → "100", 0.001234 → "0.001234", 1.23e-7 → "0.00000012")
- `formatSignedCurrency(amount, currencyCode)` — "+ $123.45" or "− €50.00"
- Reuses existing `signedPercent` for yield

## UX flows & screens

### Updates to existing screens

**`AssetTypePickerSheet`:** Financial row enabled; "Coming soon" subtitle removed; clicks navigate to `Screen.AddFinancialAsset`.

**`AssetListRow`:** Financial type uses `Icons.AutoMirrored.Filled.ShowChart`. Click navigates to `Screen.FinancialDetail.createRoute(asset.id)`.

**`AssetsListScreen`:** TopAppBar gets a refresh icon (visible only when financial assets exist). Triggers `RefreshPricesUseCase`. Lazy refresh via `LaunchedEffect(Unit)` runs once per screen entry, debounced inside the use case.

### New: Add Financial Asset (`Screen.AddFinancialAsset`)

Single-screen form, sectioned scroll:

- **Ticker** — text input. Capitalized. Debounced 500 ms → `ValidateTickerUseCase`. State machine: `Idle / Validating / Valid(quote) / Invalid(reason) / Error(reason)`.
- **Validation feedback** under the ticker field: spinner during `Validating`; `"Apple Inc. · USD · $234.56"` on `Valid`; error text on `Invalid` / `Error`.
- **Name** (text, optional override) — prefilled from `quote.displayName` on first `Valid`; not overwritten on subsequent re-validations.
- **Purchase**: date picker (defaults to today, must be ≤ today), quantity (decimal numeric), price/share (decimal numeric, prefilled from `quote.price`).
- **Currency badge** next to price — read-only, from `quote.currencyCode`.
- **Notes** (multiline, optional).
- **Save** button — bottom bar, disabled until ticker is `Valid` AND lot fields valid. Calls `SaveFinancialAssetUseCase`. `isSaved = true` → `popBackStack()`.

### New: Financial Detail (`Screen.FinancialDetail`)

Read-only with inline lot editing:

- **TopAppBar:** back, title = `name`, subtitle = `ticker · currencyCode`. Actions: open in Yahoo browser (↗︎), refresh (⟳ ↔ spinner), delete asset (🗑).
- **Aggregated stats card** (Material 3 `Card`):
  - Current value (large, formatted in asset currency)
  - Unrealized P&L absolute + percent (signed, color-coded)
  - Average yearly yield (signed percent + "per year")
  - Total quantity + average cost basis ("10.00 shares @ avg $210.00")
  - "Last updated X ago" with stale hint if > 1 day old
- **Lots section** with header "Lots (N)" + "+ Add" button:
  - Each lot row: date · "qty @ price" · cost · current value · per-lot return (abs + percent) · annualized yield
  - Tap a row → action sheet "Edit" / "Delete"
  - Edit → `Screen.AddLot.createRoute(asset.id, lotId)` (prefilled)
  - Delete → confirmation; if last lot, extra warning "This will delete the asset entirely"
- **Notes** (only if non-blank).
- **Loading** state: spinner. **Error** state: "Couldn't load asset" + back button.

### New: Add / Edit Lot (`Screen.AddLot`)

Slim form, dual-purpose like `AddTransactionScreen`:

- TopAppBar title: "Add lot to {ticker}" / "Edit lot of {ticker}"
- Fields: date (defaults today), quantity, price/share (prefilled with cached `latest_price`)
- Save button at bottom; disabled while invalid
- No network call required. Saves via `AddLotUseCase` (insert) or via the same use case in update mode (mirror real-estate pattern with optional `lotId` route arg)
- Save → recompute `assets.current_value` = new total quantity × cached `latest_price`, update `assets.purchase_date` = earliest lot date, `popBackStack()`

### Force-refresh button on AssetsListScreen

- TopAppBar action — `Icons.Default.Refresh` swaps to a small spinner during refresh
- Hidden when zero financial assets (no point refreshing real-estate)
- On failure: snackbar "Couldn't refresh N of M assets"

## Validation & error handling

### Field rules — `AddFinancialAssetScreen`

| Field | Rule | Error message |
|---|---|---|
| `ticker` | Non-blank | "Ticker is required" |
| `ticker` | Validation = `Valid` | "Ticker not recognised" / "Couldn't reach price source" |
| `name` | Non-blank | "Name is required" |
| `purchaseDate` | Required, ≤ today | "Purchase date is required" / "Cannot be in the future" |
| `quantity` | Required, > 0 | "Quantity must be greater than 0" |
| `pricePerUnit` | Required, > 0 | "Price must be greater than 0" |
| `notes` | Optional | — |

### Field rules — `AddLotScreen`

| Field | Rule | Error message |
|---|---|---|
| `purchaseDate` | Required, ≤ today | (same as above) |
| `quantity` | Required, > 0 | (same) |
| `pricePerUnit` | Required, > 0 | (same) |

Validation computed via `_form.map { computeErrors(it) }` (same pattern as real estate, with `SharingStarted.Eagerly` for the same async-init reasoning).

### Network failure behavior

| Scenario | Behavior |
|---|---|
| `getQuote` cache miss + network failure | Returns persisted last-known. Silent. |
| `getQuote` cache miss + persisted null + network failure | Returns null. UI shows "—". |
| `validateTicker` returns `NotFound` | Ticker field error: "Ticker not found. Try the symbol exactly as on Yahoo Finance (e.g. AAPL, VWCE.DE, BTC-USD)." |
| `validateTicker` returns `Error` | Ticker field error: "Couldn't reach price source. Check your connection." |
| `refreshAll` per-asset failures | Other assets continue. Snackbar "Couldn't refresh N of M assets". Persisted last-known stays. |
| `Save` / `AddLot` / `Delete` exceptions | "Couldn't save / add / delete. Try again." snackbar |
| `Save` / `AddLot` `IllegalStateException` (asset deleted from another flow) | "This asset no longer exists" specific message |

### Edge cases

- **Editing a lot whose asset was deleted** — `AddLotUseCase` throws `IllegalStateException`, caught with the specific message.
- **Currency change at Yahoo** (extremely unlikely) — frozen currency stays in DB; logged warning.
- **Concurrent refreshes** — `Mutex` shares the in-flight execution.
- **Offline lot addition** — works (no network needed). `current_value` recomputes from cached `latest_price`.
- **Deleting the last lot** — extra warning; deletes the asset entirely via `db.withTransaction`.
- **Same ticker added twice** — `SaveFinancialAssetUseCase` checks for duplicate ticker; throws `IllegalStateException("Asset for $ticker already exists")` → "An asset for this ticker already exists" snackbar in the add screen.

### Migration `3 → 4`

Mirror the real-estate spec's pattern: SQL strings copied verbatim from Room's generated `AppDatabase_Impl.createAllTables` (after a build) to avoid the identity-hash mismatch trap. The plan will document this step explicitly.

```sql
-- Functional schema (final form to be confirmed against Room's generated text):
CREATE TABLE IF NOT EXISTS `financial_holdings` (
    `asset_id` INTEGER NOT NULL,
    `ticker` TEXT NOT NULL,
    `display_name` TEXT NOT NULL,
    `latest_price` REAL,
    `latest_price_at` INTEGER,
    PRIMARY KEY(`asset_id`),
    FOREIGN KEY(`asset_id`) REFERENCES `assets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS `financial_lots` (
    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `asset_id` INTEGER NOT NULL,
    `purchase_date` TEXT NOT NULL,
    `quantity` REAL NOT NULL,
    `price_per_unit` REAL NOT NULL,
    FOREIGN KEY(`asset_id`) REFERENCES `assets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS `index_financial_lots_asset_id` ON `financial_lots` (`asset_id`);
```

## Testing

### Unit tests (this spec)

**`CalculateYieldUseCaseTest`** — pure function, no mocks
- Empty lots → null
- Null current price → null
- Single lot held 1 year, +10% gain → 0.10
- Single lot held 0.5 years, +10% gain → ~0.21 (annualizes up)
- Single lot held 2 years, +21% gain → ~0.10 (annualizes down)
- Two lots held 1y and 2y, both +10% → weighted between values
- Lot purchased today excluded; if only one → null
- Negative price ratio → -1.0
- Zero cost lot → excluded

**`PriceRepositoryTest`** — mocks `PriceSource`, DAOs, AppDatabase
- Cache miss + Success → caches + persists + returns
- Cache fresh → no network call
- Cache stale + Success → re-fetches
- Cache stale + NotFound → returns persisted last-known
- Cache stale + Error → returns persisted last-known
- `force=true` bypasses fresh cache
- `validateTicker` always hits network
- `refreshAll` per-asset isolation
- `refreshAll` mutex deduplication

**`AddFinancialAssetViewModelTest`** — mocks `ValidateTickerUseCase`, `SaveFinancialAssetUseCase`, `SettingsRepository`
- Ticker state transitions: Idle → Validating → Valid / Invalid / Error
- `name` and `pricePerUnit` prefill on first Valid; not overwritten on re-validation
- Form validation: gates Save on ticker valid AND fields valid
- Save success → `isSaved = true`
- Save IllegalStateException → "asset already exists" message
- Save other exception → generic message

**`FinancialDetailViewModelTest`** — mocks `GetFinancialAssetUseCase`, `DeleteAssetUseCase`, `DeleteLotUseCase`, `PriceRepository`
- Loads asset, computes equity / P&L
- Missing id / missing asset → loadError
- `refresh()` calls `PriceRepository.getQuote(ticker, force=true)`
- `deleteLot(lotId)`: regular case vs last-lot case
- `deleteAsset()` flow

**`AddLotViewModelTest`** — mocks `AddLotUseCase`, `GetFinancialAssetUseCase`
- Prefill ticker / currency / cached price
- Form validation
- Save (insert vs edit mode)
- Asset-deleted error path

**`AssetFormattingTest` (extension)**
- `formatQuantity` magnitude buckets
- `formatSignedCurrency` positive / negative / zero

**`YahooPriceSourceTest`** — uses `okhttp3:mockwebserver`
- 200 with valid meta → Success
- 200 with `chart.error` → NotFound
- 404 → NotFound
- 5xx → Error
- Network exception → Error
- Malformed JSON → Error
- Verifies User-Agent header

Target: ~50–60 new unit tests.

### Deferred (require instrumented tests)

- Room migration `3 → 4`
- DAO query correctness (joined `AssetWithFinancial` reads)
- `ON DELETE CASCADE` for asset deletion
- Compose UI tests
- Real Yahoo HTTP integration

These are explicit risks; manual checklist below substitutes.

### Run command

```bash
./gradlew testDebugUnitTest
```

### Manual verification checklist

1. Bottom nav shows Assets; Real Estate detail/edit still works (regression check)
2. Tap FAB → type picker — Financial enabled
3. Tap Financial → form opens
4. Type "AAPL" → ~500 ms later "Apple Inc. · USD · $X.XX" appears, lot fields enable, name/price prefilled
5. Fill quantity, save → returns to list with new AAPL row at current value
6. Tap AAPL → detail shows aggregated stats + 1 lot
7. Detail: "+ Add lot" → add another purchase → detail shows 2 lots, stats update
8. Tap a lot → Edit → modify → save → updates persist
9. Tap a lot → Delete → if more than 1, lot removed; if last, "deletes asset entirely" warning → confirm → back to list
10. Detail refresh icon → spinner → "Last updated just now"
11. List refresh icon → all financial assets refresh in parallel; header total updates
12. Bad ticker (e.g. "ZZZZZ123") → "Ticker not found"
13. Airplane mode → list refresh → snackbar; values stay
14. Airplane mode → add lot to existing AAPL → succeeds (no network needed)
15. Add EUR asset (e.g. "VWCE.DE") → list header shows mixed-currency stack
16. Add crypto (e.g. "BTC-USD") → quantity accepts decimals like 0.05
17. Delete an asset from detail → cascades cleanly
18. Force-quit + reopen offline → list shows last-known values; stale hint if old
19. Upgrade test: install previous APK with seeded data → confirm intact + new tables empty

## Open follow-ups (for later specs)

- **FX conversion** for grand-total wealth (across both real estate and financial)
- **Sales / partial closings** (separate sale events, FIFO/LIFO, realized P&L, XIRR for accurate yield)
- **Dividends** (manual entry; total return calculation)
- **Charts** (price history, value-over-time)
- **Background refresh** (`WorkManager` for periodic updates while app closed)
- **Linking transactions to assets** (asset_id FK on transactions for cost-basis verification)
- **Instrumented test infrastructure** (Room migration tests, DAO tests, real Yahoo integration tests, Compose UI)
- **Asset-search by name** (the Yahoo `v1/finance/search` endpoint, for users who don't know the exact ticker)
