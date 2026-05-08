# Use date picker for all date input/edit fields (incl. Assets) — Design Spec

**Date:** 2026-05-08
**Issue:** #4
**Source:** Use date picker for all date input/edit fields (incl. Assets)

## Goal

Replace every free-text date input in the app with the Material 3 date picker that the Add/Edit Transaction screen already uses, so date entry is consistent, less error-prone, and faster on mobile. The visible bug is in the Assets screens, where users must type `YYYY-MM-DD` into an `OutlinedTextField`; the fix is to extract a shared component and route all date fields through it.

## Non-goals

- **Changing date storage / persistence.** Dates remain `LocalDate` at the domain layer, persisted as ISO-8601 strings via the existing `Converters.kt`. No DB migration.
- **CSV import date parsing.** `CsvImporter` reads dates from CSV strings (`yyyy-MM-dd` / `dd/MM/yyyy`). That's a file-format concern, not a UI input — out of scope.
- **Date-range pickers.** No screen needs one today. If filtering by date range is added later, it gets its own picker.
- **Localising the picker UI itself.** The `DatePicker` composable already follows the system locale; we don't override it.
- **Replacing `MonthSelector`** in Timeline (month-stepper chevrons; not a date "input/edit" field).
- **Tests for the picker UI.** No instrumented/Compose UI tests in this spec — same posture as the prior assets specs.

## Decisions

### Single shared component, four call sites

The Add/Edit Transaction screen has a working `DatePickerModal` (`AddTransactionScreen.kt:357`) plus a clickable date "row" with a calendar icon and a formatted display. Both pieces are extracted to `ui/component/` and reused everywhere:

- `DatePickerDialogModal` — wraps `DatePickerDialog` + `rememberDatePickerState`. Accepts a nullable `LocalDate` (so the credit-end-date field, which can be unset, works without a sentinel value).
- `DatePickerField` — a tappable read-only surface (icon + label + formatted date) that opens the modal on click. Drop-in replacement for the four current `DateRow`s.

All four screens — including `AddTransactionScreen` — migrate to `DatePickerField`. We do **not** keep the inline `DatePickerModal` in the transaction screen and copy it to Assets in parallel: that would re-create the same divergence the issue is fixing.

### Display format

The current transaction screen formats dates as `d MMM yyyy` with `Locale("pt", "PT")` (e.g. `15 jan. 2026`). The Assets `DateRow` shows the raw ISO string (`2026-01-15`). The shared component standardises on the transaction-screen format `d MMM yyyy` but resolves the locale via `Locale.getDefault()` rather than hard-coding `pt-PT`. Rationale: the app has no documented `LocalePolicy`, and hard-coding Portuguese on dates while leaving the rest of the UI on system defaults would diverge from the platform's normal locale behaviour. On a pt-PT device this is identical to today; on a non-pt-PT device the displayed month names follow the system locale.

### Tappable read-only field, not editable text

Reasoning:
- The picker is the only intended input path. A typeable text field invites two parallel inputs we'd then have to keep in sync.
- Material 3 `DatePicker` ships with an "input" mode (keyboard) that the user can switch to from the dialog if they really want to type — covered for free.
- Validation collapses: if the picker can only return a valid `LocalDate`, the "is this parseable?" branch in every screen disappears.

### Nullable vs required dates

- `purchaseDate` (real estate, financial asset, lot) is **required**. The shared field still accepts `LocalDate?` so the same component handles both, but each screen continues to flag missing values via its existing `formErrors`.
- `creditEndDate` (real estate, debt section) is **optional in the form** until the debt section is shown, then becomes required. The shared component supports a "Clear" action only where the screen passes `allowClear = true` — disabled by default to keep behaviour identical to the current free-text fields.

### Bounds on the picker

- `purchaseDate` everywhere: `selectableDates` blocks dates after today (matches existing validation `Cannot be in the future`).
- `creditEndDate`: no upper bound (mortgages run for decades). Lower bound is `purchaseDate + 1 day` only when `purchaseDate` is set; otherwise no lower bound. (Matches the current validator: `Must be after purchase date`.)
- Transaction date: no bounds change. The current screen does not block future dates; we keep that behaviour to avoid scope creep on a UI-consistency issue.

### No ViewModel changes

ViewModels already expose `LocalDate` / `LocalDate?` getters and `(LocalDate) -> Unit` / `(LocalDate?) -> Unit` callbacks (`onDateChanged`, `onPurchaseDate`, `onCreditEndDate`). The migration is purely the screen composition layer. No state-shape changes, no new repository or use-case wiring.

## Affected components

### New files

```
ui/component/DatePickerDialogModal.kt   — modal wrapping DatePickerDialog + rememberDatePickerState
ui/component/DatePickerField.kt         — tappable read-only field that opens the modal
```

`DatePickerField` signature:

```kotlin
@Composable
fun DatePickerField(
    label: String,
    value: LocalDate?,
    onChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    allowClear: Boolean = false,
    selectableDates: SelectableDates = PastOrToday,   // bounded predicate set per call site
)
```

`DatePickerDialogModal` signature:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogModal(
    initialDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    selectableDates: SelectableDates = AllDates,
)
```

Defines two reusable `SelectableDates` constants in the same file — `AllDates`, `PastOrToday` — plus a tiny factory `notBefore(date: LocalDate)` for the credit-end-date case.

### Modified files

| File | Change |
|---|---|
| `ui/feature/addtransaction/AddTransactionScreen.kt` | Remove private `DatePickerModal`; replace the inline date-row composition (lines ~195–220) with `DatePickerField`. Drop now-unused `DatePicker`, `DatePickerDialog`, `rememberDatePickerState`, `Instant`, `ZoneOffset` imports. |
| `ui/feature/assets/realestate/edit/AddEditRealEstateScreen.kt` | Replace the private `DateRow` with `DatePickerField` (two call sites: purchase date + credit end date). Remove the placeholder comment at line 235–237. |
| `ui/feature/assets/financial/add/AddFinancialAssetScreen.kt` | Replace the private `DateRow` with `DatePickerField` (one call site: first-lot purchase date). |
| `ui/feature/assets/financial/addlot/AddLotScreen.kt` | Replace the private `DateRow` with `DatePickerField` (one call site: lot purchase date). |

No ViewModel, repository, DAO, or domain-model changes.

## Implementation notes

### Conversion at the modal boundary

The Material 3 `DatePickerState` exposes `selectedDateMillis: Long?`. Conversion mirrors what `AddTransactionScreen.DatePickerModal` already does:

```kotlin
val initialMillis = initialDate?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli()
val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
// On confirm:
state.selectedDateMillis?.let { millis ->
    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    onDateSelected(date)
}
```

UTC is safe here because `LocalDate` has no timezone. Using the local zone would risk a one-day shift in the picker for users east of UTC.

### Display formatter

```kotlin
private val DisplayFormatter: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
```

Defined as a property (not a top-level `val`) so the formatter picks up locale changes if the user switches device locale at runtime. Empty-state placeholder text mirrors the surrounding fields (`"Pick a date"` in the same `onSurfaceVariant` colour the transaction screen already uses for the unselected category).

### `allowClear`

When `allowClear = true` AND `value != null`, `DatePickerField` shows a small clear/`×` trailing icon. Tapping it calls `onChange(null)` without opening the picker. Only the credit-end-date call site sets `allowClear = true`; everywhere else it stays `false` (you cannot clear a required date).

### Bounded picker per call site

```kotlin
// purchase dates everywhere
DatePickerField(
    label = "Date",
    value = state.purchaseDate,
    onChange = viewModel::onPurchaseDate,
    error = state.formErrors.purchaseDate,
    selectableDates = PastOrToday,
)

// credit end date — only after purchase date, no upper bound
DatePickerField(
    label = "Credit end date",
    value = state.creditEndDate,
    onChange = viewModel::onCreditEndDate,
    error = state.formErrors.creditEndDate,
    allowClear = true,
    selectableDates = state.purchaseDate?.let { notBefore(it.plusDays(1)) } ?: AllDates,
)

// transaction date — same picker, no bound (matches current behaviour)
DatePickerField(
    label = "Date",
    value = uiState.date,
    onChange = viewModel::onDateChanged,
    selectableDates = AllDates,
)
```

### Removed code

- `AddTransactionScreen.DatePickerModal` (private composable, lines 357–382) — deleted.
- `AddEditRealEstateScreen.DateRow` (private composable) — deleted.
- `AddFinancialAssetScreen.DateRow` (private composable) — deleted.
- `AddLotScreen.DateRow` (private composable) — deleted.

The placeholder comment "A native DatePickerDialog can be added later" in `AddEditRealEstateScreen.kt:235–237` is removed along with the function.

### Behavioural changes user-visible

- **Real estate, financial add, add lot:** dates are now picker-only. The `YYYY-MM-DD` placeholder text disappears.
- **Transaction:** the dialog and tap-target are unchanged. The displayed date now follows the system locale rather than always being `pt-PT` — identical for pt-PT devices, localised month names on others (e.g. `15 Jan 2026` on `en-GB`).
- **Real estate credit-end-date:** gains a "Clear" action that the free-text field didn't have (you previously had to delete the text manually). The validator already permits null when `debt_amount` is blank, so this is purely an ergonomic improvement.

## Validation & error handling

No validation rules change. Each screen continues to compute `formErrors.<dateField>` via its existing flow. `DatePickerField` surfaces the error via `isError` styling on the surface and an `error` text below — same pattern as the existing `TextRow` / `NumberRow` helpers it sits alongside.

The previously-possible "free text isn't parseable" failure mode is gone: the picker can only emit a valid `LocalDate`, so the only remaining failure is "field is required and value is null", which the existing `formErrors` already covers.

## Testing

### Unit tests

No new unit tests. The four ViewModels already have date-handler tests; behaviour is unchanged. Skipping new tests is consistent with the prior assets specs (Compose UI tests are explicitly out of scope until instrumented-test infrastructure exists).

If we extracted any pure-function date helpers (we don't — the `LocalDate ↔ epoch-millis` conversion lives inside the composable), we'd unit-test those. Nothing to test here.

### Run command

```bash
./gradlew testDebugUnitTest
```

Existing suites must stay green.

### Manual verification checklist

1. Add Transaction → tap the date row → picker opens; pick a date → row updates with `d MMM yyyy` format; save → reopen in edit mode → same date.
2. Edit Transaction → tap the date row → picker opens at the existing date.
3. Add Property → tap **Purchase date** → picker opens at today; future dates are not selectable; pick yesterday → row updates.
4. Add Property → enter a debt amount to reveal the debt section → tap **Credit end date** → picker opens; dates on or before purchase-date not selectable; future dates allowed; pick a date in 2055.
5. Edit Property → debt section shown → **Credit end date** has a clear (×) icon → tap it → field becomes empty (saving is now blocked by the existing validator since the debt section is still shown — proves the validation gate is intact).
6. Edit Property → change purchase date to a later date → credit-end-date lower bound moves with it (next picker open shows the new bound).
7. Add Financial Asset → ticker validates → tap **Date** → picker; future dates blocked.
8. Financial Detail → "+ Add lot" → tap **Date** → picker.
9. Edit existing lot → date picker pre-populated with the lot's stored date.
10. Rotate device on each screen with the picker open → state preserved (Compose handles this; should be a non-event).

## Out of scope

- **CSV import date parsing** — file-format input, not user-input.
- **Date-range / period filters** — no screen needs them today.
- **Localising the picker beyond the system default** — the displayed-date formatter resolves locale via `Locale.getDefault()`; we don't override the picker's own locale handling.
- **Animating the picker open / custom theming** — use the Material 3 default.
- **An instrumented Compose UI test asserting the picker dialog is shown** — deferred to future instrumented-test infrastructure (consistent with the prior specs).
