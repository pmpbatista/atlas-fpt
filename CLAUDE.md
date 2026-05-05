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
│   │   ├── AppDatabase.kt          — Room DB, version 1, seeds Portuguese categories on first create
│   │   ├── Converters.kt           — LocalDate ↔ String type converter
│   │   ├── dao/                    — TransactionDao, CategoryDao, LabelDao, RecurringRuleDao
│   │   └── entity/                 — DB entities + mappers to/from domain models
│   ├── importer/
│   │   └── CsvImporter.kt          — Parses CSV: date,amount,type,category,note
│   ├── repository/                 — TransactionRepository, CategoryRepository, LabelRepository, RecurringRuleRepository
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
│   ├── model/                      — Transaction, Category, Label, RecurringRule, CategoryType, TransactionType, RecurringFrequency
│   └── usecase/
│       ├── SaveTransactionUseCase.kt
│       ├── DeleteTransactionUseCase.kt  — also cleans up orphaned recurring rules
│       ├── GetTimelineUseCase.kt
│       ├── GetOverviewUseCase.kt
│       └── MaterialiseRecurringTransactionsUseCase.kt
└── ui/
    ├── component/                  — AmountDisplay, CategoryPickerBottomSheet, LabelChip, MonthSelector, TransactionRow
    ├── feature/
    │   ├── addtransaction/         — AddTransactionScreen + ViewModel (dual-purpose: add and edit)
    │   ├── csvimport/              — ImportScreen + ViewModel (NOTE: package is csvimport, not import — reserved keyword)
    │   ├── overview/               — OverviewScreen + ViewModel
    │   ├── settings/               — SettingsScreen + ViewModel
    │   └── timeline/               — TimelineScreen + ViewModel
    ├── navigation/
    │   └── AppNavGraph.kt          — NavHost + bottom bar; routes: timeline, overview, activity, settings, add_transaction, edit_transaction/{id}, import
    └── theme/                      — Color.kt, Theme.kt, Type.kt
```

## Key Design Decisions

- **`csvimport` package name:** The import feature package is `com.spendtrack.ui.feature.csvimport` (NOT `import`) — `import` is a reserved keyword in Kotlin/Java and causes KSP/Hilt code generation to crash.
- **Edit vs Add screen:** `AddTransactionScreen` serves both purposes. `transactionId == null || transactionId == 0L` means add mode; otherwise edit mode. The `isEditMode` local val captures this.
- **Delete flow:** Trash icon in TopAppBar (edit mode only) → confirmation `AlertDialog` → `DeleteTransactionUseCase` → `isDeleted = true` → `LaunchedEffect` pops back stack. Mirrors the `isSaved` save pattern.
- **Category seeding:** Default Portuguese expense/income categories are inserted via `SeedCallback` when the Room DB is first created.
- **WorkManager init:** Default WorkManager initialiser is disabled in the manifest; custom init is done via `Configuration.Provider` in `SpendTrackApplication`.

## Navigation Routes

| Route | Screen |
|---|---|
| `timeline` | Timeline (start destination) |
| `overview` | Overview with charts |
| `activity` | Placeholder (coming soon) |
| `settings` | Settings |
| `add_transaction` | Add Transaction |
| `edit_transaction/{transactionId}` | Edit Transaction |
| `import` | CSV Import |

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

## Known Gaps / Future Work

- No app icon / launcher resources (`res/` only has `values/`)
- No instrumented tests (`androidTest/`)
- `Activity` screen is a placeholder
- `save()` in `AddTransactionViewModel` hardcodes `recurringRuleId = null`, which loses the rule link when editing a recurring transaction
- `delete()` silently no-ops if called before `loadTransaction()` completes (extremely unlikely in practice but untested)

## Repository

`https://github.com/pmpbatista/spendtrack.git` — single `main` branch
