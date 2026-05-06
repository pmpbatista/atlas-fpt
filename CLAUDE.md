# SpendTrack — Project Context

Personal finance tracker Android app. Tracks expenses and income with categories, labels, recurring rules, and CSV import.

## Quick Start

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest

# Install and launch on connected emulator
/Users/pedrobatista/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
/Users/pedrobatista/Library/Android/sdk/platform-tools/adb shell am start -n com.spendtrack/.MainActivity
```

## Environment

- **Android SDK:** `/Users/pedrobatista/Library/Android/sdk`
- **Installed platform:** `android-36.1` (compileSdk/targetSdk = 36, minSdk = 26)
- **Android Studio:** `/Applications/Android Studio.app` (v2025.3.4)
- **`local.properties`** and **`gradle.properties`** are present and not tracked by git — do not delete them

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 dark theme |
| DI | Hilt 2.51.1 |
| Database | Room 2.6.1 (SQLite) |
| Navigation | Navigation Compose 2.8.3 |
| Background | WorkManager 2.9.1 |
| Charts | Vico 1.13.1 |
| Images | Coil 2.7.0 |
| Unit tests | JUnit 4 + MockK + kotlinx-coroutines-test + Turbine |
| Build | AGP 8.7.3, KSP 2.0.21-1.0.28 |

## Architecture

Clean Architecture with three layers:

```
ui/          → Compose screens + ViewModels (MVVM)
domain/      → Models + use cases (pure Kotlin)
data/        → Room DB, repositories, WorkManager, settings
di/          → Hilt modules
```

## Package Structure

```
com.spendtrack/
├── MainActivity.kt
├── SpendTrackApplication.kt
├── data/
│   ├── db/
│   │   ├── AppDatabase.kt          — Room DB, version 2, seeds Portuguese categories on first create
│   │   ├── Converters.kt           — LocalDate ↔ String type converter
│   │   ├── dao/                    — TransactionDao, CategoryDao, LabelDao, RecurringRuleDao, PersonDao
│   │   └── entity/                 — DB entities + mappers to/from domain models
│   ├── importer/
│   │   └── CsvImporter.kt          — Parses CSV: date,amount,type,category,note
│   ├── repository/                 — TransactionRepository, CategoryRepository, LabelRepository, RecurringRuleRepository, PersonRepository
│   ├── settings/
│   │   ├── AppSettings.kt          — data class(currencySymbol, currencyCode)
│   │   └── SettingsRepository.kt   — StateFlow<AppSettings> backed by SharedPreferences
│   └── worker/
│       └── RecurringTransactionWorker.kt — WorkManager worker that materialises recurring transactions
├── di/
│   ├── DatabaseModule.kt
│   ├── RepositoryModule.kt
│   └── WorkerModule.kt
├── domain/
│   ├── model/                      — Transaction, Category, Label, Person, RecurringRule, CategoryType, TransactionType, RecurringFrequency
│   └── usecase/
│       ├── SaveTransactionUseCase.kt
│       ├── DeleteTransactionUseCase.kt  — also cleans up orphaned recurring rules
│       ├── GetTimelineUseCase.kt
│       ├── GetOverviewUseCase.kt
│       └── MaterialiseRecurringTransactionsUseCase.kt
└── ui/
    ├── component/                  — AmountDisplay, CategoryPickerBottomSheet, LabelChip, PersonChip, PersonPickerBottomSheet, MonthSelector, TransactionRow
    ├── feature/
    │   ├── addtransaction/         — AddTransactionScreen + ViewModel (dual-purpose: add and edit)
    │   ├── assets/                 — Assets list + type picker + real estate detail/edit
    │   ├── csvimport/              — ImportScreen + ViewModel (NOTE: package is csvimport, not import — reserved keyword)
    │   ├── overview/               — OverviewScreen + ViewModel
    │   ├── persons/                — PersonsScreen + ViewModel (manage persons list)
    │   ├── settings/               — SettingsScreen + ViewModel
    │   └── timeline/               — TimelineScreen + ViewModel
    ├── navigation/
    │   └── AppNavGraph.kt          — NavHost + bottom bar; routes: timeline, overview, assets, settings, add_transaction, edit_transaction/{id}, import, persons, real_estate_detail/{id}, add_real_estate, edit_real_estate/{id}
    └── theme/                      — Color.kt, Theme.kt, Type.kt
```

## Key Design Decisions

- **`csvimport` package name:** The import feature package is `com.spendtrack.ui.feature.csvimport` (NOT `import`) — `import` is a reserved keyword in Kotlin/Java and causes KSP/Hilt code generation to crash.
- **Edit vs Add screen:** `AddTransactionScreen` serves both purposes. `transactionId == null || transactionId == 0L` means add mode; otherwise edit mode. The `isEditMode` local val captures this.
- **Delete flow:** Trash icon in TopAppBar (edit mode only) → confirmation `AlertDialog` → `DeleteTransactionUseCase` → `isDeleted = true` → `LaunchedEffect` pops back stack. Mirrors the `isSaved` save pattern.
- **Category seeding:** Default Portuguese expense/income categories are inserted via `SeedCallback` when the Room DB is first created.
- **WorkManager init:** Default WorkManager initialiser is disabled in the manifest; custom init is done via `Configuration.Provider` in `SpendTrackApplication`.
- **Persons many-to-many:** Mirrors the Labels pattern exactly — `persons` table + `transaction_person_cross_ref` cross-ref table with composite PK and `ON DELETE CASCADE` on both FKs. `TransactionWithDetails` fetches persons via `@Relation(Junction(TransactionPersonCrossRef::class))`. `TransactionRepository.save()` does delete-then-re-insert for cross-refs, same as labels. DB version 2 with `MIGRATION_1_2`.
- **PersonRepository.save vs insert:** The repository exposes `save()` (consistent with `LabelRepository`); internally it delegates to `PersonDao.insert()` with `OnConflictStrategy.IGNORE`.

## Navigation Routes

| Route | Screen |
|---|---|
| `timeline` | Timeline (start destination) |
| `overview` | Overview with charts |
| `settings` | Settings |
| `add_transaction` | Add Transaction |
| `edit_transaction/{transactionId}` | Edit Transaction |
| `import` | CSV Import |
| `persons` | Persons management (Settings sub-screen) |
| `assets` | Assets list (replaces Activity) |
| `real_estate_detail/{assetId}` | Real Estate detail (read-only) |
| `add_real_estate` | Add Real Estate |
| `edit_real_estate/{assetId}` | Edit Real Estate |

## CSV Import Format

```
date,amount,type,category,note
2026-01-15,48.14,expense,Contas,Internet
2026-01-14,1500.00,income,Salário,January
```

- **date:** `yyyy-MM-dd` or `dd/MM/yyyy`
- **type:** `expense` / `income` (case-insensitive; also accepts Portuguese: `despesa`, `receita`, `saída`, `entrada`)
- **category:** matched case-insensitively to existing categories; falls back to first matching type category if not found

## Testing

Unit tests live in `app/src/test/java/com/spendtrack/`. Test infrastructure:

- `util/MainDispatcherRule.kt` — JUnit4 rule that sets `Dispatchers.Main` to `UnconfinedTestDispatcher`
- `ui/feature/addtransaction/AddTransactionViewModelDeleteTest.kt` — 3 tests for delete functions using MockK + Turbine

Run: `./gradlew testDebugUnitTest`

## Code Patterns

### ViewModel

All ViewModels use `@HiltViewModel` + `@Inject constructor`. State is a `data class` exposed as `StateFlow` built from `combine().stateIn()`. Mutable state lives in a private `MutableStateFlow<UiState>` named `_form` (or similar). Navigation side-effects (`isSaved`, `isDeleted`) are boolean fields set to `true` after an action completes; the screen observes them with `LaunchedEffect` and calls `navController.popBackStack()`.

```kotlin
data class MyUiState(
    val someValue: String = "",
    val isSaved: Boolean = false,    // set true → screen navigates back
    val isLoading: Boolean = false
)

@HiltViewModel
class MyViewModel @Inject constructor(
    private val someRepository: SomeRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _form = MutableStateFlow(MyUiState())

    val uiState: StateFlow<MyUiState> = combine(
        _form,
        settingsRepository.settings          // StateFlow<AppSettings>
    ) { form, settings ->
        form.copy(/* merge settings if needed */)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MyUiState()
    )

    fun onValueChanged(v: String) { _form.update { it.copy(someValue = v) } }

    fun save() {
        _form.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            someRepository.save(...)
            _form.update { it.copy(isLoading = false, isSaved = true) }
        }
    }
}
```

Screen navigation-back pattern (place after `val uiState by viewModel.uiState.collectAsState()`):
```kotlin
LaunchedEffect(uiState.isSaved) {
    if (uiState.isSaved) navController.popBackStack()
}
```

### Hilt DI

Use cases inject directly via `@Inject constructor` — no module registration needed. Repositories and DAOs are provided in `di/DatabaseModule.kt` (`@Provides`) and `di/RepositoryModule.kt`. To add a new singleton dependency:

```kotlin
// di/DatabaseModule.kt — add a @Provides for a new DAO:
@Provides
fun provideMyDao(db: AppDatabase): MyDao = db.myDao()

// Use cases need NO module entry — Hilt resolves them automatically:
class MyUseCase @Inject constructor(
    private val myRepository: MyRepository
) {
    suspend operator fun invoke(...) { ... }
}

// ViewModels need NO module entry either:
@HiltViewModel
class MyViewModel @Inject constructor(
    private val myUseCase: MyUseCase
) : ViewModel()
```

### Adding a New Screen / Route

**1. Add a `Screen` entry** in `ui/navigation/AppNavGraph.kt`:
```kotlin
sealed class Screen(val route: String) {
    // existing entries...
    object MyScreen : Screen("my_screen")
    // with args:
    object MyDetail : Screen("my_detail/{itemId}") {
        fun createRoute(id: Long) = "my_detail/$id"
    }
}
```

**2. Register a `composable` in the `NavHost`** in the same file:
```kotlin
composable(Screen.MyScreen.route) {
    MyScreen(navController = navController)
}
// with args:
composable(Screen.MyDetail.route) { backStack ->
    val id = backStack.arguments?.getString("itemId")?.toLongOrNull()
    MyDetailScreen(navController = navController, itemId = id)
}
```

**3. Add to bottom nav** (optional) by appending to `bottomNavItems`:
```kotlin
Triple(Screen.MyScreen, Icons.Default.SomeIcon, "Label")
```

Navigate to it from any screen with `navController.navigate(Screen.MyScreen.route)`.

### Room DAO Queries

Any query returning a `Transaction` must use `TransactionWithDetails` (not `TransactionEntity`) and be annotated with `@Transaction` so Room fetches the related `CategoryEntity` and `List<LabelEntity>` in one go. The result maps to domain via `.toDomain()`.

```kotlin
// In a DAO interface:
@Transaction
@Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
suspend fun getById(id: Long): TransactionWithDetails?

// In the repository, convert to domain:
suspend fun getById(id: Long): Transaction? = withContext(Dispatchers.IO) {
    dao.getById(id)?.toDomain()
}
```

For aggregate queries that don't return full transaction rows (summaries, totals), `@Transaction` is not needed — define a plain `data class` for the result:
```kotlin
data class MyAggregate(val categoryId: Long, val total: Double)

@Query("SELECT categoryId, SUM(amount) as total FROM transactions GROUP BY categoryId")
fun observeTotals(): Flow<List<MyAggregate>>
```

## Known Gaps / Future Work

- No app icon / launcher resources (`res/` only has `values/`)
- No instrumented tests (`androidTest/`)
- `save()` in `AddTransactionViewModel` hardcodes `recurringRuleId = null`, which loses the rule link when editing a recurring transaction
- `delete()` silently no-ops if called before `loadTransaction()` completes (extremely unlikely in practice but untested)
- Persons: no rename after creation (edit name is not supported)
- Persons: not shown on `TransactionRow` in the timeline (only visible in the edit screen)
- Persons: no filtering of timeline/overview by person
- Financial assets (ETF / stocks / crypto) — separate spec, includes Yahoo Finance + 15-min cache
- Linking transactions to assets (assetId FK on transactions)
- Live Euribor rate fetching for variable-rate real estate
- Photos for real-estate properties
- Instrumented tests (Room migration tests, DAO query tests, FK CASCADE verification, Compose UI tests)

## Repository

`https://github.com/pmpbatista/spendtrack.git` — single `main` branch
