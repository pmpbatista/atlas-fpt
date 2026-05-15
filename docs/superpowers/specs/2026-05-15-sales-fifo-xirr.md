# Spec: Sales / FIFO / realized P&L / XIRR (#26)

## Problem

Today, financial-asset lots can only record acquisitions. There is no way to record a sale, so realized P&L and accurate annualised return (XIRR) can't be computed.

## Decisions

- **Accounting method:** **FIFO** only. Aligns with the default for most retail brokerages and with the "consume oldest first" intuition. LIFO can come later if the user asks for it.
- **Data shape:** a `lotType: BUY | SELL` enum on `financial_lots`, not a separate `sales` table. Sales reuse the lot row's `quantity` (always positive) and `pricePerUnit`. This keeps the existing list UI, mappers, and FK relationships unchanged.
- **Net quantity:** `holding = sum(BUY.qty) - sum(SELL.qty)`. Computed in SQL via `SUM(CASE WHEN lotType='BUY' THEN quantity ELSE -quantity END)`.
- **Validation rule:** at save time, the cumulative running net quantity across all the asset's lots (in date-then-id order) must never go negative — i.e. a SELL can never close more than what's been bought up to that point.

## Domain model

```kotlin
enum class LotType { BUY, SELL }

data class FinancialLot(
    val id: Long,
    val purchaseDate: LocalDate,
    val quantity: Double,            // always positive
    val pricePerUnit: Double,        // cost for BUY, sale price for SELL
    val type: LotType = LotType.BUY,
)
```

A new `FinancialAssetReturns` value object exposes the computed numbers:

```kotlin
data class FinancialAssetReturns(
    val realizedPnl: Double,             // sum of FIFO-matched (sellPx - buyPx) * qty
    val unrealizedPnl: Double?,          // (currentPrice - costPerRemainingShare) for the leftover BUY pool; null when no price
    val totalReturn: Double?,            // realized + unrealized; null when unrealized is null
    val totalReturnPct: Double?,         // totalReturn / total_invested_capital; null when invested == 0
    val totalInvested: Double,           // sum of BUY (qty * price) — the gross capital deployed
    val xirr: Double?,                   // annualised IRR or null when ambiguous
    val netQuantity: Double,
    val avgCostPerRemainingShare: Double, // weighted-average price of the surviving BUY pool, or 0
)
```

## FIFO algorithm (computed in a pure function, not the DB)

1. Sort lots by `(purchaseDate, id)` ascending.
2. Walk a mutable list of `(remainingQty, pricePerUnit)` cells.
3. On `BUY`: append a cell.
4. On `SELL`: consume cells head-first, subtracting until SELL.quantity is exhausted. For each consumed slice of size `s` at buy price `c` and sell price `p`, add `(p - c) * s` to realized P&L. If the user's data is consistent (validation enforces this), the SELL will always be fully consumable.
5. After the walk, the remaining cells make up the leftover BUY pool.
6. `unrealizedPnl = sum(remaining.qty * (currentPrice - remaining.price))`.
7. `avgCostPerRemainingShare = sum(remaining.qty * remaining.price) / sum(remaining.qty)` (or 0 when empty).

## XIRR

Cash flows: `−qty*price` on each BUY date, `+qty*price` on each SELL date, and a terminal flow on today of `+netQty * currentPrice` (if the user still holds shares and a price exists). Solve `NPV(r) = 0` via bisection in `[-0.99, 100]` for up to 60 iterations (≤ 2^60 precision — more than enough). Return null when:

- No flows, or only buys with no terminal value (no price),
- Sign change can't be found within the bracket (degenerate input).

The bisection lives in a pure helper file (`util/Xirr.kt`) and is unit-tested against a known cash-flow series.

## Schema (migration 7 → 8)

```sql
ALTER TABLE `financial_lots` ADD COLUMN `lotType` TEXT NOT NULL DEFAULT 'BUY';
```

All existing rows become BUY by default — backwards compatible.

`FinancialDao.sumLotQuantity` and the related repository helpers update to the net-quantity formula.

## UI

- **AddLotScreen** gains a segmented "Buy / Sell" selector at the top. The price field's label changes ("Price/share" → "Sale price") when SELL is active. Save validates the cumulative-net invariant — on a violation, a snackbar reads "Selling X exceeds shares held on YYYY-MM-DD" and the save is rejected.
- **FinancialDetailScreen** lot list distinguishes lot type with a small `SELL` chip on sell rows (and a "−" prefix on the quantity). The stats area gains three new rows: Realized P&L, Total Return (abs + %), XIRR. Existing `avg yearly yield` is dropped in favour of XIRR — same intent, more rigorous.
- **Record sale** action: the existing "+" FAB in the detail screen continues to add a buy by default; a long-press / overflow opens "Record sale" (decision: simpler — make the FAB open AddLot in the user's last-used mode, with the Buy/Sell selector visible immediately).

## Out of scope

- LIFO and other matching methods.
- Cash dividends — tracked by `#27`.
- Wash-sale or tax-lot exotica.
- Partial-share precision beyond `Double` (good to ~15 significant digits; fine for retail position sizes).

## Acceptance

- A user can record a sale via the add-lot screen with the selector set to SELL.
- The detail screen shows realized P&L, total return (abs + %), and XIRR alongside the existing snapshot.
- Lot list visually distinguishes BUY vs SELL.
- Selling more shares than are open at the sale date is blocked with a clear error.
- `./gradlew testDebugUnitTest` includes XIRR test against a known series (≥ 1 sanity case), FIFO realized-P&L test for the canonical scenario (3 buys, 2 sells), and a validation test for the running-net check.
- Existing financial-asset tests (yield, holdings, lots) still pass — backward compatibility for BUY-only portfolios.
- Migration test: not required because Room's identity-hash check fires on each emulator boot; existing connected tests on v8 schema are sufficient.

## Open questions, resolved

- **Average yearly yield is replaced by XIRR.** XIRR is the correct cash-flow-weighted measure; per-lot CAGR weighted by cost was an approximation that doesn't survive partial sales. Drop the old card.
- **Per-share precision.** `Double` is acceptable. Documenting this so any future migration to `java.math.BigDecimal` is a conscious choice.
- **Negative quantities in the DB.** Rejected — keeps SELL rows readable in raw SQL and prevents accidental sign flips.
