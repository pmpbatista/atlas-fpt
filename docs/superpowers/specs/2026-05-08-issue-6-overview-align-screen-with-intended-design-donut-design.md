# Overview: align screen with intended design (donut + category list) — Design Spec

**Date:** 2026-05-08
**Issue:** #6
**Source:** Overview: align screen with intended design (donut + category list)

## Goal

Re-skin `OverviewScreen` to match the original mock: a "Categories" header, a dual-total segmented toggle, a richer donut that uses category colors and floats a category icon + percentage outside each slice, and a list of category rows that use circular icon badges and a transaction-count subtitle. Remove the standalone "Total" row that's redundant once the toggle pills carry the totals.

## Non-goals

- Time-window pickers other than the existing `MonthSelector`.
- Drilldown from a list row to a filtered transaction list (that's a Timeline concern, tracked in issue #7).
- Multi-currency overview totals — the existing single-`currencySymbol` rendering is preserved.
- Migrating `Category.iconRes` to `@DrawableRes` ints. The current string-keyed schema stays; we add a UI-side `String → ImageVector` lookup.
- Charts library swap. The current hand-rolled `Canvas` donut continues; we extend it.
- Animations / transitions when switching pills or months.
- Inner-ring "comparison" data source (see decision below — punted to a follow-up).

## Decisions

### 1. Screen header

Add a bold `"Categories"` `Text` (`MaterialTheme.typography.headlineSmall`, `FontWeight.Bold`) at the top of `OverviewScreen`, above the segmented toggle, with horizontal padding matching the rest of the screen (16dp).

### 2. Dual-total segmented toggle

Replace the `TabRow` with two side-by-side pill buttons. Each pill shows:
- An amount line (`titleMedium`, signed and color-coded — `−€2,440.57` in `ExpenseColor`, `€8,652.15` in `IncomeColor`)
- A small label line (`labelSmall`, `onSurfaceVariant`) — `"Expenses"` / `"Income"`

The selected pill carries a filled `MaterialTheme.colorScheme.surfaceVariant` background and the unselected pill is a thin outlined border. Tapping flips the selection. Both totals stay visible at all times — that's the whole point of the change.

We do *not* use `SegmentedButton` from M3 because its prescribed inner layout (icon + single-line label, with check mark on selection) doesn't fit two-line numeric content. A `Row` of two `Surface(onClick=…)` cards gives the layout we want with no fight.

### 3. Donut segment colors

`DonutChart` stops generating HSL colors and uses `Color(item.category.color)` directly. The `accentColor` parameter is removed — it now only controls the *toggle* pill colors, not the donut.

For categories whose stored `color = 0` (theoretical edge case; the seeded set has no zeroes), fall back to `MaterialTheme.colorScheme.outline` so a slice never renders fully transparent.

### 4. Donut icons + percentages around the ring

Re-architect `DonutChart` from a single `Canvas` to a `Box` containing:
- One `Canvas` that draws the arcs as today.
- A `Layout` (or per-slice absolute-positioned `Composable`s) that places, for each slice whose `percentage` clears a visibility threshold, a small icon badge + a percentage label outside the ring.

Per-slice overlay placement:
- Compute the slice midpoint angle: `startAngle + sweepAngle / 2`.
- Project to a point at radius `outerRadius * 1.18` from the center.
- Place a 28dp circular `Box(background = Color(category.color))` containing the resolved `ImageVector` (Material icon, white tint).
- Place a small `Text(percentage)` next to the badge, color-matched to the slice color.

**Visibility threshold for icon overlays:** `percentage >= 4.0f`. Below that, the slice still draws, but the icon and percentage are suppressed to avoid overlap. The list view always shows the row, so nothing is hidden from the user — only crowded labels are.

If two adjacent over-threshold slices' midpoints fall within ~14° of each other, only the larger one shows its label. This is a simple greedy de-overlap; the mock has so few visible labels that it suffices.

### 5. Per-segment percentage labels (out of the list rows)

The list-row subtitle is no longer the percentage. The percentage now lives only on the chart overlay (gap 4). The list-row subtitle becomes the transaction count (gap 7).

### 6. Inner concentric ring

**Decision: not implemented in this spec.** The mock shows a thinner darker ring inside the main donut, but its semantics aren't specified by the issue ("Open question: What does the inner ring represent?"). Three candidates were weighed:

- *Pure styling.* Drawing a ring that means nothing is dishonest UI on a screen whose entire purpose is to communicate where money went. Rejected.
- *Previous-period comparison (same type, prior month).* Useful, but adds a second flow into `GetOverviewUseCase` and forces an answer to "what color/normalization should it use." Wider in scope than this issue.
- *Budget vs actual.* Requires a budgets feature that doesn't exist.

Ship gaps 1, 2, 3, 4, 5, 7, 8. File a follow-up issue ("Overview: previous-month comparison ring") with the inner ring as the design and the previous-month breakdown as the source. The donut implementation in this spec leaves a clean seam (an unused inner radius range) for that future work.

### 7. List rows: icon badge + transaction-count subtitle

`CategoryBreakdownRow` is rebuilt:

- Leading: 40dp circular badge with `Color(category.color)` background at 100% alpha and the resolved `ImageVector` tinted white.
- Title (`bodyLarge`): `category.name`.
- Subtitle (`bodyMedium onSurfaceVariant`): `"$count transactions"` (singular variant for `count == 1`).
- Trailing: amount, `bodyLarge`, no color tint (the toggle conveys sign already).

Transaction count must be wired through. Today `getCategoryTotals` returns `(categoryId, total)`. We extend the SQL to also return `COUNT(*)`:

```kotlin
data class CategoryTotal(
    val categoryId: Long,
    val total: Double,
    val transactionCount: Int,
)
```

```sql
SELECT categoryId,
       SUM(amount) as total,
       COUNT(*) as transactionCount
FROM transactions
WHERE isScheduled = 0 AND type = :type AND date BETWEEN :from AND :to
GROUP BY categoryId
```

`CategoryBreakdown` carries the count through:

```kotlin
data class CategoryBreakdown(
    val category: Category,
    val amount: Double,
    val percentage: Float,
    val transactionCount: Int,
)
```

`CategoryTotal` is internal to the data layer, so renaming/extending it is contained — only `GetOverviewUseCase` reads it directly.

### 8. Standalone "Total" row

Removed. The toggle pills (gap 2) display both totals; the row beneath the chart is dead weight after that change.

### Category icon registry (cross-cutting for gaps 4 + 7)

`Category.iconRes` is a free-form `String` populated from the seeded set in `AppDatabase` (`"payments"`, `"home"`, `"directions_car"`, `"category"`, …). Today no UI resolves it — `CategoryPickerBottomSheet` renders the first letter instead. We add a single source of truth:

```kotlin
// ui/component/CategoryIconRegistry.kt
object CategoryIconRegistry {
    fun resolve(iconRes: String): ImageVector = when (iconRes) {
        "payments" -> Icons.Filled.Payments
        "home" -> Icons.Filled.Home
        "directions_car" -> Icons.Filled.DirectionsCar
        "school" -> Icons.Filled.School
        "account_balance" -> Icons.Filled.AccountBalance
        "currency_exchange" -> Icons.Filled.CurrencyExchange
        "add_circle" -> Icons.Filled.AddCircle
        "category" -> Icons.Filled.Category
        // …all keys seeded in AppDatabase.kt step 4 below
        else -> Icons.Filled.Category
    }
}
```

The registry MUST cover every `iconRes` value seeded in `AppDatabase.SeedCallback`. The registry implementation step starts with a one-time exhaustive read of that seed list — any new seed value added later must add a registry entry; an unknown value falls through to the `Category` default icon (visible-but-bland).

This consolidates the `String → ImageVector` mapping in one file so the eventual category-edit feature only has one place to update.

## Affected components

```
app/src/main/java/com/spendtrack/
├── data/db/dao/TransactionDao.kt              (CategoryTotal grows transactionCount field; SQL adds COUNT(*))
├── domain/usecase/GetOverviewUseCase.kt       (CategoryBreakdown grows transactionCount)
└── ui/
    ├── component/
    │   └── CategoryIconRegistry.kt            (NEW — String → ImageVector lookup)
    └── feature/overview/
        ├── OverviewScreen.kt                  (full re-layout: header, toggle, donut overlay, list)
        └── OverviewViewModel.kt               (selectedTab moves into VM; tab now drives breakdown selection)
```

`OverviewViewModel` gains a `selectedTab: MutableStateFlow<TransactionType>` and merges it into `OverviewScreenState` so configuration changes don't reset the user's pill selection. `OverviewScreen` reads `state.selectedTab` instead of `remember { mutableIntStateOf(0) }`.

`Category` model and DB schema are untouched.

## Implementation Notes

### DAO change

Existing query callers (`OverviewScreen` only, via `GetOverviewUseCase`) already drop the unused field, so widening `CategoryTotal` is safe. The query continues to be a `Flow<List<CategoryTotal>>` against the same `WHERE isScheduled = 0 AND type = :type AND date BETWEEN :from AND :to` clause.

A unit test on `GetOverviewUseCase` should verify the count flows through with a basic two-transactions-one-category fixture.

### Donut layout

`DonutChart` becomes:

```kotlin
@Composable
private fun DonutChart(
    breakdown: List<CategoryBreakdown>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val side = min(maxWidth, maxHeight)
        // …
        Canvas(Modifier.size(side)) {
            // arcs (uses category.color)
        }
        // overlay layer: Box with absolute offsets, computed via measured constraints
        breakdown.withVisibleLabels().forEach { (item, midAngle, _) ->
            CategoryBadge(
                category = item.category,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { polarToOffset(midAngle, badgeRadiusPx) }
            )
            Text(
                "${item.percentage.toInt()}%",
                color = Color(item.category.color),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { polarToOffset(midAngle, labelRadiusPx) }
            )
        }
    }
}
```

`withVisibleLabels()` is a small extension that pre-computes mid-angles, filters by the 4% threshold, and de-overlaps the 14° rule. Pure list operation, easy to unit-test in isolation.

The leader-line in the mock is cosmetic — a thin 1dp `drawLine` from the slice's outer edge to the badge can be added if it doesn't add noticeable code complexity. Treat it as polish; it can land in the same PR or a small follow-up.

### Toggle composable

Extract a `private @Composable DualTotalToggle(...)` to keep `OverviewScreen` readable:

```kotlin
@Composable
private fun DualTotalToggle(
    expenseTotal: Double,
    incomeTotal: Double,
    selected: TransactionType,
    onSelect: (TransactionType) -> Unit,
    currencySymbol: String,
)
```

Uses two `Surface(onClick=…)` cards in a `Row(modifier = Modifier.fillMaxWidth())`. Selected card uses `containerColor = surfaceVariant`; unselected uses `containerColor = surface` with a 1dp `border`.

### Empty / loading

The existing `isLoading` and `breakdown.isEmpty()` branches are preserved verbatim, except the empty branch must keep the toggle visible (today it returns early before the toggle renders). Move the "No transactions this month" empty state *under* the toggle so the user can switch sides without losing the screen.

## Testing

### Unit tests

- `GetOverviewUseCaseTest` — gets a fresh test for the new field:
  - `transactionCount` flows through `CategoryBreakdown` per category
  - Empty totals → empty breakdown lists, zero counts
- `DonutLabelLayoutTest` (new) — pure-function tests on `withVisibleLabels()`:
  - All slices ≥ 4% → all labels visible
  - One slice < 4% → suppressed
  - Two adjacent slices within 14° → smaller suppressed
- `OverviewViewModelTest` — gets a small new test:
  - Default `selectedTab = EXPENSE`
  - `selectTab(INCOME)` → state reflects new tab; persists across month change

### Manual verification checklist

1. Open Overview on a month with both expenses and income → both totals visible in pills, expense selected by default
2. Tap Income pill → chart and list update; expense pill amount remains visible and not coloured-as-selected
3. Donut slices use category colors (validate with seeded "Salário" green vs "Carro" grey)
4. Category icons appear floating outside slices ≥ 4%; tiny slices (< 4%) draw the arc but no overlay
5. List rows show 40dp colored badge + category name + "N transactions" subtitle
6. Switch month back/forward → selected pill is preserved
7. Empty month → "No transactions this month" message shows below the still-visible toggle
8. Open category picker bottom sheet → first-letter rendering still works (regression check; this spec doesn't change it but a future spec should adopt the new registry)

## Out of Scope

- Inner concentric ring (deferred — see Decisions §6)
- Adopting `CategoryIconRegistry` in `CategoryPickerBottomSheet` (replaces the first-letter rendering — separate small follow-up)
- Tap-to-filter from a list row to Timeline filtered by category
- Localized number formatting beyond the existing `CurrencyFormatter`
- Donut animation when switching pills
