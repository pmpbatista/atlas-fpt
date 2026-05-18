# Timeline Bar Chart — Diverging Bars + Net-Cashflow Line

## Overview

Replace the Timeline screen's side-by-side income/expense bars with a single column per period: income drawn above a zero line, expense drawn below as negative values. Overlay a line series whose points are the period net (income − expense). The chart's data model, scroll behaviour, mode plumbing, and selection plumbing are unchanged.

## Goals

- Make income/expense per period easier to compare against zero and against each other on a single visual axis.
- Surface the per-period surplus/deficit explicitly via a line series, rather than leaving readers to subtract bar heights mentally.
- Keep the change scoped to one file (`CashFlowBarChart.kt`) — no ViewModel, use case, or domain changes.

## Non-Goals

- Tooltips, hover values, or numeric labels on the line.
- Animated transitions on mode switch.
- A second series for cumulative running net.
- Compose UI test infrastructure (none in project today; this change does not add it).

## Affected Files

- `app/src/main/java/com/atlasfpt/ui/component/CashFlowBarChart.kt` — sole source of behavioural and visual change.
- `app/src/main/java/com/atlasfpt/ui/theme/Color.kt` — add `CashFlowNetColor = Color(0xFFFFD66E)`.

## Data Model

No changes. `CashFlowBar` already exposes `income`, `expense`, `periodStart`, `periodEnd`, `label`. Per-period net is computed inline as `income − expense` when laying out the line.

## Visual Spec

### Canvas geometry

- Total chart height: `BAR_HEIGHT = 160.dp` (unchanged).
- The drawable area is split horizontally at its vertical midpoint by a solid zero line. Income draws upward from this line; expense draws downward.
- Slot width: `SLOT_WIDTH = 52.dp` (unchanged) — preserves existing scroll geometry, tap-row alignment, and the label row beneath the chart.

### Y-scale (symmetric)

```kotlin
val maxAbs = max(bars.maxOf { it.income }, bars.maxOf { it.expense })
    .coerceAtLeast(1.0)
val halfHeight = h / 2f
```

Both halves map `[0, maxAbs]` to `halfHeight`. Bar heights are therefore directly comparable across the zero line.

### Bars

- One column per period, centered at the slot's x-center (no income/expense offset; both bars share the same x range).
- Bar width: `(slotWidth * 0.4f).coerceAtMost(24.dp.toPx())`. Wider than today's per-pair bars because pairs collapse into a single column.
- Corner radius: `4.dp` (unchanged).
- Colors: `IncomeColor` (`#1DB984`) for the upper bar; `ExpenseColor` (`#E05252`) for the lower bar.
- Income bar drawn at `topLeft = (cx − barWidth/2, zeroY − incomeH)`, `size = (barWidth, incomeH)`.
- Expense bar drawn at `topLeft = (cx − barWidth/2, zeroY)`, `size = (barWidth, expenseH)`.

### Grid

- Solid zero baseline at the canvas vertical midpoint: `Color.Gray.copy(alpha = 0.5f)`, 1px.
- Dashed lines at the canvas top (`+maxAbs`) and bottom (`-maxAbs`): `Color.Gray.copy(alpha = 0.3f)`, `PathEffect.dashPathEffect(floatArrayOf(8f, 8f))`.
- The current 50% mid-line is removed — it conflicts visually with the net line.

### Net line

- Defined as `net(i) = bars[i].income − bars[i].expense`, mapped to y via the same symmetric scale:
  `y = zeroY − (net / maxAbs) * halfHeight`.
- Drawn as `bars.size − 1` individual stroked line segments connecting each adjacent pair of (x-center, y) points; stroke `2.dp`, `StrokeCap.Round`. Per-segment alpha (see Selection alpha) requires separate `drawLine` calls rather than a single `drawPath` / polyline.
- One filled dot per period at radius `3.5.dp` at each (x-center, y).
- Color: `CashFlowNetColor` (new in `theme/Color.kt`), value `Color(0xFFFFD66E)`.

### Selection alpha

- `SELECTED_ALPHA = 1.0f`, `UNSELECTED_ALPHA = 0.4f` (unchanged constants).
- Selected slot's income bar, expense bar, and net dot draw at `SELECTED_ALPHA`.
- Non-selected slots' bars and dots draw at `UNSELECTED_ALPHA`.
- Line segments: a segment between index `i` and index `i+1` draws at `SELECTED_ALPHA` if either endpoint is the selected index; otherwise at `UNSELECTED_ALPHA`. Each segment is drawn as a separate stroked line so per-segment alpha can vary.

## Interaction

Unchanged:

- Tap any slot → `onBarSelected(index)` (existing transparent tap-row over the canvas).
- Right-anchor scroll on data change via the existing `LaunchedEffect(anchorKey)` block.
- Horizontal scroll on overflow via `Modifier.horizontalScroll(scrollState)`.
- Label row beneath the chart renders `bar.label` with the same selected/unselected weight + color logic.

The public composable signature is unchanged:

```kotlin
@Composable
fun CashFlowBarChart(
    bars: List<CashFlowBar>,
    selectedIndex: Int,
    onBarSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
)
```

`TimelineScreen` integration requires no edits.

## Edge Cases

- **Single bar**: line renders as one dot, no segments.
- **All-zero period** (`income == 0 && expense == 0`): bars don't render (height 0); the dot sits on the zero baseline.
- **Degenerate scale** (`maxAbs == 0`): coerced to `1.0`; bars and line collapse to the zero baseline.
- **Empty `bars`**: composable returns early (unchanged).

## Testing

- **Manual**: install on the emulator and verify across all three modes (Monthly / Annual / Total), with sample data containing at least one period where expense > income so the net line dips below the zero baseline.
- **Unit tests**: none added. The change is pure Canvas drawing; ViewModel and use case behaviour is unchanged.

## Implementation Notes

- Constants `BAR_HEIGHT`, `SLOT_WIDTH`, `SELECTED_ALPHA`, `UNSELECTED_ALPHA` stay as today.
- New private helper `drawBar` keeps its signature — only call-site arguments change to use the zero baseline.
- Net-line drawing pulls out into a small private helper (e.g. `drawNetLine`) that takes `bars`, `maxAbs`, geometry, and `selectedIndex` so the main `Canvas` block stays readable.
- Add `CashFlowNetColor` to `theme/Color.kt` alongside `IncomeColor` / `ExpenseColor`.
