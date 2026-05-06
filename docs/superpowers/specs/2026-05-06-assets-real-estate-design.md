# Assets — Foundation & Real Estate

**Date:** 2026-05-06
**Status:** Draft
**Scope:** Spec #1 of 2 in the asset-tracking effort. This spec ships the assets feature shell and the real-estate asset type. Spec #2 (separate) will add financial assets (ETF / stocks / crypto) with Yahoo Finance integration.

## Goal

Let the user track their assets alongside the existing transactions feature. This spec adds the assets shell (navigation, list, totals header, type picker) and the real-estate asset type end-to-end (create / edit / delete / read-only detail), so the user can record properties with cost, capital, debt, location, and current valuation, and see net wealth aggregated across them.

## Non-goals

- Financial assets (ETF / stocks / crypto) — separate spec
- Yahoo Finance / live price fetching, caching, P&L
- Live Euribor rate fetching
- Linking transactions to assets (e.g. mortgage-payment ledger entries)
- Photos / images on properties
- Mortgage amortization schedules
- FX conversion for the mixed-currency total
- Compose UI tests, Room instrumented tests
- App icon / launcher resources (separate known gap)

## Decisions

### Scope and integration
- The assets feature lives at the bottom-nav slot currently occupied by the **Activity** placeholder. The `Activity` screen and its sealed-class entry are removed.
- Assets are **fully separate from transactions**. Asset events do not create transactions, and vice versa. A future spec may add an `assetId` foreign key on `transactions` for linkage; this spec doesn't.
- The foundation and real-estate types are bundled into one spec. Without a generic "Other" type to give the foundation user-facing value, shipping it as a standalone release adds no end-user value, so the two are merged.

### Total wealth model
- The list header shows **net wealth**: `SUM(current_value − COALESCE(outstanding_debt, 0))` grouped by `currency_code`.
- When all assets share a currency, the header shows a single number. When currencies differ, it shows a per-currency breakdown stacked, with no synthetic conversion. FX conversion is deferred to the financial spec.

### Real-estate field semantics
- `cost`, `invested_capital`, and `debt_amount` are **three independent user-provided values**. They are not related by `cost = invested_capital + debt_amount`.
  - `cost` = price paid to the seller (excludes taxes/fees), preserved for future appreciation calculations
  - `invested_capital` = total out-of-pocket cash (down payment + taxes + notary + agent fees)
  - `debt_amount` = initial mortgage; nullable (a property bought outright has no debt)
- `outstanding_debt` is **manually editable** and defaults to `debt_amount` on creation. The user updates it from their bank app as payments are made. No amortization schedule is computed.
- Variable interest rates are stored **structured**: `reference_rate` enum (`EURIBOR_1M / 3M / 6M / 12M`) + `spread` (Double, signed). Fixed rates store a single `fixed_rate` value. Live Euribor fetch is out of scope.
- Location (`district`, `council`, `parish`) is freeform text. `size_m2` is numeric. `energy_rating` is an enum (`A_PLUS`, `A`, `B`, `B_MINUS`, `C`, `D`, `E`, `F`).

### Architecture
- **Data model:** parent `assets` table + per-type `real_estate_details` table (1:1, FK with `ON DELETE CASCADE`). Mirrors the existing transactions/categories pattern. Joined reads use `@Transaction` and `@Relation`.
- **Per-asset currency:** `assets.currency_code` is a column on the parent table, defaulting to the app's currency from `SettingsRepository`. Currency is read-only after asset creation.
- **Domain layer:** per-type isolated models. `RealEstateAsset` is a typed model used for detail/edit screens. The shared assets list uses a lightweight `AssetListItem` projection populated by a `LEFT JOIN`.
- **Detail vs edit screens:** typed assets get a separate read-only detail screen + a combined add/edit form (mirrors the dual-purpose `AddTransactionScreen` pattern). Delete lives on the edit screen via a TopAppBar trash icon + confirmation dialog.

## Data model

### Room schema (DB version 1 → 2)

**`assets`** (parent, shared fields)

| Column | Type | Notes |
|---|---|---|
| `id` | `INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL` | |
| `type` | `TEXT NOT NULL` | `REAL_ESTATE` (this spec) or `FINANCIAL` (next spec) |
| `name` | `TEXT NOT NULL` | User label |
| `currency_code` | `TEXT NOT NULL` | Defaults to app currency |
| `current_value` | `REAL NOT NULL` | Manually edited for real estate |
| `current_value_updated_at` | `INTEGER NOT NULL` | Epoch millis; bumped on valuation change |
| `purchase_date` | `TEXT` | LocalDate via existing converter; nullable on parent |
| `notes` | `TEXT` | Optional |

**`real_estate_details`** (1:1 with `assets`, `ON DELETE CASCADE`)

| Column | Type | Notes |
|---|---|---|
| `asset_id` | `INTEGER PRIMARY KEY NOT NULL` (FK → `assets.id`) | |
| `cost` | `REAL NOT NULL` | Price paid to seller |
| `invested_capital` | `REAL NOT NULL` | Total out-of-pocket cash |
| `debt_amount` | `REAL` | Nullable: cash purchase |
| `outstanding_debt` | `REAL` | Nullable when no debt; manually edited |
| `interest_type` | `TEXT` | `FIXED` or `VARIABLE`; null when no debt |
| `fixed_rate` | `REAL` | Used when `interest_type = FIXED` |
| `reference_rate` | `TEXT` | `EURIBOR_1M / 3M / 6M / 12M`; used when `VARIABLE` |
| `spread` | `REAL` | Signed; used when `VARIABLE` |
| `credit_end_date` | `TEXT` | LocalDate; required when debt present |
| `district` | `TEXT NOT NULL` | Freeform |
| `council` | `TEXT NOT NULL` | Freeform |
| `parish` | `TEXT NOT NULL` | Freeform |
| `size_m2` | `REAL NOT NULL` | |
| `energy_rating` | `TEXT NOT NULL` | Enum value |

Index on `real_estate_details.asset_id` for FK lookups.

Migration `1 → 2` adds both tables; no data backfill (new tables start empty).

### Domain models

```kotlin
enum class AssetType { REAL_ESTATE, FINANCIAL }
enum class InterestType { FIXED, VARIABLE }
enum class ReferenceRate(val label: String) {
    EURIBOR_1M("Euribor 1M"),
    EURIBOR_3M("Euribor 3M"),
    EURIBOR_6M("Euribor 6M"),
    EURIBOR_12M("Euribor 12M"),
}
enum class EnergyRating(val label: String) {
    A_PLUS("A+"), A("A"), B("B"), B_MINUS("B-"),
    C("C"), D("D"), E("E"), F("F"),
}

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

data class TotalWealth(
    val byCurrency: Map<String, Double>,
    val assetCount: Int,
) {
    val isMixedCurrency: Boolean get() = byCurrency.size > 1
    val isEmpty: Boolean get() = byCurrency.isEmpty()
}

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

## Package layout (additions)

```
com.spendtrack/
├── data/
│   ├── db/
│   │   ├── AppDatabase.kt                       (version 1 → 2; add Migration1to2 + new entities)
│   │   ├── dao/
│   │   │   ├── AssetDao.kt                      (NEW — list projection, parent CRUD)
│   │   │   └── RealEstateDao.kt                 (NEW — joined reads + detail-table CRUD)
│   │   └── entity/
│   │       ├── AssetEntity.kt                   (NEW)
│   │       ├── RealEstateDetailsEntity.kt       (NEW)
│   │       └── AssetWithRealEstate.kt           (NEW — @Relation wrapper)
│   └── repository/
│       ├── AssetRepository.kt                   (NEW)
│       └── RealEstateRepository.kt              (NEW)
├── domain/
│   ├── model/
│   │   ├── AssetType.kt                         (NEW)
│   │   ├── AssetListItem.kt                     (NEW)
│   │   ├── TotalWealth.kt                       (NEW)
│   │   ├── RealEstateAsset.kt                   (NEW)
│   │   ├── EnergyRating.kt                      (NEW)
│   │   ├── InterestType.kt                      (NEW)
│   │   └── ReferenceRate.kt                     (NEW)
│   └── usecase/
│       ├── GetAssetsListUseCase.kt              (NEW)
│       ├── GetTotalWealthUseCase.kt             (NEW)
│       ├── SaveRealEstateUseCase.kt             (NEW)
│       ├── GetRealEstateUseCase.kt              (NEW)
│       └── DeleteAssetUseCase.kt                (NEW)
├── di/
│   ├── DatabaseModule.kt                        (add @Provides for AssetDao, RealEstateDao)
│   └── RepositoryModule.kt                      (add @Provides for AssetRepository, RealEstateRepository)
└── ui/feature/assets/
    ├── list/
    │   ├── AssetsListScreen.kt                  (NEW)
    │   └── AssetsListViewModel.kt               (NEW)
    ├── typepicker/
    │   └── AssetTypePickerSheet.kt              (NEW)
    ├── realestate/
    │   ├── detail/
    │   │   ├── RealEstateDetailScreen.kt        (NEW)
    │   │   └── RealEstateDetailViewModel.kt     (NEW)
    │   └── edit/
    │       ├── AddEditRealEstateScreen.kt       (NEW — combined add/edit)
    │       └── AddEditRealEstateViewModel.kt    (NEW)
    └── component/
        ├── TotalWealthHeader.kt                 (NEW)
        └── AssetListRow.kt                      (NEW)
```

## Navigation

Routes added to `AppNavGraph.kt`:

```kotlin
object Assets : Screen("assets")                                     // bottom nav
object RealEstateDetail : Screen("real_estate_detail/{assetId}") {
    fun createRoute(id: Long) = "real_estate_detail/$id"
}
object AddRealEstate : Screen("add_real_estate")
object EditRealEstate : Screen("edit_real_estate/{assetId}") {
    fun createRoute(id: Long) = "edit_real_estate/$id"
}
```

`bottomNavItems` in `AppNavGraph.kt`: replace the `Triple(Screen.Activity, Icons.Default.Timeline, "Activity")` entry with `Triple(Screen.Assets, Icons.Default.AccountBalance, "Assets")`. Remove `Screen.Activity` and its placeholder composable.

## Screens

### Assets list (`Screen.Assets`)

- `TopAppBar` titled "Assets"
- `TotalWealthHeader` (collapses when list empty)
- `LazyColumn` of `AssetListRow`s ordered by `LOWER(name) ASC`
- Empty state (when list is empty): "No assets yet" + "Tap + to add one"
- Bottom-right FAB → `AssetTypePickerSheet`

`TotalWealthHeader` rendering:
- Empty → not rendered
- One currency → "Net wealth" + single large amount, subtitle "Across N assets"
- Multiple currencies → "Net wealth" + each currency stacked, subtitle "Across N assets · mixed currencies"

`AssetListRow`:
- Leading: type icon (house glyph for real estate)
- Title: `name`
- Subtitle: equity formatted in `currencyCode` (e.g. `€100,000`)
- Trailing: gross value in muted text (e.g. `Value €300,000`)
- Click → `RealEstateDetail.createRoute(id)` (dispatch on `type` once financial lands)

### Asset type picker (modal bottom sheet)

`ModalBottomSheet` with two `ListItem` rows:
- **Real Estate** → `AddRealEstate`
- **Financial** — disabled with subtitle "Coming soon"

### Real Estate detail (`Screen.RealEstateDetail`)

Read-only. `TopAppBar`: back arrow, title = property `name`, action = edit pencil → `EditRealEstate.createRoute(id)`.

Body sections (`LazyColumn`):
1. **Valuation card** — current market value (large), equity below (`currentValue − (outstandingDebt ?: 0)`), "Updated X ago" from `current_value_updated_at`
2. **Purchase summary** — `purchase_date`, `cost`, `invested_capital`
3. **Debt section** (when `debt_amount != null`):
   - Initial debt amount, outstanding debt
   - Interest description (see "Interest description" below)
   - `credit_end_date` + remaining months
   - Otherwise: "Bought outright" badge
4. **Property details** — district / council / parish on one line, size (`N m²`), energy rating chip
5. **Notes** (if non-empty)

Loading state: centered progress spinner. Load error: "Couldn't load property" + back button.

### Add / Edit Real Estate (`Screen.AddRealEstate` / `Screen.EditRealEstate`)

Single combined screen with `assetId == null` → add, non-null → edit. Mirrors `AddTransactionScreen` exactly.

`TopAppBar`:
- Title: "Add property" / "Edit property"
- Edit-mode action: trash icon → `AlertDialog` confirm → `DeleteAssetUseCase` → `popBackStack()`

Form sections (single scroll, no toggles):

1. **Basics** — name (required), currency (defaults to app currency, dropdown to override on add only — read-only on edit), purchase date (required), current value (required, numeric)
2. **Money** — cost (required), invested capital (required), debt amount (optional numeric)
3. **Debt details** (revealed when `debt_amount` set):
   - Outstanding debt (numeric, defaults to `debt_amount` value when adding)
   - Interest type segmented control (Fixed / Variable)
   - Fixed → rate (numeric %)
   - Variable → reference rate dropdown + spread (numeric %)
   - Credit end date (date picker)
4. **Property** — district, council, parish (text), size m² (numeric), energy rating (dropdown)
5. **Notes** (multiline)

Bottom: "Save" button. Disabled while invalid.

Save behavior:
- Add: insert into `assets` then `real_estate_details` in a single Room `@Transaction`
- Edit: update both rows in one `@Transaction`. Bump `current_value_updated_at` to `Instant.now()` only when `current_value` changed
- Both: set `isSaved = true`, `LaunchedEffect(isSaved)` calls `popBackStack()`

## Calculations & display logic

### Per-currency net wealth (`GetTotalWealthUseCase`)

Consumes `Flow<List<AssetListItem>>` from `AssetRepository.observeAssetList()`. Folds in Kotlin (not SQL) so the financial spec can layer FX conversion later without DAO changes:

```kotlin
class GetTotalWealthUseCase @Inject constructor(
    private val repo: AssetRepository,
) {
    operator fun invoke(): Flow<TotalWealth> = repo.observeAssetList().map { items ->
        val byCurrency = items.groupBy { it.currencyCode }
            .mapValues { (_, list) -> list.sumOf { it.equity } }
        TotalWealth(byCurrency, assetCount = items.size)
    }
}
```

### List query (`AssetDao`)

```kotlin
@Query("""
    SELECT a.id, a.type, a.name, a.current_value AS currentValue,
           a.currency_code AS currencyCode, r.outstanding_debt AS outstandingDebt
    FROM assets a
    LEFT JOIN real_estate_details r ON r.asset_id = a.id
    ORDER BY LOWER(a.name) ASC
""")
fun observeAssetList(): Flow<List<AssetListItem>>
```

When the financial spec lands it adds a second `LEFT JOIN` to the financial detail table. The projection's shape is preserved.

### Currency formatting

Reuse existing `CurrencyFormatter` (commit `0f142fd`). Add an overload that accepts an explicit `currencyCode` so each asset can be formatted in its native currency. Single-currency call sites continue to pass the settings value.

### Relative time ("Updated X ago")

Helper in the detail ViewModel (no new file unless it grows):

```kotlin
fun relativeTimeString(epochMillis: Long, now: Instant = Instant.now()): String
```

Buckets: today / yesterday / N days / N months / over a year. `java.time` only.

### Remaining months on credit

`ChronoUnit.MONTHS.between(LocalDate.now(), credit_end_date)`. Display:
- `> 12` → "X years Y months remaining"
- `1..12` → "N months remaining"
- `<= 0` → "Credit ended"

### Interest description

```kotlin
fun describeInterest(asset: RealEstateAsset): String = when (asset.interestType) {
    null -> "Bought outright"
    InterestType.FIXED -> "Fixed ${formatPercent(asset.fixedRate)}"
    InterestType.VARIABLE -> "${asset.referenceRate?.label} ${signedPercent(asset.spread)}"
}
```

`signedPercent(0.5) = "+ 0.5%"`, `signedPercent(-0.2) = "− 0.2%"`. Spread can be negative.

### Computation placement

| Calculation | Lives in |
|---|---|
| List projection (joined SQL) | `AssetDao.observeAssetList()` |
| Per-currency net wealth | `GetTotalWealthUseCase` |
| Equity per row | `AssetListItem.equity` getter |
| Relative time, remaining months | ViewModel helpers |
| Interest description | UI-layer pure helper |

## Validation & error handling

### Field rules

| Field | Rule | Error message |
|---|---|---|
| `name` | Non-blank | "Name is required" |
| `purchase_date` | Required, parses, ≤ today | "Purchase date is required" / "Cannot be in the future" |
| `current_value` | Required, ≥ 0 | "Current value is required" |
| `cost` | Required, ≥ 0 | "Cost is required" |
| `invested_capital` | Required, ≥ 0 | "Invested capital is required" |
| `debt_amount` | Optional. If present, > 0 | "Must be greater than 0" |
| `outstanding_debt` | Required when `debt_amount` set; ≥ 0 | "Outstanding debt is required" |
| `interest_type` | Required when `debt_amount` set | "Pick fixed or variable" |
| `fixed_rate` | Required when FIXED | "Rate is required" |
| `reference_rate` | Required when VARIABLE | "Pick a reference rate" |
| `spread` | Required when VARIABLE; negative allowed | "Spread is required" |
| `credit_end_date` | Required when debt present; > `purchase_date` | "Must be after purchase date" |
| `district`, `council`, `parish` | Non-blank | "Required" |
| `size_m2` | > 0 | "Size must be greater than 0" |
| `energy_rating` | Required (has default) | — |

Validation lives in `AddEditRealEstateViewModel`; computed reactively from form `StateFlow`; exposed as `FormErrors` on `UiState`. Inline error text under each field. Save button disabled while `formErrors.hasAny`.

### Rules deliberately not enforced

- `outstanding_debt ≤ debt_amount` — the user might prepay then re-borrow
- `cost + fees ≈ invested_capital + debt_amount` — they're independent values

### Decimal input

Single helper `parseDecimal(input: String): Double?`:
- Trims whitespace
- Accepts both `.` and `,` as decimal separator (Portuguese locale uses `,`)
- Returns `null` on failure (no exceptions into UI)

Form state stores raw input strings so the user's "12," doesn't get truncated mid-typing. Validation parses on every change.

### Currency immutability

`currency_code` is read-only on the edit screen. To change currency, the user deletes and re-adds. Reason: changing currency mid-life invalidates historical valuation entries; the financial spec will compound this once price snapshots accumulate.

### Database errors

- All Room ops in `withContext(Dispatchers.IO)` (existing pattern)
- Use cases throw on failure; ViewModels wrap save/delete in `try/catch`
- Save failure → `errorMessage` set, `Snackbar` triggered by `LaunchedEffect`, form values preserved
- Delete failure → same; user stays on edit screen
- Load failure (detail/edit) → `loadError = true`, screen shows centered "Couldn't load property" + back button
- No retries (rare on local SQLite; user can re-tap save)

### Edge cases

- **Editing a deleted asset**: save use case checks parent exists; throws if not → caught → "This property no longer exists" snackbar → `popBackStack()`
- **Stale form on rotation**: ViewModel state survives via `viewModelScope` + `StateFlow`; field cursors via `rememberSaveable`
- **Date picker**: stores `LocalDate`, persisted via existing `Converters.kt`

### FK cascade

`real_estate_details.asset_id` declared `ON DELETE CASCADE`. `DeleteAssetUseCase` deletes only the parent row; Room CASCADE handles the detail row. (Verification deferred to instrumented tests — see Testing.)

## Testing

### Unit tests (this spec)

**`GetTotalWealthUseCaseTest`**
- Empty list → `isEmpty == true`
- Single EUR no debt → entry equals `currentValue`
- Single EUR with `outstanding_debt = 100k` → entry equals `currentValue − 100k`
- Two EUR assets → sum
- Mixed EUR + USD → two entries; `isMixedCurrency == true`
- `outstanding_debt = null` → treated as 0
- Underwater (`outstanding_debt > currentValue`) → entry can be negative

**`AddEditRealEstateViewModelTest`**

Form validation (Turbine):
- Empty form → all required fields flagged
- Valid form → save enabled
- `debt_amount` blank → debt sub-fields not required
- `debt_amount` set → outstanding/interest/end-date required
- `interest_type = FIXED` → `fixed_rate` required, others not
- `interest_type = VARIABLE` → `reference_rate`/`spread` required, `fixed_rate` not
- `credit_end_date ≤ purchase_date` → error
- `purchase_date` in future → error
- `parseDecimal` accepts "1234.56" and "1234,56"; rejects "abc"
- Currency code read-only in edit mode

Save (mocks `SaveRealEstateUseCase`):
- Add → invokes use case, `isSaved = true`
- Edit → invokes with existing `assetId`, `isSaved = true`
- Use case throws → `errorMessage` set, form preserved
- `current_value_updated_at` bumped only when value changed (delegated to use case)

Delete (mirrors `AddTransactionViewModelDeleteTest`):
- `delete()` invokes `DeleteAssetUseCase`, `isDeleted = true`
- Use case throws → `errorMessage` set
- `delete()` is a no-op before `loadAsset()` completes (acknowledged gap, same as transactions)

**`AssetsListViewModelTest`**
- Empty → `UiState.isEmpty == true`, header collapsed
- Populated → rows map 1:1 to `AssetListItem`s
- Mixed currencies → `header.isMixedCurrency == true`

**`RealEstateDetailViewModelTest`**
- Missing id → `loadError = true`
- Equity computed correctly
- Interest description: "Bought outright" / "Fixed 3.2%" / "Euribor 12M + 1.5%" / "Euribor 12M − 0.2%"
- Remaining months: future date / past date ("credit ended")
- Relative time buckets

### Deferred (require instrumented tests — out of scope here)

- Room migration `1 → 2`
- DAO query correctness (the joined `LEFT JOIN`)
- `ON DELETE CASCADE` behavior
- Compose UI tests

These deferrals are explicit risks. The fallback is the manual checklist below.

### Run command

```bash
./gradlew testDebugUnitTest
```

### Manual verification checklist (run before claiming done)

1. Fresh install on emulator → bottom nav shows "Assets" replacing "Activity"
2. Empty Assets list shows empty state + FAB
3. Tap FAB → type picker; "Financial" disabled, "Real Estate" → form
4. Add a property with no debt → list shows it; header shows "Net wealth · €X" matching `current_value`
5. Add a property with debt → header shows `current_value − outstanding_debt`
6. Add a USD-denominated property → header shows mixed-currency stack
7. Tap a row → detail screen; all sections populated correctly
8. Edit pencil → form pre-filled; change valuation, save → detail shows updated value with "Updated today"
9. Edit form → trash → confirm → list returns without that asset
10. Upgrade test: install previous APK with seeded data → sideload new APK → confirm transactions/categories intact and Assets section appears empty

## Open follow-ups (for later specs)

- **Spec #2 — Financial assets** (Yahoo Finance integration, 15-min cache, multi-lot positions, P&L, average yearly yield, FX conversion)
- Live Euribor rate fetching (additive — no schema change)
- Photos / images on properties
- Linking transactions to assets (`assetId` FK on `transactions`)
- Mortgage amortization schedule (would replace manual `outstanding_debt` editing)
- Instrumented test infrastructure (Room migration tests, DAO tests, Compose UI tests)
- Gross/Net total-wealth toggle on the list header
