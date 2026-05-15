# Plan: Sales / FIFO / realized P&L / XIRR (#26)

Spec: [`docs/superpowers/specs/2026-05-15-sales-fifo-xirr.md`](../specs/2026-05-15-sales-fifo-xirr.md).

## File map

### New

- `domain/model/LotType.kt`
- `domain/model/FinancialAssetReturns.kt`
- `domain/usecase/ComputeFinancialReturnsUseCase.kt`
- `util/Xirr.kt` — pure bisection
- `test/.../usecase/ComputeFinancialReturnsUseCaseTest.kt`
- `test/.../util/XirrTest.kt`

### Edited

- `data/db/entity/FinancialLotEntity.kt` — `lotType: LotType`
- `data/db/entity/Mappers.kt` — preserve type on map
- `data/db/dao/FinancialDao.kt` — `sumLotQuantity` switched to signed sum, `earliestLotDate` unchanged
- `data/db/AppDatabase.kt` — version 8, `MIGRATION_7_8`
- `data/repository/FinancialRepository.kt` — validation hook before lot insert/update
- `domain/model/FinancialAsset.kt` — `totalQuantity` becomes net; expose lots untouched
- `domain/model/FinancialLot.kt` — add `type` (default BUY)
- `domain/usecase/AddLotUseCase.kt` / `UpdateLotUseCase.kt` — propagate validation result via thrown `IllegalArgumentException`
- `ui/feature/assets/financial/addlot/AddLotScreen.kt` + `AddLotViewModel.kt` — segmented selector, label swap, error surfacing
- `ui/feature/assets/financial/detail/FinancialDetailScreen.kt` + `FinancialDetailViewModel.kt` — show returns, mark SELL rows
- `schemas/com.atlasfpt.data.db.AppDatabase/8.json` — generated

## Steps

1. **Domain enum + model.** Add `LotType`. Extend `FinancialLot.type`. Update `FinancialAsset.totalQuantity` to compute net (BUY − SELL). Leave `totalCost`/`avgCostPerUnit` for now but rewire the detail screen to read from `FinancialAssetReturns` so the stale "total cost" line goes away.
2. **DB entity + migration.** Add `lotType` column, default 'BUY'. Write the migration string mirroring the Room generator's verbatim text (verify via the generated `8.json` after build).
3. **DAOs.** `sumLotQuantity` becomes `SUM(CASE WHEN lotType='BUY' THEN quantity ELSE -quantity END)`. Existing call sites (price update, recompute snapshot, add-lot validation) continue to work because they treated the value as net.
4. **Validation.** In `FinancialRepository.addLot` + `updateLot`, after inserting/updating inside the transaction, compute the cumulative running net across the asset's lots in date-then-id order. If any prefix is negative, **roll back by throwing**. Room rolls back the transaction.
5. **Use case.** `ComputeFinancialReturnsUseCase.invoke(asset: FinancialAsset, currentPrice: Double?, today: LocalDate)`: runs FIFO over the sorted lots, computes realized P&L and the leftover pool, then unrealized + total return, then calls `Xirr.solve(flows)`. Pure — easy to unit test.
6. **XIRR util.** Bisection on `[-0.99, 100]` until span < 1e-9 or 60 iters. Function signature `solve(flows: List<Pair<LocalDate, Double>>, baseDate: LocalDate): Double?`. Daily compounding: `factor = (1 + r) ^ (-days/365.0)`.
7. **UI lot form.** Segmented selector (SegmentedButton or two Filter chips — pick FilterChip for compatibility with Compose Material 3). Save validates via VM `formErrors` AND surfaces backend `IllegalArgumentException` as a snackbar.
8. **UI detail.** Replace "avg yearly yield" row with a "Returns" section: Realized P&L (signed), Unrealized P&L (signed), Total Return (signed + %), XIRR (%, "—" when null). Mark SELL rows with a chip + negative quantity glyph.
9. **Tests.**
   - `XirrTest`: a 2-flow series with known annualised IRR.
   - `ComputeFinancialReturnsUseCaseTest`: 3 buys + 2 sells, verify realized P&L, leftover pool size, unrealized P&L with a fixed price, XIRR sign.
   - `FinancialRepositoryValidationTest` (unit test with mocks): selling more than held throws.

## Risks

- **Existing tests that build a `FinancialLot` without `type`** — the default makes them keep compiling, but `CalculateYieldUseCaseTest` may end up asserting on the removed CAGR. Decision: keep `calculateAvgYearlyYield` in the codebase, just stop calling it from the screen. Frees us from updating its tests in this PR.
- **Validation race conditions.** Insert-then-check is fine inside `withTransaction`; the throw rolls back. Don't do `check + insert` (TOCTOU).
- **Schema string drift.** Build once, copy verbatim into `MIGRATION_7_8` from the generated `8.json`'s `createSql`. Confirm hash before committing.

## Verification

- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug` (8.json must appear)
- Manual emulator: add a BUY 100 @ 10 EUR; add SELL 30 @ 12 EUR; detail shows realized P&L = 60, net qty = 70; try to SELL 999 — blocked.
