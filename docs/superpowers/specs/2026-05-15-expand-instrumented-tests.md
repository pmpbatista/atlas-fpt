# Spec: Expand instrumented test coverage (#38)

## Context

`#19` shipped the androidTest source set, schema export, and the first cascade test (`TransactionCascadeTest`) plus a round-trip smoke test (`AppDatabaseSmokeTest`). `#38` is the follow-up that broadens coverage across DAO queries, the remaining foreign-key cascades, and Compose UI.

## Scope (this PR)

Three of the four buckets in `#38`:

1. **DAO query tests** — Room in-memory tests for the non-trivial query shapes that the app depends on:
   - `TransactionDao.observeByDateRange` — inclusive bounds, scheduled rows excluded
   - `TransactionDao.getCategoryTotals` — sum per category, filtered by `TransactionType`
   - `TransactionDao.observeMonthlySummaries` — month grouping via `strftime('%Y-%m', date)`
2. **Foreign-key cascade tests** — one passing test per cascade still uncovered:
   - `transaction_label_cross_ref` on `transactions.delete()`
   - `real_estate_details` on `assets.delete()`
   - `financial_holdings` on `assets.delete()`
   - `financial_lots` on `assets.delete()`
3. **Migration tests** — intentionally out of scope (the issue confirms 1→2→3→4→5→6 backfill is OOS; new migrations get tests when they ship).

## Out of scope (deferred to a follow-up)

**Compose UI smoke tests.** Adding them would pull in `compose-ui-test-junit4`, `compose-ui-test-manifest`, and a Hilt test runner / activity. The `#38` body explicitly cautions "keep them few and high-value" — landing them in their own small PR keeps that intent honest. `#38` will stay open after this PR with a comment narrowing the remaining work.

## Design decisions

- **In-memory Room over MigrationTestHelper.** All new tests build a fresh `Room.inMemoryDatabaseBuilder` per test, matching `TransactionCascadeTest`. No persisted state, no migration plumbing.
- **`allowMainThreadQueries()`** to keep the test bodies linear under `runTest`. This is already the pattern in the existing test.
- **Shared seed helper.** A small private helper in each file creates the prerequisite category / asset / label so the tests read as "arrange → act → assert". No JUnit `@Rule` extraction — premature for two files.
- **One test method per behaviour, not per query.** `observeByDateRange` gets two tests (inclusive bounds; scheduled-row exclusion) because those are two distinct invariants.

## Acceptance

- New file `TransactionDaoQueryTest` with at least 3 passing tests covering the three queries above.
- New file `AssetCascadeTest` with 3 passing cascade tests (`real_estate_details`, `financial_holdings`, `financial_lots`).
- Extension of `TransactionCascadeTest` (or sibling) covering `transaction_label_cross_ref` cascade.
- `./gradlew connectedDebugAndroidTest` passes locally (run by the user on the emulator before merge).
- `#38` stays open; a comment lists the remaining UI-smoke work.
