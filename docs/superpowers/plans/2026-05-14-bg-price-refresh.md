# Background Price Refresh (#30) Implementation Notes

**Goal:** Periodic `WorkManager` job (daily, unmetered network) that refreshes prices for held financial assets. Settings toggle to disable. Last-refresh timestamp visible on the Assets header.

**Acceptance criteria from #30:**
- Worker registered in app init.
- Settings toggle to enable/disable.
- Last-refresh timestamp visible.

## What landed

### Data layer (`feat(#30): periodic WorkManager job for background price refresh`)

- `AppSettings` gains `backgroundRefreshEnabled: Boolean = true` and `lastPriceRefreshAt: Long? = null`. The nullable Long is loaded via `prefs.contains(...)` to distinguish "never refreshed" from `0L`.
- `SettingsRepository` gains `setBackgroundRefreshEnabled(enabled)` and `setLastPriceRefreshAt(millis)`. Both reload the StateFlow.
- `RefreshPricesWorker` (`@HiltWorker` + `@AssistedInject`) reads the enabled flag, short-circuits when false, otherwise calls `PriceRepository.refreshAll()` and updates the timestamp when at least one ticker refreshed. Returns `Result.retry()` only when all tickers failed (not on partial success).
- `MainActivity.schedulePriceRefreshWorker()` enqueues a daily periodic request with `NetworkType.UNMETERED` constraint, using `ExistingPeriodicWorkPolicy.KEEP` (matches the existing `RecurringTransactionWorker` scheduling pattern).

### UI layer (`feat(#30): settings toggle + last-refresh timestamp on Assets header`)

- `SettingsViewModel.setBackgroundRefreshEnabled(enabled)` delegates to the repo.
- `SettingsScreen` gains a `SettingsToggleRow` (icon + title + subtitle + trailing `Switch`) between the Currency picker and the CSV import row. The full row is clickable to toggle.
- `AssetsListViewModel` injects `SettingsRepository` and surfaces `settings: AppSettings` on its `AssetsListUiState`. The existing 4-source `combine` becomes a two-level nested combine to keep typed-lambda inference happy.
- `TotalWealthHeader` gains `lastRefreshAt: Long? = null`; when non-null, renders "Prices updated <relative>" below the subtitle. `formatRelativeAge(...)` covers four buckets ("just now" / "Nm ago" / "Nh ago" / "Nd ago") and clamps with `coerceAtLeast(0L)` so a future-dated timestamp (clock skew) reads "just now".

## Known minor trade-offs (per final review, deliberately not fixed in this PR)

1. **Worker on empty asset list**: when `tickers.isEmpty()`, `refreshAll()` returns `RefreshResult(0, 0)` and the timestamp is NOT updated. Acceptable because the header only renders when assets exist — the missing timestamp is invisible to the user.
2. **`SettingsViewModel.setBackgroundRefreshEnabled` is wrapped in `viewModelScope.launch`** even though the repo call is synchronous. Harmless but mildly misleading. Follow-up if it ever becomes a real `suspend` operation.
3. **`AssetsListUiState.settings: AppSettings`** is a wider surface than the screen strictly needs (it only reads `lastPriceRefreshAt`). Could be narrowed to `lastPriceRefreshAt: Long?` directly.

## Out of scope

- Foreground refresh trigger from Settings ("Refresh now" button) — handled by the existing manual force-refresh on the Assets screen.
- Surfacing the last-refresh timestamp on screens other than Assets.
- Configurable refresh interval.
- Backoff strategy beyond WorkManager's default exponential retry.
