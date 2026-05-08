# Ticker search-as-you-type dropdown (Yahoo Finance-style) — Design Spec

**Date:** 2026-05-08
**Issue:** #5
**Source:** Ticker search-as-you-type dropdown (Yahoo Finance-style)

## Goal

Replace the plain ticker text field on `AddFinancialAssetScreen` with a search-as-you-type dropdown that surfaces Yahoo Finance symbol suggestions (symbol + a one-line description) as the user types. Tapping a result fills the ticker field and feeds the existing validation pipeline. Manual full-ticker entry remains a working fallback.

## Non-goals

- Reuse on `AddLot`. The ticker field on `AddLotScreen` is read-only (the asset is already chosen), so search has no place there. Calling out the issue's "reusable on any other screen" note: it doesn't apply to anything that exists today, but the new field is packaged as a standalone composable so a future screen can adopt it.
- An offline ticker corpus or cached search index. Search is online-only.
- A new network provider. Yahoo Finance's `v1/finance/search` endpoint sits on the same `query1.finance.yahoo.com` host already used for quotes — no new auth, no new dependency.
- Keyboard-driven highlight + Enter-to-select. Selection is by tap only (Material 3 `ExposedDropdownMenu` semantics).
- Search-result caching. Debounce is the only de-duplication mechanism.
- Logo / icon thumbnails per result.

## Decisions

### Endpoint and DTO

- Use Yahoo Finance unofficial search: `GET https://query1.finance.yahoo.com/v1/finance/search?q={q}&quotesCount=8&newsCount=0`. Same host as the quote endpoint; same `User-Agent` interceptor applies.
- The response's `quotes[]` is the only field we deserialize. Each entry exposes `symbol`, `shortname` / `longname`, `exchDisp`, `quoteType` / `typeDisp`. We drop the rest.
- Result limit fixed at 8. Anything more is noise on a phone screen and slower to render in the dropdown.

### Domain model

A search result is *not* a `TickerQuote` — it has no price. Introduce a new domain type so the type system makes that explicit:

```kotlin
// domain/model/TickerSuggestion.kt
data class TickerSuggestion(
    val symbol: String,        // canonical Yahoo symbol (e.g. "CSPX.L")
    val displayName: String,   // longname || shortname || symbol
    val exchange: String?,     // e.g. "London", "NASDAQ"; null when Yahoo omits it
    val quoteType: String?,    // e.g. "ETF", "EQUITY", "CRYPTOCURRENCY"; null when Yahoo omits it
)

// domain/model/SearchResult.kt
sealed interface SearchResult {
    data class Success(val suggestions: List<TickerSuggestion>) : SearchResult
    data object Empty : SearchResult                 // 200 with empty quotes[]
    data class Error(val cause: String) : SearchResult
}
```

`SearchResult.Empty` distinguishes "the network call worked, no matches" from "couldn't reach the server" — the dropdown UI renders different copy for each.

### Source-layer extension

Extend the existing `PriceSource` interface with a sibling `search` method, rather than carving a new interface for one method:

```kotlin
interface PriceSource {
    suspend fun fetchQuote(ticker: String): QuoteResult
    suspend fun search(query: String): SearchResult   // NEW
}
```

`YahooPriceSource` grows a `search(query)` implementation that mirrors the `fetchQuote` error-handling shape (404 / non-2xx / parse / network → `Error`; 200 with empty `quotes[]` → `Empty`).

### Use case

```kotlin
class SearchTickerUseCase @Inject constructor(
    private val priceSource: PriceSource,
) {
    suspend operator fun invoke(query: String): SearchResult {
        val q = query.trim()
        if (q.length < 2) return SearchResult.Success(emptyList())
        return priceSource.search(q)
    }
}
```

- Minimum 2 characters before hitting the network. Single-character queries return huge unfocused result sets and waste calls.
- The use case stays thin (no caching, no merging). Debounce belongs in the ViewModel where it can be cancelled cleanly.

The validate-on-selection step continues to go through the existing `ValidateTickerUseCase` → `PriceRepository.validateTicker` → `fetchQuote` chain. We do not invent a second validation path.

### UX

The ticker field becomes a Material 3 `ExposedDropdownMenuBox` wrapping the same `OutlinedTextField`:

- **Trigger:** every keystroke. ViewModel debounces 250ms, then runs `SearchTickerUseCase`. The 500ms full-ticker validation continues in parallel — this is what catches users who paste a complete symbol and expect the green-check confirmation without scrolling a dropdown.
- **States rendered inside the dropdown:**
  - *Idle* (query length < 2): dropdown closed.
  - *Loading*: single row with a small `CircularProgressIndicator` and "Searching…".
  - *Results*: up to 8 rows. Title = `symbol`; subtitle = a comma-joined description, e.g. `"iShares Core S&P 500 UCITS ETF · ETF · London"`.
  - *Empty*: single non-clickable row, "No matches".
  - *Error*: single non-clickable row, "Couldn't reach search. Check your connection." (No snackbar — the field-level message is enough.)
- **Selection:** tapping a row sets the ticker field to the row's `symbol`, dismisses the dropdown, and triggers the existing `validateTicker(symbol)` immediately (no 500ms debounce — the user explicitly chose this symbol). The standard `TickerState.Valid(quote)` flow then prefills `name` and `pricePerUnit` exactly as today.
- **Manual entry fallback:** if the user dismisses the dropdown (tap-out, back press, IME hide) and keeps typing, the existing 500ms validate-ticker debounce continues to fire. Behavior is unchanged from today for users who already know the exact symbol.
- **Capitalization:** the field still uses `KeyboardCapitalization.Characters`, but search queries are sent verbatim. Yahoo accepts mixed-case search input; ticker validation continues to upper-case before lookup.
- **Dropdown layout:** Material 3 `ExposedDropdownMenu`. Width matches the anchor field. Max height capped so it never covers the bottom Save bar — Material's default behavior already does this on small screens.

### Coexistence with current `TickerState`

`TickerState` (`Idle / Validating / Valid / Invalid / Error`) is unchanged. A new orthogonal `SearchState` lives next to it on the UI state:

```kotlin
sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Results(val items: List<TickerSuggestion>) : SearchState
    data object Empty : SearchState
    data class Error(val reason: String) : SearchState
}
```

The two states are independent: the user can be looking at search results while a previous validate call is still in-flight, and vice versa. `canSave` keeps gating on `tickerState is TickerState.Valid` — the search dropdown never claims "valid" on its own.

## Affected components

```
app/src/main/java/com/spendtrack/
├── data/network/
│   ├── PriceSource.kt                    (add search() method)
│   ├── YahooFinanceApi.kt                (add @GET v1/finance/search)
│   ├── YahooFinanceDtos.kt               (add SearchResponse + Quote DTOs)
│   └── impl/YahooPriceSource.kt          (implement search())
├── domain/
│   ├── model/
│   │   ├── TickerSuggestion.kt           (NEW)
│   │   └── SearchResult.kt               (NEW)
│   └── usecase/
│       └── SearchTickerUseCase.kt        (NEW)
└── ui/feature/assets/financial/add/
    ├── AddFinancialAssetScreen.kt        (replace the ticker field with TickerSearchField)
    ├── AddFinancialAssetViewModel.kt     (inject SearchTickerUseCase, add SearchState + debounced search job)
    └── component/
        └── TickerSearchField.kt          (NEW — ExposedDropdownMenuBox-based composable)
```

No DI module changes: `SearchTickerUseCase` is a plain `@Inject constructor` use case; it auto-resolves through `PriceSource`, which is already bound in `NetworkModule`.

## Implementation Notes

### `YahooFinanceApi` addition

```kotlin
@GET("v1/finance/search")
suspend fun search(
    @Query("q") query: String,
    @Query("quotesCount") quotesCount: Int = 8,
    @Query("newsCount") newsCount: Int = 0,
): Response<SearchResponse>
```

### DTOs

```kotlin
@Serializable
data class SearchResponse(
    val quotes: List<SearchQuote> = emptyList(),
)

@Serializable
data class SearchQuote(
    val symbol: String,
    val shortname: String? = null,
    val longname: String? = null,
    val exchDisp: String? = null,
    val quoteType: String? = null,
    val typeDisp: String? = null,
)
```

Yahoo occasionally returns entries without a `symbol` (news / option contracts mixed in). Filter those out at the source layer before mapping to `TickerSuggestion`.

### `YahooPriceSource.search`

```kotlin
override suspend fun search(query: String): SearchResult = runCatching {
    val response = api.search(query)
    when {
        !response.isSuccessful -> SearchResult.Error("HTTP ${response.code()}")
        else -> {
            val items = response.body()?.quotes
                ?.filter { it.symbol.isNotBlank() }
                ?.map { it.toSuggestion() }
                .orEmpty()
            if (items.isEmpty()) SearchResult.Empty
            else SearchResult.Success(items)
        }
    }
}.getOrElse { t ->
    runCatching { Log.w("YahooPriceSource", "search($query) failed", t) }
    SearchResult.Error(t.message ?: t.javaClass.simpleName)
}

private fun SearchQuote.toSuggestion() = TickerSuggestion(
    symbol = symbol,
    displayName = longname ?: shortname ?: symbol,
    exchange = exchDisp,
    quoteType = typeDisp ?: quoteType,
)
```

### ViewModel additions

- New field `private var searchJob: Job? = null` next to `validationJob`.
- New private state holder `_search = MutableStateFlow<SearchState>(SearchState.Idle)`. Combine into `uiState` alongside `_form`.
- `onTicker(raw)` keeps its current 500ms validation debounce; additionally, it cancels and restarts a 250ms search debounce that calls `SearchTickerUseCase` and updates `_search`. Empty/short queries set `_search` back to `Idle` immediately.
- New handler `onSuggestionSelected(suggestion: TickerSuggestion)`:
  1. `_form.update { it.copy(ticker = suggestion.symbol, tickerState = TickerState.Validating) }`
  2. `_search.update { SearchState.Idle }` (collapse dropdown)
  3. Cancel any in-flight `validationJob` and launch a fresh one that calls `validateTicker(suggestion.symbol)` with no delay.
- New handler `onDropdownDismissed()`: sets `_search` to `Idle`. The validation job is left running.

### `TickerSearchField` composable

Anchored on `ExposedDropdownMenuBox`. The text field inside is the same `OutlinedTextField` with the same hint, capitalization, and `tickerState` feedback row underneath. The `ExposedDropdownMenu` body switches on `SearchState`:

- `Loading` → one `DropdownMenuItem` with a spinner + "Searching…" text, `enabled = false`.
- `Results` → one `DropdownMenuItem` per suggestion. Two-line layout: `symbol` in `titleSmall`, description in `bodySmall onSurfaceVariant`. `onClick` calls the supplied `onSelect(suggestion)`.
- `Empty` → one disabled item, "No matches".
- `Error` → one disabled item with the error text in `MaterialTheme.colorScheme.error`.

The host screen owns dismissal: `onExpandedChange = { open -> if (!open) viewModel.onDropdownDismissed() }`.

### Field-level errors

`computeErrors` is unchanged. Search states never produce a `formErrors.ticker` entry — only `tickerState` does. This keeps Save gating identical to today.

## Testing

### Unit tests

- `YahooPriceSourceSearchTest` (new, alongside the existing `YahooPriceSourceTest`):
  - 200 with three quotes → `Success(3)`, mapping verified
  - 200 with `quotes: []` → `Empty`
  - 200 with quotes whose `symbol` is blank → those entries filtered out
  - 5xx → `Error`
  - Network exception → `Error`
- `SearchTickerUseCaseTest`:
  - Query `""` → `Success(emptyList())`, no source call
  - Query `"a"` (length 1) → `Success(emptyList())`, no source call
  - Query `"aa"` → delegates to source
- `AddFinancialAssetViewModelSearchTest` (alongside the existing add-asset VM tests):
  - Typing "AA" debounces and emits `SearchState.Loading` → `Results`
  - Typing rapidly cancels the previous search job
  - `onSuggestionSelected` populates ticker, transitions to `Validating`, then `Valid`, and prefill kicks in
  - `onDropdownDismissed` sets `_search` to `Idle` without affecting `tickerState`

Target: ~10 new unit tests.

### Manual verification checklist

1. Open Add Financial Asset, type `csp` → dropdown shows ETF rows including `CSPX.L` and `CSPXJ.SW` with descriptions and exchanges
2. Tap `CSPX.L` → dropdown closes, ticker field shows `CSPX.L`, brief spinner, then green check with display name + USD + price
3. Save → returns to list with new asset
4. Repeat with `aapl` → tap `AAPL` → name prefilled to "Apple Inc."
5. Type `zzzzz123` → dropdown shows "No matches"
6. Airplane mode → type `csp` → dropdown shows error row; existing validate-on-debounce error still fires under the field
7. Type `CSPX.L` directly without picking → existing 500ms validation fires and turns the row green (regression check on the manual-entry fallback)
8. Single character `c` → dropdown stays closed (length-2 minimum)
9. Rotate the device with the dropdown open → state persists; no crash

## Out of Scope

- A "favourites" list of frequently used tickers
- Asset-class filtering chips (ETF / Stock / Crypto) above the dropdown
- Returning prices in the search endpoint (Yahoo's search payload omits them; trying to merge in `regularMarketPrice` would require a per-suggestion fan-out call)
- Deduplicating against tickers the user already owns
- I18n of `quoteType` and `exchDisp` strings — Yahoo returns English; we render verbatim
