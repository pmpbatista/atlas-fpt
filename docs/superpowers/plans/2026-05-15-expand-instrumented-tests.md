# Plan: Expand instrumented test coverage (#38)

Spec: [`docs/superpowers/specs/2026-05-15-expand-instrumented-tests.md`](../specs/2026-05-15-expand-instrumented-tests.md).

## Files

- `app/src/androidTest/java/com/atlasfpt/data/db/TransactionDaoQueryTest.kt` — new
- `app/src/androidTest/java/com/atlasfpt/data/db/AssetCascadeTest.kt` — new
- `app/src/androidTest/java/com/atlasfpt/data/db/TransactionCascadeTest.kt` — add a `transaction_label_cross_ref` test

## Steps

1. `TransactionDaoQueryTest`:
   - `setup`/`teardown` mirroring `AppDatabaseSmokeTest`.
   - Seed an EXPENSE category + an INCOME category.
   - **Test A (`observeByDateRange` inclusive):** insert 3 transactions on 2026-01-01, 2026-01-15, 2026-01-31; query [2026-01-01, 2026-01-31]; expect all 3.
   - **Test B (`observeByDateRange` excludes scheduled):** insert a normal row + an `isScheduled = true` row in the same range; expect only the normal row.
   - **Test C (`getCategoryTotals`):** insert 3 EXPENSE rows across 2 categories + 1 INCOME row; query EXPENSE totals; expect 2 rows summing per category, INCOME excluded.
   - **Test D (`observeMonthlySummaries`):** insert rows across 2026-01 and 2026-02; expect 2 grouped rows with correct totals.
2. `AssetCascadeTest`:
   - Seed an `AssetEntity` (financial type) + matching `FinancialHoldingEntity` + a `FinancialLotEntity`.
   - **Test E:** delete the asset → expect `financialDao.getHolding(id)` returns null AND `financialDao.countLots(id) == 0`.
   - Repeat with `AssetType.REAL_ESTATE` + `RealEstateDetailsEntity`.
   - **Test F:** delete the asset → expect `realEstateDao.getWithDetails(id)?.details` is null.
3. Extend `TransactionCascadeTest` with `deletingTransactionCascadesLabelCrossRefs`:
   - Seed a category + a label + a transaction; insert a `TransactionLabelCrossRef`.
   - Use a raw SQL count via `db.openHelper.readableDatabase` (no DAO surfaces the count directly) or add a tiny `LabelDao.countTransactionsFor(labelId)` query. **Decision:** raw-SQL count to keep the change minimal — no production-code change.

## Risks

- **`AssetEntity` requires `type`, `currencyCode`, `currentValue`, `currentValueUpdatedAt`.** Capture sensible defaults in the test helper.
- **`AssetType` enum import path** — verify with the existing financial detail tests before writing.
- **No `LabelDao` count query exists** — use raw `db.query("SELECT COUNT(*) ...")` to avoid touching production code.

## Verification

- `./gradlew compileDebugUnitTestKotlin compileDebugAndroidTestKotlin` (compile gate)
- `./gradlew testDebugUnitTest` (existing unit tests must still pass)
- User runs `connectedDebugAndroidTest` on emulator before merge.
