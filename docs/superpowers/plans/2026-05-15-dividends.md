# Plan: Dividends tracking + total return (#27)

Spec: [`docs/superpowers/specs/2026-05-15-dividends.md`](../specs/2026-05-15-dividends.md).

## Files

### New

- `domain/model/Dividend.kt`
- `data/db/entity/DividendEntity.kt`
- `data/db/dao/DividendDao.kt`
- `data/repository/DividendRepository.kt`
- `domain/usecase/AddDividendUseCase.kt`
- `domain/usecase/UpdateDividendUseCase.kt`
- `domain/usecase/DeleteDividendUseCase.kt`
- `ui/feature/assets/financial/dividend/AddDividendScreen.kt` + `AddDividendViewModel.kt`
- `test/.../domain/usecase/ComputeFinancialReturnsUseCaseDividendsTest.kt`

### Edited

- `data/db/AppDatabase.kt` — v9, register `DividendEntity`, `MIGRATION_8_9`
- `data/db/entity/AssetWithFinancial.kt` — `@Relation` for dividends
- `data/db/entity/Mappers.kt` — map dividend entity to domain and propagate in `AssetWithFinancial.toFinancialDomain`
- `domain/model/FinancialAsset.kt` — `dividends: List<Dividend>`
- `domain/model/FinancialAssetReturns.kt` — `dividendIncome: Double`
- `domain/usecase/ComputeFinancialReturnsUseCase.kt` — sum + XIRR flows
- `di/DatabaseModule.kt` — provide `DividendDao`
- `ui/navigation/AppNavGraph.kt` — register `Screen.AddDividend.route` (`add_dividend/{assetId}?dividendId={id}`) and `Screen.AddDividend.createRoute` / `createRouteEdit`
- `ui/feature/assets/financial/detail/FinancialDetailScreen.kt` + `FinancialDetailViewModel.kt` — dividend list, "+ Add" action, returns block updates
- `ui/feature/assets/financial/detail/FinancialDetailViewModelTest.kt` — pass the new computeReturns wiring (already taken via the existing default ctor)
- `app/schemas/com.atlasfpt.data.db.AppDatabase/9.json` — generated

## Steps

1. Domain `Dividend`. DB entity + DAO. Migration string copied from the generated schema after the first build.
2. Repository CRUD with transactional snapshot recompute (`recomputeAssetSnapshotInTxn` doesn't need to change because dividends don't affect quantity).
3. Use cases. Each is a one-liner delegating to the repository.
4. AssetWithFinancial relation + mapper extension. `FinancialAsset.dividends` defaults to empty.
5. `ComputeFinancialReturnsUseCase` updates:
   - Compute `dividendIncome = asset.dividends.sumOf { it.grossAmount }`.
   - Add dividend flows to the XIRR cashflow list (positive on `payDate`).
   - Returns object exposes `dividendIncome`; total return adds it.
6. Add dividend screen + VM. Mirrors AddLotScreen's MVVM structure. Save → AddDividendUseCase or UpdateDividendUseCase.
7. Detail screen renders dividends section with "+ Add" and shows "Dividends" line in the Returns block.
8. Tests:
   - Compute test: 1 BUY + 1 dividend → realized 0, dividendIncome correct, total return adds dividends, XIRR positive.
   - VM/Repository tests where they happen to break.

## Risks

- **Relation ordering.** `lots` are sorted by purchaseDate in the mapper; dividends should also be sorted (helps the UI). Sort by `payDate ASC, id ASC`.
- **Schema string identity hash.** Build first, copy from `9.json`'s `createSql`.
- **VM ctor explosion.** FinancialDetailViewModel already injects `ComputeFinancialReturnsUseCase`; the returns calc reads dividends from the loaded asset, so the VM ctor doesn't need new params.

## Verification

- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug` and confirm `9.json` exists
- Manual: emulator add a dividend, confirm it appears in the list and the Total return moves accordingly.
