# Spec: Dividends tracking + total return (#27)

## Problem

Asset-level total return must include cash dividends, not just price appreciation. The app has no place to record them.

## Decisions

- **Data shape:** new `dividends` table with FK to `assets` (CASCADE). One row per payment.
- **Per-share vs gross.** A single `gross: Double` field. The Add Dividend form has an optional "per-share × quantity-on-date" helper that the user can use to compute the gross; only the gross is persisted. Keeps the data model tight.
- **Currency.** Dividends inherit the asset's currency. Per-share dividends in a different currency are a future-FX problem; not in scope.
- **Tax / withholding.** Out of scope. The "gross" is what gets recorded; future spec can add a withholding column.

## Data model

```sql
CREATE TABLE dividends (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    assetId INTEGER NOT NULL,
    payDate TEXT NOT NULL,                    -- ISO yyyy-MM-dd
    grossAmount REAL NOT NULL,
    note TEXT,
    FOREIGN KEY (assetId) REFERENCES assets(id) ON DELETE CASCADE
);
CREATE INDEX index_dividends_assetId ON dividends(assetId);
```

Migration 8 → 9 adds the table only.

## Domain

```kotlin
data class Dividend(
    val id: Long,
    val payDate: LocalDate,
    val grossAmount: Double,
    val note: String?,
)
```

`FinancialAsset.dividends: List<Dividend>` joins from `AssetWithFinancial`.

`FinancialAssetReturns` gains `dividendIncome: Double` (sum of grosses) and updates:

- `realizedPnl` stays as **capital** realized P&L (FIFO match).
- New `totalCashReturn = realizedPnl + dividendIncome`.
- `totalReturn = totalCashReturn + unrealizedPnl` (when price known).
- `xirr` adds a positive flow per dividend on its `payDate`.

## UI

- **Detail screen** Returns block grows two rows: "Dividends" (sum, signed) and updates "Total return" to fold them in. No layout reflow other than that.
- A **Dividends section** below the lots list, with a "+ Add" action and rows showing date + gross. Long-press row → confirm delete; tap row → edit screen.
- **AddDividendScreen** is a slim variant of AddLotScreen — date picker, amount field, optional note. Validation: positive amount; date not in the future.

## Out of scope

- Dividend re-investment (DRIP) — those land as new BUY lots manually.
- Per-share helper persistence — the user enters the gross; the form's per-share calculator is one-way.
- Multi-currency dividends.

## Acceptance

- A user can add, edit, and delete dividend entries per financial asset.
- Detail screen shows the dividend list and incorporates dividends into Total Return + XIRR.
- Existing tests for sales / FIFO still pass; new unit tests cover the dividend math + XIRR with dividends.

## Open questions, resolved

- **Use a transactions row or a separate table?** Separate table. Dividends are conceptually different from spending/income (no category, no person) and shouldn't pollute the main timeline. They have a dedicated UI under the asset.
- **Default currency.** Always the asset's currency. No setting required.
