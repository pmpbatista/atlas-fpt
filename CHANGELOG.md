# Changelog

## [1.1] — 2026-05-05

### Added

- **Persons** — link any transaction to one or more named people
  - New "Persons" row in Settings → dedicated sub-screen to add and delete persons
  - Delete warning shows how many transactions will be unlinked
  - Add/Edit transaction screen shows a "Link persons" row; selected persons appear as chips
  - Person picker bottom sheet lists all persons with checkboxes; full row is tappable
  - Persons stored in a new `persons` table with a `transaction_person_cross_ref` join table (DB version 1 → 2, automatic migration)

### Fixed

- Add/Edit transaction screen: form content is now scrollable so the Save button stays visible when many labels or person chips are present

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
