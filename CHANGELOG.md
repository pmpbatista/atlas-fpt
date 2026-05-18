# Changelog

## [1.9] — 2026-05-18

### Changed

- Timeline header combines the cadence picker and the cash flow value into a single row; subtitle now reads `<Mode> Cash Flow` (#71)

## [1.8] — 2026-05-18

### Changed

- Timeline bar chart: diverging income/expense bars around a zero line, per-period net-cashflow line overlay, and y-axis tick labels in a fixed left gutter (#70)

## [1.7] — 2026-05-17

### Added

- Horizontal scrolling on the Timeline bar graph; chart anchors to the most recent bar on data change (#68)

## [1.6] — 2026-05-17

### Changed

- Tapping a Timeline bar scopes the header total and entries to that period (#67)

## [1.5] — 2026-05-17

### Added

- Monthly / Annual / Total aggregation modes on the Timeline bar chart (#66)

## [1.4] — 2026-05-17

### Removed

- Wallet and persons filter chips from the Timeline (#65)

## [1.3] — 2026-05-17

### Removed

- Spending Overview pill from the Timeline (#64)

## [1.2] — 2026-05-17

### Removed

- Disabled search icon from the Timeline header (#63)

---

## [1.1] — 2026-05-05 (through 2026-05-15)

### Added

- **Persons** — link any transaction to one or more named people (#1)
  - "Persons" row in Settings → dedicated sub-screen to add and delete persons
  - Delete warning shows how many transactions will be unlinked
  - Add/Edit transaction screen shows a "Link persons" row; selected persons appear as chips
  - Person picker bottom sheet lists all persons with checkboxes; full row is tappable
  - Persons stored in a new `persons` table with a `transaction_person_cross_ref` join table (DB version 1 → 2, automatic migration)
- Assets foundation — real-estate properties as a first-class asset type (#2)
- Financial assets — Yahoo Finance integration, multi-lot positions, P&L, CAGR (#3)
- Adaptive launcher icon (#33)
- Material date picker used for all date inputs (#36)
- Link transactions to assets via an `assetId` foreign key (#39)
- Rename persons after creation; persons shown on `TransactionRow` (#40)
- Overview redesign — segmented totals + category donut (#41)
- Timeline redesign — header + bars + day groups (#44)
- Filter Timeline and Overview by person (#45)
- Split net wealth into Financial / Real Estate sub-totals (#46)
- Ticker search-as-you-type dropdown on Add Financial Asset (#47)
- Background price refresh via WorkManager (#48)
- Live Euribor fetching from ECB with manual override (#49)
- Real-estate property photos (#50)
- FX conversion for mixed-currency wealth totals (#52)
- Sales, FIFO accounting, realized P&L, XIRR (#53)
- Dividends tracking with total-return metrics (#54)
- Asset charts — price history and portfolio value over time (#55)
- Local backup — manual export and scheduled background backups (#56)

### Fixed

- Add/Edit transaction screen is scrollable so the Save button stays visible when many labels or person chips are present (#1)
- Preserve `recurringRuleId` when editing a recurring transaction (#34)
- Disable the trash icon on the edit screen until the transaction has loaded (#43)

---

## [1.0] — initial release

- Add and edit expense/income transactions (amount, category, date, optional note)
- Timeline — chronological view grouped by day
- Monthly overview with charts (spending by category, month-over-month)
- Categories — pre-seeded in Portuguese, colour-coded
- Recurring transactions — daily / weekly / monthly / yearly rules
- Labels — tag transactions across categories
- CSV import — `date, amount, type, category, note` format
- Currency setting
- Material 3 dark theme
