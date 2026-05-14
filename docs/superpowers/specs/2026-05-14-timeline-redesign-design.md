# Timeline Screen — Redesign (#7)

## Overview

Align Timeline to the intended design: a header "Cash Flow" total, two filter chips, a monthly income-vs-expense bar chart, a "Spending Overview" shortcut, a "Scheduled" rollup section, and a richer per-day grouped transaction list with cleaner rows.

Target visual: `docs/design/timeline-target.jpeg`.

## Affected Files

- `app/src/main/java/com/atlasfpt/ui/feature/timeline/TimelineScreen.kt` — full rewrite of the body
- `app/src/main/java/com/atlasfpt/ui/feature/timeline/TimelineViewModel.kt` — extend state for header total, monthly bars, scheduled rollup, and day-grouped rows
- `app/src/main/java/com/atlasfpt/ui/component/TransactionRow.kt` — extend with wallet line and note line (or rewrite if too divergent)
- New: `app/src/main/java/com/atlasfpt/ui/component/CashFlowBarChart.kt`
- `app/src/main/java/com/atlasfpt/ui/navigation/AppNavGraph.kt` — wire "Spending Overview" link to the existing Overview destination

## State Changes

`TimelineUiState`:

```kotlin
data class TimelineUiState(
    val headerTotal: Double = 0.0,           // cash flow over the visible window
    val walletFilter: WalletFilter = WalletFilter.All,
    val rangeMode: RangeMode = RangeMode.ByMonths,
    val bars: List<MonthBar> = emptyList(),  // up to last 6 months, oldest → newest
    val scheduled: ScheduledRollup? = null,  // null if no future-scheduled tx
    val days: List<DayGroup> = emptyList(),  // grouped, descending by date
    val isLoading: Boolean = false,
)

data class MonthBar(val month: YearMonth, val income: Double, val expense: Double, val isCurrent: Boolean)
data class ScheduledRollup(val count: Int, val net: Double)
data class DayGroup(val date: LocalDate, val net: Double, val rows: List<TransactionRowItem>)
data class TransactionRowItem(
    val transaction: Transaction,
    val walletLabel: String,    // for now: a fixed "Wallet" string until wallets land
    val noteLine: String?,      // transaction.note, but only when non-blank
)
```

`RangeMode` and `WalletFilter` are sealed/enums. `WalletFilter.All` is the default; the wallet concept is not yet modelled, so the chip is rendered but the dropdown contains only "All Wallets" for this iteration — keep the surface so a future Wallets feature can drop in.

## Components

### Header

- `Text(headerTotal, style = headlineLarge)` centered. Two-decimal currency format. Sign-coloured (green if ≥ 0, red if < 0).
- Caption `Text("Cash Flow", style = bodyMedium, dim)` immediately below.
- Trailing `IconButton(Icons.Filled.Search) {}` top-right (search not implemented yet — render disabled or no-op; create a follow-up issue if desired).

### Filter chips

Row of two `AssistChip`s:

- "All Wallets ▾" — clickable. For now a no-op dropdown placeholder.
- "By months ▾" — clickable; opens a small menu offering "By months" / "By weeks". Default and only behaviour for v1: months.

### Bar chart

`CashFlowBarChart(bars: List<MonthBar>, modifier: Modifier)`. Renders:

- Canvas, height ~160dp.
- Two horizontal dashed grid lines: at `0` and at `maxOf(income, expense) / 2` and `max`. Labels left of axis (e.g. `0k`, `7.5k`, `15k`) — short formatting (`"%dk"`).
- For each month: two adjacent bars (income green, expense red), corner-rounded 4dp, evenly spaced across the canvas width.
- The latest month uses a lighter / brighter shade to read as "current".
- Month label `Text("MMM\nYYYY")` (two-line) under each bar pair, `bodySmall`, centered.
- The chart is `Modifier.fillMaxWidth().padding(horizontal = 16.dp)`.

### Spending Overview pill

A pill-shaped `Surface(shape = CircleShape)` with leading donut icon, label "Spending Overview" and trailing chevron. Tapping it navigates to `Screen.Overview.route`.

### Scheduled rollup

If `scheduled != null`:

- `Row` matching the transaction-row visual: leading icon (`Icons.Filled.Schedule` in a muted-grey circle), title "Scheduled", sub-text "$count transactions", trailing amount coloured by sign.
- Tap → no-op for v1. Follow-up issue: drill into the scheduled list.

### Day groups + rows

`LazyColumn`:

- Day header: `Text` with date label (`"Yesterday"`, `"Today"`, or `"MMM d"` for older days, Portuguese locale), and trailing `net` total. Slightly dimmer surface bg, full-width.
- Each `TransactionRow`:
  - Leading 40dp circle filled `category.color`, with the category icon centered (white tint).
  - First line: category name (`titleMedium`).
  - Second line: wallet icon + `walletLabel` (`bodySmall`, dim).
  - Third line (only if note present): pencil icon + note text (`bodySmall`, dim).
  - Trailing amount (`titleMedium`, red for expense, green for income).
- Tap row → navigate to `Screen.EditTransaction.createRoute(id)` (existing behaviour).

### FAB

Existing `FloatingActionButton` for adding a transaction — keep, no change.

## Date Bucketing Rules

- `"Yesterday"` for `LocalDate.now().minusDays(1)`.
- `"Today"` for `LocalDate.now()`.
- All other dates: locale-formatted day + month (e.g. `"May 4"` in Portuguese: `"4 de maio"` — use `DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("pt", "PT"))`).

## Wallet Filter Surface

The "All Wallets" chip is rendered but inactive in this iteration. Document a TODO and a follow-up issue. Do **not** introduce a `Wallet` model now — out of scope.

## Out of Scope

- Search (top-right icon is rendered but disabled — file a follow-up issue).
- Switching between "By months" and "By weeks" is stubbed in the dropdown; only months data is computed for v1.
- True multi-wallet support.

## Testing

- Unit: `TimelineViewModel` produces `bars` over the last six months with correct income/expense splits; `days` ordered newest-first; `scheduled` populated only when future-dated transactions with `isScheduled = true` exist.
- Manual: scroll the list, switch month range (when more than one option exists), confirm bar chart matches the underlying data, tap "Spending Overview" → navigates to Overview, tap a row → edit screen.
- Visual regression: compare to `docs/design/timeline-target.jpeg`.

## Known Risks

- Drawing the bar chart by hand on `Canvas` is fine for 6 bars; if bar count grows, consider migrating to Vico (already in stack) — out of scope for v1.
- Bucketing scheduled transactions: the existing `isScheduled` flag + future `date` are the source of truth. Verify the worker materialises with `isScheduled = true` before assuming this query works.
- Locale formatting: confirm Portuguese month names render correctly on emulator (default device locale may be en_US).
