# Overview Screen — Redesign (#6)

## Overview

Align the Overview screen to the intended design. Replace the current tab-row UI with a header dual-card (Expenses / Income), an animated donut chart with category icons orbiting the donut, and a tap-to-toggle dataset that drives both donut and the category list below.

Target visual: `docs/design/overview-target.jpeg`.

## Affected Files

- `app/src/main/java/com/atlasfpt/ui/feature/overview/OverviewScreen.kt` — full rewrite of the body
- `app/src/main/java/com/atlasfpt/ui/feature/overview/OverviewViewModel.kt` — extend state with `selectedSide: TransactionType` and totals split per side; keep month-selection logic
- New: `app/src/main/java/com/atlasfpt/ui/component/CategoryDonut.kt` — reusable donut + orbiting icons

## State Changes

`OverviewUiState` adds:

```kotlin
val selectedSide: TransactionType = TransactionType.EXPENSE  // toggles donut + list dataset
val expenseTotal: Double          // already present? rename if needed
val incomeTotal: Double
val breakdownByCategory: List<CategorySlice>  // ordered desc by abs amount
```

`CategorySlice` model:

```kotlin
data class CategorySlice(
    val category: Category,
    val amount: Double,
    val transactionCount: Int,
    val percentOfSide: Float    // 0f..1f
)
```

ViewModel exposes `onSideSelected(side: TransactionType)`.

## Components

### Header (`Categories`)

- Title `Text("Categories", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)` left-aligned, top padding 16dp.
- Below: month selector (the existing `MonthSelector` component) — keep.

### Segmented totals card

A `Row` with two equal-weight `Card`s, rounded 16dp, spacing 8dp:

- Left card: amount in `colorScheme.error` (or a dedicated `red` from theme), label "Expenses" small caption.
- Right card: amount in green (add `colorScheme.tertiary` or a dedicated `incomeGreen`), label "Income".
- Both cards `.clickable`. Selected card has slightly stronger surface tint (`Modifier.border(2.dp, ...)` or `Card(colors = ...selected)`); unselected card uses default surface.
- Tapping a card calls `viewModel.onSideSelected(EXPENSE|INCOME)`.

Amounts formatted with the configured currency symbol and 2 decimals (use existing `CurrencyFormatter` util).

### Donut

`CategoryDonut(slices: List<CategorySlice>, modifier: Modifier)` — reusable. Size ~280dp. Renders:

1. A `Canvas` drawing a donut with `drawArc` per slice. Each slice uses `category.color` (already an `Int`). Inner radius ≈ 0.55 × outer radius.
2. For each slice ≥ a threshold (e.g. ≥ 2 % of total), position a `CategoryIcon` circle on the orbit just outside the donut at the slice's angular midpoint. Use `Modifier.offset { ... }` with polar→cartesian conversion.
3. From each visible icon, draw a thin line to the donut's edge at that angle (same Canvas pass).
4. Above each icon (radially outward), render the percentage `Text` colored to match `category.color`, format `"%.1f%%"` (drop trailing `.0` to integer if whole).

Slices < threshold are still drawn in the donut but their orbiting icon is suppressed (the unlabelled ring portion appears as solid-color arc).

Centre of donut: leave empty (transparent) — design has empty centre.

Use existing `drawCategoryIcon` if any exists, otherwise render a `Box` with a colored circle background and the category's `iconRes` (currently a String reference).

### Category list

`LazyColumn` below the donut:

- Each row: `Row(verticalAlignment = CenterVertically, horizontalArrangement = SpaceBetween)` with:
  - Leading `Box` 40dp circle, filled `category.color`, hosting the category icon (white tint).
  - Column with category name (`titleMedium`) and "$count transactions" sub-text (`bodySmall`, dim).
  - Trailing amount (`titleMedium`, red for expense / green for income).
- Divide rows with a thin `HorizontalDivider`.
- Tap row → no-op for now (future: drill into category detail; out of scope).

## Theme Additions

Add semantic colors in `ui/theme/Color.kt` if not present:

- `IncomeGreen = Color(0xFF22C55E)`
- `ExpenseRed = Color(0xFFEF4444)`

Expose via `MaterialTheme.colorScheme` extensions or a thin wrapper, e.g. an `extraColors` object. Keep the change minimal.

## Out of Scope

- Drill-down from category list rows (separate issue).
- Per-day or per-week aggregation (still monthly).
- Switching between absolute and percent display in cards.

## Testing

- Unit test: `OverviewViewModel.onSideSelected(INCOME)` flips `selectedSide` and breakdown reflects the income categories.
- Manual: tap each card and confirm donut + list switch dataset.
- Visual regression: compare to `docs/design/overview-target.jpeg` for layout and colour roles.

## Known Risks

- Drawing icons + lines + percentages on a `Canvas` plus overlay `Box`es with precise polar positioning is fiddly. Suggest implementing the donut + icon ring as two layers: a `Canvas` for the donut + lines, then a `Box(modifier = Modifier.fillMaxSize())` with absolutely-positioned `Box`es per icon using `Modifier.absoluteOffset { polarToOffset(angle, radius) }`.
- For very many small slices, percentage labels can overlap; the threshold suppression handles this. May still need a minimum-angular-gap rule between adjacent labels (future polish).
