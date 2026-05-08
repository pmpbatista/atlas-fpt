# Split net wealth between Financial and Real Estate on Assets header — Design Spec

**Date:** 2026-05-08
**Issue:** #8
**Source:** Split net wealth between Financial and Real Estate on Assets header

## Goal

Surface separate `Financial` and `Real Estate` sub-totals on the Assets header, alongside the combined Net wealth, so the user can see how their wealth splits between volatile/liquid (financial) and leveraged/illiquid (real estate) holdings. Pure domain/UI surfacing — `AssetListItem.type` already discriminates, no schema work.

## Non-goals

- FX conversion across mixed currencies. The mixed-currency-stack rendering pattern stays.
- Any change to how individual assets are listed below the header.
- A third asset-type bucket. The model has exactly two `AssetType` values today; the design must keep working when a third appears, but does not anticipate it with placeholder UI.
- Configurability (e.g. a "show only one type" toggle).
- Caching the breakdown — the upstream `Flow<List<AssetListItem>>` is the source of truth and the math is trivial.

## Decisions

### Domain model — `TotalWealth`

Replace the current shape with a per-type breakdown that keeps existing derived accessors working. The data class stays a pure value holder; everything is computed from `byTypeAndCurrency`:

```kotlin
data class TotalWealth(
    val byTypeAndCurrency: Map<AssetType, Map<String, Double>>,
    val countByType: Map<AssetType, Int>,
) {
    /** Combined per-currency totals across all asset types. */
    val byCurrency: Map<String, Double>
        get() = byTypeAndCurrency.values.fold(emptyMap()) { acc, perCurrency ->
            (acc.keys + perCurrency.keys).associateWith { code ->
                (acc[code] ?: 0.0) + (perCurrency[code] ?: 0.0)
            }
        }

    val assetCount: Int get() = countByType.values.sum()
    val isMixedCurrency: Boolean get() = byCurrency.size > 1
    val isEmpty: Boolean get() = byTypeAndCurrency.values.all { it.isEmpty() }

    fun forType(type: AssetType): Map<String, Double> =
        byTypeAndCurrency[type] ?: emptyMap()

    fun countFor(type: AssetType): Int = countByType[type] ?: 0

    fun isMixedCurrencyForType(type: AssetType): Boolean =
        forType(type).size > 1

    companion object {
        val EMPTY = TotalWealth(emptyMap(), emptyMap())
    }
}
```

Notes:
- `byCurrency` recomputation per access is cheap — at most a handful of currencies. No need for memoization.
- `EMPTY` replaces the ad-hoc `TotalWealth(emptyMap(), 0)` literal in `AssetsListViewModel`'s initial state.
- Existing callers of `byCurrency`, `assetCount`, `isMixedCurrency`, `isEmpty` keep working unchanged.

### `GetTotalWealthUseCase` — group by type then currency

```kotlin
operator fun invoke(): Flow<TotalWealth> = repo.observeAssetList().map { items ->
    val byTypeAndCurrency = items
        .groupBy { it.type }
        .mapValues { (_, list) ->
            list.groupBy { it.currencyCode }
                .mapValues { (_, l) -> l.sumOf { it.equity } }
        }
    val countByType = items.groupingBy { it.type }.eachCount()
    TotalWealth(byTypeAndCurrency, countByType)
}
```

Pure transformation; no error paths beyond what the upstream Flow already handles.

### Header layout

`TotalWealthHeader` is rebuilt as a vertical stack:

1. **Combined Net wealth** (largest figure, top of the card):
   - Label: `"Net wealth"` (`labelMedium`).
   - For each `(currency, amount)` in `byCurrency`, render one `Text` line at `headlineMedium`, formatted with `CurrencyFormatter.formatAbsoluteForCurrency`.
   - Subtitle (`bodySmall onSurfaceVariant`): `"Across N assets"` or `"Across N assets · mixed currencies"` if `isMixedCurrency`.

2. **A `HorizontalDivider`** (subtle), then …

3. **Per-type sub-total blocks** in a fixed order (Financial first, then Real Estate). Each block is a `Column`:
   - Small label row: an icon (`Icons.AutoMirrored.Filled.ShowChart` for Financial, `Icons.Filled.Home` for Real Estate) + the type label (`"Financial"` / `"Real Estate"`) in `labelMedium`.
   - One amount line per currency in `forType(type)` (`titleMedium`).
   - Subtitle: `"N assets"` (or `"1 asset"`) + `" · mixed currencies"` if `isMixedCurrencyForType(type)`.
   - Hidden entirely when `countFor(type) == 0`.

4. If exactly one type has assets, the per-type block is still rendered (so the user sees "Financial" or "Real Estate" labelled explicitly), but the redundant combined "Net wealth" block at the top still shows the same number — this is intentional and small enough not to feel duplicative. The combined block's role is "what's my total"; the per-type block's role is "how is it split". Even with one type, the split-line answers a different question (which type is this?).

5. If both types are empty, the whole header returns early (existing `isEmpty` guard).

**Layout chosen: stacked vertically, not side-by-side.** Side-by-side cards compress badly with mixed-currency lines (can have 2–3 amounts per type). The mock isn't authoritative on this; vertical stacking handles all the cases without truncation.

### Fixed type ordering

Per-type blocks are rendered in the order `[FINANCIAL, REAL_ESTATE]`. This is hard-coded in `TotalWealthHeader`. When a third asset type lands, that spec adds it to the ordered list. The order isn't read from `byTypeAndCurrency` map iteration because `Map` ordering is implementation-defined — the UI must be deterministic.

### Empty / mixed-currency hints

- `isEmpty` semantics shift slightly. Today: `byCurrency.isEmpty()`. New: `byTypeAndCurrency.values.all { it.isEmpty() }`. Both are equivalent in practice (an asset always has both type and currency), but the new form is robust to a future state where a type bucket is empty while another is populated.
- Per-type mixed-currency hint uses `isMixedCurrencyForType(type)`. The combined `isMixedCurrency` keeps its existing semantics.

## Affected components

```
app/src/main/java/com/spendtrack/
├── domain/
│   ├── model/TotalWealth.kt                      (full rewrite — see Decisions)
│   └── usecase/GetTotalWealthUseCase.kt          (group by type, then currency)
└── ui/feature/assets/
    ├── component/TotalWealthHeader.kt            (per-type blocks, fixed ordering, hidden when empty)
    └── list/AssetsListViewModel.kt               (initial-state literal → TotalWealth.EMPTY)
```

`AssetsListScreen` consumes `state.total` directly — no changes needed there.

## Implementation Notes

### `TotalWealthHeader` skeleton

```kotlin
@Composable
fun TotalWealthHeader(total: TotalWealth, modifier: Modifier = Modifier) {
    if (total.isEmpty) return
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        // 1. Combined block
        Text("Net wealth", style = MaterialTheme.typography.labelMedium)
        total.byCurrency.forEach { (code, value) ->
            Text(
                CurrencyFormatter.formatAbsoluteForCurrency(value, code),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        Text(
            buildSubtitle(total.assetCount, total.isMixedCurrency),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // 2. Per-type blocks (fixed order)
        listOf(AssetType.FINANCIAL, AssetType.REAL_ESTATE).forEach { type ->
            if (total.countFor(type) > 0) {
                AssetTypeBlock(total = total, type = type)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AssetTypeBlock(total: TotalWealth, type: AssetType) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = type.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(type.label(), style = MaterialTheme.typography.labelMedium)
        }
        total.forType(type).forEach { (code, value) ->
            Text(
                CurrencyFormatter.formatAbsoluteForCurrency(value, code),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            buildSubtitle(total.countFor(type), total.isMixedCurrencyForType(type)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun buildSubtitle(count: Int, mixed: Boolean): String {
    val countText = if (count == 1) "1 asset" else "$count assets"
    return if (mixed) "$countText · mixed currencies" else countText
}

private fun AssetType.label() = when (this) {
    AssetType.FINANCIAL -> "Financial"
    AssetType.REAL_ESTATE -> "Real Estate"
}

private fun AssetType.icon() = when (this) {
    AssetType.FINANCIAL -> Icons.AutoMirrored.Filled.ShowChart
    AssetType.REAL_ESTATE -> Icons.Filled.Home
}
```

`AssetType.label()` and `AssetType.icon()` are kept private to this file — they're presentation concerns, not domain.

### `AssetsListViewModel` change

Single-line:

```kotlin
- total = TotalWealth(emptyMap(), 0),
+ total = TotalWealth.EMPTY,
```

## Testing

### Unit tests

- `TotalWealthTest` (new):
  - `EMPTY.isEmpty == true`, `EMPTY.assetCount == 0`, `EMPTY.byCurrency` is empty
  - With one financial USD asset → `byCurrency = {USD: x}`, `forType(FINANCIAL) = {USD: x}`, `forType(REAL_ESTATE)` empty
  - With financial USD + real-estate EUR → `byCurrency = {USD, EUR}`, `isMixedCurrency = true`, neither type's `isMixedCurrencyForType` is true
  - With two financial USD assets → `forType(FINANCIAL) = {USD: sum}`, `countFor(FINANCIAL) = 2`
- `GetTotalWealthUseCaseTest` updates:
  - Replace existing assertion shape: instead of asserting `byCurrency` directly, assert `byTypeAndCurrency` for known fixtures
  - Add: empty repo → emits `TotalWealth.EMPTY`-equivalent
  - Add: mixed-type fixture → both buckets populated correctly
- `AssetsListViewModelTest`: ensure tests still pass after the `TotalWealth.EMPTY` rename. No new tests needed at the VM layer — the VM passes `total` through unchanged.

### Manual verification checklist

1. Empty assets → header doesn't render (regression check)
2. One real-estate asset (EUR) → header shows combined "Net wealth €X" + a "Real Estate · 1 asset · €X" block; no Financial block
3. One financial asset (USD) → header shows combined "Net wealth $X" + a "Financial · 1 asset · $X" block; no Real Estate block
4. One of each, same currency (EUR) → combined `€X+Y`, two blocks both EUR, no mixed-currency hint anywhere
5. Financial USD + Real Estate EUR → combined shows two lines (USD + EUR) with "mixed currencies" hint; each per-type block shows its single currency, no per-type mixed hint
6. Two financial assets, one USD one EUR → Financial block shows both lines + "mixed currencies"; Real Estate block hidden
7. Force-refresh prices on financial assets → header updates correctly without flicker
8. Add a new asset → counts and totals update live (Flow propagation)

## Out of Scope

- FX conversion (still in `Known Gaps / Future Work`)
- A "compact" header variant that hides per-type blocks (no requirement for this)
- Per-type charts (allocation pie etc.) — separate feature if ever wanted
- A horizontal-pair layout — vertical stack handles all cases
