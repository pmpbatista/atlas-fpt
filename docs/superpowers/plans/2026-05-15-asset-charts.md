# Plan: Asset charts (#29)

Spec: [`docs/superpowers/specs/2026-05-15-asset-charts.md`](../specs/2026-05-15-asset-charts.md).

## File map

### New

- `domain/model/PricePoint.kt`
- `domain/usecase/GetPriceHistoryUseCase.kt`
- `domain/usecase/GetPortfolioValueHistoryUseCase.kt`
- `ui/component/LineChart.kt` — pure Compose Canvas, mirrors `CashFlowBarChart`
- `ui/component/ChartRangeChips.kt`
- `test/.../domain/usecase/GetPortfolioValueHistoryUseCaseTest.kt`

### Edited

- `data/network/YahooFinanceDtos.kt` — add `timestamp` and `indicators` fields
- `data/repository/PriceRepository.kt` — passthrough for `fetchChart(ticker, range, interval)`
- `ui/feature/assets/financial/detail/FinancialDetailScreen.kt` + `FinancialDetailViewModel.kt` — range state, price-history fetch, chart card
- `ui/feature/assets/list/AssetsListScreen.kt` + `AssetsListViewModel.kt` — portfolio-value range + chart

## Steps

1. **DTO extension.** Make `ChartResult` include `timestamp: List<Long>?` and `indicators: ChartIndicators?`. Add `ChartIndicators(quote: List<ChartQuote>)` and `ChartQuote(close: List<Double?>?)`. Keep all fields nullable so existing code paths (which only read `meta`) still work.
2. **PriceRepository.fetchChart(ticker, range, interval)** — thin wrapper returning `List<PricePoint>` (filters nulls, converts timestamp to LocalDate UTC).
3. **GetPriceHistoryUseCase** delegates to the repository.
4. **GetPortfolioValueHistoryUseCase**:
   - Inject `FinancialRepository`, `AssetRepository`, and `PriceRepository`.
   - Fetch financial assets + their lots (via `FinancialRepository.getById` for each).
   - Fetch price history per ticker in parallel (`coroutineScope { async { ... } }`).
   - For each per-asset history, walk dates: position = sum of buys-on-or-before − sum of sells-on-or-before; convert to display currency via the passed-in rates map.
   - Union dates across assets; for any date missing from an asset's history, carry forward the previous price (last-known-price-before-date).
   - Add real-estate value as a constant for the chart's date window.
5. **LineChart.kt** — simple Canvas: derive min/max y, draw polyline, light grid lines, no axis labels other than first/last x. Caller passes a list of (LocalDate, Double).
6. **ChartRangeChips.kt** — Compose `FilterChip` row for 1M / 6M / 1Y / All, single-select.
7. **Wire UI:** FinancialDetailScreen embeds a `PriceHistoryCard` reading from VM state; AssetsListScreen embeds a `PortfolioValueCard`. Both VMs expose `Range` + `history: List<PricePoint>` + `isLoading`.
8. **Test:** Construct two fake assets with deterministic histories and lots; assert totals.

## Risks

- **Yahoo response shape variability** — when there's an error or symbol unknown, `result == null`. Treat as empty.
- **Currency mixing** — historic FX is not stored. Portfolio chart uses current FX rates uniformly, which slightly distorts older points; the spec accepts this trade-off.
- **Coroutine scope** — fetching N tickers in parallel must respect VM lifetime. Use `viewModelScope`.

## Verification

- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
- Manual: open a tracked asset, swap ranges; open Assets, swap ranges.
