# Timeline: align screen with intended design (cash-flow chart + richer rows) — Design Spec

**Date:** 2026-05-08
**Issue:** #7
**Source:** Timeline: align screen with intended design (cash-flow chart + richer rows)

## Goal

Re-skin `TimelineScreen` and `TransactionRow` to match the intended mock: the cash-flow header gains a 6-month income-vs-expense bar chart with grid lines and a "Spending Overview" deep-link, the date headers switch to relative labels (Today / Yesterday / `MMM d`), the screen renders the previously-unused `scheduledTransactions` list as a pinned summary row, and each transaction row uses the actual category icon on a fully-saturated colored circle with a pencil-prefixed note line. Two open questions in the issue (wallets, search) are settled with explicit punts.

## Non-goals

- **Wallets / accounts.** No `Wallet` entity, no FK on `Transaction`, no UI for managing wallets. This means gap #3's "All Wallets" filter chip and gap #8's per-row wallet line are **not** shipped here.
- **Transaction search.** The search icon is added to the header but its tap surfaces a "Coming soon" snackbar; a real search feature is a separate epic.
- **Charts library swap.** The bar chart is hand-rolled in `Canvas` to stay consistent with the existing donut on Overview. Vico is in the dep list but not used here.
- **Animations / transitions** between header and chart, or between months.
- **Multi-currency timeline totals** — the existing single-`currencySymbol` rendering is preserved.
- **Period grouping selector** ("By months" chip in the mock, gap #3) — see Decisions §3.

## Decisions

### 1. Header — multi-month cash-flow bar chart

`CashFlowHeader` is rebuilt as a column:

- The total cash-flow line stays at the top (`headlineMedium`, color-coded).
- Below it, a `MultiMonthBarChart` composable renders the last 6 months.
- The composable consumes the existing `monthlySummaries: List<MonthlySummary>` already produced by `GetTimelineUseCase`. It needs no new data plumbing.
- Each month is two adjacent bars (income green, expense red) anchored at a shared zero baseline, with the current month's pair drawn in fully saturated `IncomeColor` / `ExpenseColor` and the prior five drawn at 60% alpha.
- Three horizontal grid lines at evenly spaced y-values (0, max/2, max) are drawn behind the bars, with their values rendered as `labelSmall` text on the right edge in `onSurfaceVariant`. The max is rounded up to a "nice" number (`roundedUpToTwoSigFig`).
- A small month label (`Jan`, `Feb`, …, `MMM` localized) sits below each bar pair.
- The 6 months are computed from "today's `YearMonth` minus 5" up to today. If `monthlySummaries` returns fewer than 6 (early adopters, no historical data), the missing slots render as zero-height bars with the month label still present.
- The chart is fixed-height (140dp) and full-width with 16dp horizontal padding to match the rest of the screen.

### 2. Header — search action (stubbed)

A small `IconButton` with `Icons.Default.Search` sits in the top-right of the header column. Tapping it triggers a snackbar `"Search is coming soon"`. The button exists so the screen visually matches the mock; the wiring is intentional — when search lands, the only change is replacing the snackbar callback with a navigation call.

A separate follow-up issue is filed: *"Timeline: text + filter search across transactions"*.

### 3. Header — filter chips

The mock shows two chip-style dropdowns under the total: "All Wallets ⌄" and "By months ⌄".

**Decision: ship neither in this spec.**

- "All Wallets" is gated on a `Wallet` domain entity that doesn't exist yet. The issue explicitly flags this. A disabled placeholder chip is dishonest UI; a real chip can't be built without first introducing the entity, the FK on `Transaction`, the wallet-management UI, and the seed/default-wallet rules. That's an epic, not a sub-task.
- "By months" looks tractable but materially changes the timeline grouping pivot (date → period). That work isn't only a UI tweak — `TimelineItem.DateHeader` has to become parametric over a period type, and `GetTimelineUseCase.buildItems` rewrites with the period bucketer. It earns its own spec rather than hiding inside the cosmetic-alignment issue.

A follow-up issue is filed: *"Wallets / accounts (epic): entity + FK + management UI + filter chip"*. A second follow-up: *"Timeline: period grouping selector (months / weeks / days)"*.

The Decisions section keeps a record of why these chips don't ship; the visual difference vs the mock is acknowledged and accepted.

### 4. "Spending Overview" deep-link

A pill-style `OutlinedButton` sits below the bar chart, full-width-with-margin, label `"Spending Overview"`, trailing `Icons.AutoMirrored.Filled.ArrowForward`. `onClick` calls `navController.navigate(Screen.Overview.route)`.

It's part of the header `item {}` block, so it scrolls with the chart and is never sticky.

### 5. "Scheduled" summary row

`scheduledTransactions` is currently produced but never rendered. Add a new `TimelineItem.ScheduledSummary(count: Int, total: Double)` variant and emit it from `GetTimelineUseCase.buildItems` as the first item *only when `scheduled.isNotEmpty()`*.

```kotlin
sealed class TimelineItem {
    data class ScheduledSummary(val count: Int, val total: Double) : TimelineItem()
    data class DateHeader(val date: LocalDate, val dailyTotal: Double) : TimelineItem()
    data class TransactionRow(val transaction: Transaction) : TimelineItem()
}
```

The signed total uses the same convention as the rest of the timeline: expenses negative, incomes positive. UI-wise the row is a `Surface(onClick=…)` that contains:

- 40dp circular badge with `Icons.Filled.Schedule` on `MaterialTheme.colorScheme.tertiaryContainer`
- Title `"Scheduled"` (`bodyLarge`)
- Subtitle `"$count transactions"` / `"1 transaction"` (`bodyMedium onSurfaceVariant`)
- Trailing signed amount, color-coded the same way as a transaction row

**Tap target:** opens an inline expansion below the summary row that lists each scheduled transaction using the existing `TransactionRow`. Tap-toggle, no new screen, no new route. Lives entirely in `TimelineScreen` state (a screen-level `mutableStateOf(false)` for `isScheduledExpanded`). When collapsed, only the summary shows. This is intentionally lightweight — a dedicated `ScheduledScreen` is overkill for a list that is usually 0–5 items long.

### 6. Date header — relative formatting

Replace the existing `DateTimeFormatter.ofPattern("EEEE, d MMM", Locale("pt", "PT"))` with a small helper:

```kotlin
fun formatDateHeader(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
}
```

`today` is computed once in `TimelineScreen` from `LocalDate.now()` and passed through. The Portuguese locale is dropped — the rest of the app's strings are English, the mock is English; the previously hard-coded Portuguese was a holdover.

### 7. Transaction row — actual category icon

`TransactionRow.CategoryIcon` stops rendering the first letter and uses `CategoryIconRegistry.resolve(category.iconRes)` to fetch an `ImageVector`. The icon is drawn at 24dp with `tint = Color.White` on the saturated background.

This requires `CategoryIconRegistry` (the `String → ImageVector` lookup specced for issue #6). **Cross-spec dependency:** if #6 ships first, this spec consumes its registry. If this spec ships first, it ships the registry, and #6's spec consumes it. The registry's location (`ui/component/CategoryIconRegistry.kt`) and shape are identical in both specs. Whichever lands first introduces the file — there are no implementation differences to reconcile.

### 8. Transaction row — wallet line

Not shipped (see Decisions §3). The row keeps the existing two-line layout: category name + (optional) note. When wallets land, a third line slots between the category and the note.

### 9. Transaction row — note pencil prefix

When `transaction.note` is non-blank, the note line gets a leading 14dp `Icons.Filled.EditNote` icon (`onSurfaceVariant` tint), 4dp gap, then the note text. Same `bodyMedium`, same single-line ellipsis behavior.

### 10. Transaction row — icon background saturation

Background goes from `bg.copy(alpha = 0.2f)` to a fully saturated `Color(category.color)`. Icon tint flips to white. Contrast is fine for the seeded palette (deep colors like `0xFF795548`, `0xFF3F51B5`, `0xFFFF9800`, `0xFF4CAF50` all have AA-passing contrast against white). Should a future custom-color feature allow pale categories, the spec for that feature must add a `pickReadableForeground(bg)` helper. Until then, white-on-saturated is acceptable.

## Affected components

```
app/src/main/java/com/spendtrack/
├── domain/usecase/GetTimelineUseCase.kt        (TimelineItem.ScheduledSummary; buildItems prepends it)
└── ui/
    ├── component/
    │   ├── CategoryIconRegistry.kt              (NEW or shared with issue #6 spec)
    │   └── TransactionRow.kt                    (saturated badge + actual icon + pencil-prefixed note)
    └── feature/timeline/
        └── TimelineScreen.kt                    (header rebuild: bar chart, search-stub, deep-link button;
                                                  scheduled-summary row + inline expansion;
                                                  relative date headers)
```

`TimelineViewModel` is touched only to expose `LocalDate.now()` once for the relative-date helper (or, simpler, the screen reads it directly each composition — a few `compareTo` calls per recomposition is fine; we don't need to inject a clock for this).

The `Locale("pt", "PT")` import in `TimelineScreen.kt` is removed.

## Implementation Notes

### Bar chart geometry

```kotlin
@Composable
private fun MultiMonthBarChart(
    monthlySummaries: List<MonthlySummary>,
    currencySymbol: String,
    today: YearMonth = YearMonth.now(),
    modifier: Modifier = Modifier,
)
```

- Build a list of 6 `YearMonth` keys: `today.minusMonths(5)..today`. For each, look up the corresponding `MonthlySummary` (key = `"YYYY-MM"`); fall back to zeros if missing.
- The y-scale max = `niceCeil(max(allIncomes ∪ allExpenses))`, with a floor of 1.0 to avoid divide-by-zero when the user's history is empty. `niceCeil` rounds up to two significant figures (`7341 → 7500`, `13800 → 15000`).
- Bar width = `(slot - innerGap) / 2` where `slot = canvasWidth / 6`. innerGap is 4dp.
- Bars draw upward from a fixed baseline at `canvasHeight - labelHeight` (24dp reserved for month labels).
- Grid lines at y = 0, max/2, max — drawn first, behind the bars, with their values shown as text on the right edge.

This is small enough to inline in `TimelineScreen.kt` rather than spinning out a new component file. If reuse needs justify it later, extract.

### Scheduled-summary expansion

```kotlin
val (scheduledExpanded, setScheduledExpanded) = remember { mutableStateOf(false) }
```

In the `LazyColumn`:

```kotlin
val firstItem = data.timelineItems.firstOrNull()
if (firstItem is TimelineItem.ScheduledSummary) {
    item("scheduled_summary") {
        ScheduledSummaryRow(
            count = firstItem.count,
            total = firstItem.total,
            currencySymbol = symbol,
            isExpanded = scheduledExpanded,
            onToggle = { setScheduledExpanded(!scheduledExpanded) },
        )
    }
    if (scheduledExpanded) {
        items(data.scheduledTransactions, key = { "sched_${it.id}" }) { tx ->
            TransactionRow(transaction = tx, currencySymbol = symbol, onClick = { /* edit nav */ }, onDelete = { /* delete */ })
        }
    }
}
```

The summary row's chevron rotates 0° → 180° on expand. Use `animateFloatAsState`.

### Date headers

`DateHeader` is unchanged structurally — only its formatting helper swaps. Pass `today: LocalDate = LocalDate.now()` from the screen's recomposition scope so date arithmetic is deterministic per composition.

### TransactionRow note line

```kotlin
if (!transaction.note.isNullOrBlank()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.EditNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = transaction.note,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
```

`contentDescription = null` because the text is the accessible label.

### `GetTimelineUseCase` change

Single insertion in `buildItems`:

```kotlin
private fun buildItems(transactions: List<Transaction>, scheduled: List<Transaction>): List<TimelineItem> {
    val items = mutableListOf<TimelineItem>()
    if (scheduled.isNotEmpty()) {
        val total = scheduled.sumOf { tx ->
            if (tx.type == TransactionType.EXPENSE) -tx.amount else tx.amount
        }
        items += TimelineItem.ScheduledSummary(scheduled.size, total)
    }
    val grouped = transactions.groupBy { it.date }
    grouped.entries
        .sortedByDescending { it.key }
        .forEach { (date, txs) ->
            val dailyTotal = /* unchanged */
            items += TimelineItem.DateHeader(date, dailyTotal)
            txs.forEach { items += TimelineItem.TransactionRow(it) }
        }
    return items
}
```

Pass `scheduled` through from the existing `combine` block. The `TimelineData.scheduledTransactions` field stays in place for the inline-expansion lookup.

## Testing

### Unit tests

- `GetTimelineUseCaseTest` — gets new tests:
  - Empty `scheduled` → no `ScheduledSummary` in `timelineItems`
  - Two scheduled (one expense -10, one income +30) → first item is `ScheduledSummary(count=2, total=20.0)`
  - The unchanged date-grouping behavior is preserved (regression)
- `TimelineDateHeaderFormatTest` (new pure-function test):
  - `today` → `"Today"`
  - `today.minusDays(1)` → `"Yesterday"`
  - `today.minusDays(2)` → matches `"MMM d"` pattern
- `TimelineBarChartScalingTest` (new pure-function test on `niceCeil`):
  - `niceCeil(0) → 1.0`
  - `niceCeil(7341) → 7500`
  - `niceCeil(13800) → 15000`
  - `niceCeil(245) → 250`

### Manual verification checklist

1. Open Timeline on a fresh seed → header shows total, bar chart with one current-month bar pair, others zero
2. Add five months of historical transactions via CSV import → bar chart fills in, current month brighter than the rest
3. Tap the "Spending Overview" pill → navigates to Overview
4. Tap the search icon → snackbar "Search is coming soon"
5. With at least one scheduled transaction (recurring rule already materialized future entries), the "Scheduled" summary row appears at the top of the list with `N transactions` and signed total
6. Tap the Scheduled row → expands inline with one row per scheduled transaction; chevron rotates
7. Tap again → collapses
8. Date headers show "Today" for today, "Yesterday" for yesterday, "May 4" for older
9. Transaction rows show the actual category icon (e.g. `home`, `directions_car`, `payments`) on a saturated colored circle, white tint
10. A row with a note → note line begins with a small pencil icon
11. Swipe-to-delete still works (regression check)
12. Tap a regular row → navigates to edit (regression check)

## Out of Scope

- Wallets / accounts (separate epic; gates the wallet filter chip and the per-row wallet line)
- Period grouping selector (months / weeks / days; separate spec)
- Real text-search feature (entry-point stub only here)
- Drag-to-reschedule, complete-now actions on scheduled rows
- A dedicated scheduled screen (inline expansion is sufficient)
- Charts library migration (current `Canvas`-based pattern stays)
- Recurring-rule edits from inside the inline expansion
