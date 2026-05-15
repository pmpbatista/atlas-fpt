# Spec: Asset charts — price history + portfolio value (#29)

## Problem

Financial-asset detail shows numeric stats only — no charts. The user wants to see how a holding's price has trended and how the whole portfolio's value has evolved.

## Decisions

- **Source of price history:** Yahoo Finance `/v8/finance/chart/SYMBOL` (already used for live price). The response includes `timestamp[]` + `indicators.quote[].close[]`. No persistence — fetched on demand per range selection.
- **Range selector:** `1M`, `6M`, `1Y`, `All`. Mapped to Yahoo `range` query (`1mo` / `6mo` / `1y` / `max`).
- **Interval:** `1d` for the first three ranges; `1wk` for `All` (to keep point counts reasonable).
- **Chart library:** keep the existing pattern — a pure-Compose Canvas line chart. Vico is in the stack but the existing `CashFlowBarChart` is hand-drawn and matches the codebase style.
- **Portfolio value:** computed client-side from each financial asset's history + the asset's lot ledger. For each daily timestamp in the source, value = `Σ_assets ( net_qty_held_at(t) × price_at(t) )`. Real-estate assets contribute their current value as a constant horizontal series for the duration of the chart (their value isn't time-series tracked).
- **Caching:** in-memory per ViewModel session is enough — the user typically swaps ranges, not assets, so re-fetching on range change is fine. No DB-backed cache.

## UI

- **Financial detail screen** gains a chart card directly under the AggregatedStatsCard:
  - Title row: "Price history" + range chips (1M / 6M / 1Y / All).
  - Body: line chart of close prices.
  - Empty state: "No price data" when the fetch yields nothing.
- **Assets screen** gains a chart card directly under `TotalWealthHeader`:
  - Title row: "Portfolio value" + the same range chips.
  - Body: line chart of summed equity over time, in the user's display currency. When the user holds a mixed-currency portfolio, the chart aggregates in the display currency using cached FX rates; if any rate is missing, the chart shows "Convert FX to chart portfolio value".

## Out of scope

- Persisting price history.
- Drag-to-zoom, hover crosshair, exact-value tooltips. Just the line.
- Real-estate value-time series — they show as a constant baseline contribution.
- Comparing multiple assets on one chart.

## Data flow

```
YahooFinanceApi.getChart(symbol, range, interval) -> ChartResponse {
  result[0].timestamp: List<Long>      // unix seconds
  result[0].indicators.quote[0].close: List<Double?>
}
```

`GetPriceHistoryUseCase(ticker, range)` maps to `List<PricePoint(date, price)>`. Drops null prices (holidays / pre-IPO).

`GetPortfolioValueHistoryUseCase(range, displayCurrency, rates)`:
1. Load all financial assets via `AssetRepository.observeAssetList()`.
2. For each financial asset, fetch its price history.
3. Build a unified date axis as the union of all dates.
4. At each date, compute the per-asset position value (net qty × price-at-or-before, converted to display currency), sum, add real-estate constant.

## Acceptance

- Asset detail screen shows a price-history line chart with a range chip row; switching the range refetches.
- Assets screen shows a portfolio-value line chart, also with range chips.
- Empty / error states are explicit ("No price data" / "Couldn't load chart").
- Unit test: `GetPortfolioValueHistoryUseCase` aggregates a 2-asset, 3-date toy dataset to the expected per-date totals.

## Open questions, resolved

- **Should the portfolio chart respect the display currency from #28?** Yes — totals on the Assets screen are already converted, charts should match.
- **What about transactions linked to assets?** Those are spending, not value. Ignore for chart.
- **Real estate.** Constant baseline. Acceptable: the user's mortgage payments aren't time-series tracked, and the issue is explicit about "price history per **financial** asset".
