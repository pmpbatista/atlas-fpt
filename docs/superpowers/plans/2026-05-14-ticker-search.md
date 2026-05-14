# Ticker Search-as-you-Type Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a search-as-you-type dropdown to the ticker field on `AddFinancialAssetScreen`. As the user types, suggestions appear with symbol + short description; tapping a suggestion fills the field and triggers the existing validation.

**Architecture:**
- Extend `PriceSource` with `searchTickers(query): TickerSearchResult` so the contract for any future provider stays uniform.
- `YahooPriceSource` implements it via Yahoo's `v1/finance/search?q=...` endpoint.
- `PriceRepository` exposes `searchTickers(query)` as a passthrough.
- New `SearchTickersUseCase` wraps the repo call.
- `AddFinancialAssetViewModel` gains a `searchResults: TickerSearchState` field driven by a debounced text flow (kotlinx-coroutines `debounce` + `flatMapLatest`).
- `AddFinancialAssetScreen` renders the dropdown below the ticker field; selecting a row calls existing `onTicker(symbol)` to trigger validation.

**Tech Stack:** Kotlin 2.0.21, Retrofit 2 (already used for Yahoo chart), kotlinx-serialization, Jetpack Compose Material 3, JUnit4 + MockK + Turbine.

---

### Task 1: Branch (done)

Branch `feat/5-ticker-search` is already checked out from `bca0fa4`.

---

### Task 2: Network — search endpoint + DTOs + `PriceSource` extension

**Files:**
- Create: `app/src/main/java/com/atlasfpt/data/network/SearchResponse.kt`
- Modify: `app/src/main/java/com/atlasfpt/data/network/YahooFinanceApi.kt`
- Modify: `app/src/main/java/com/atlasfpt/data/network/PriceSource.kt`
- Modify: `app/src/main/java/com/atlasfpt/data/network/impl/YahooPriceSource.kt`
- Create: `app/src/main/java/com/atlasfpt/domain/model/TickerSearchResult.kt`

- [ ] **Step 1: Create the response DTO**

```kotlin
// app/src/main/java/com/atlasfpt/data/network/SearchResponse.kt
package com.atlasfpt.data.network

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(val quotes: List<SearchQuote> = emptyList())

@Serializable
data class SearchQuote(
    val symbol: String? = null,
    val shortname: String? = null,
    val longname: String? = null,
    val exchange: String? = null,
    val quoteType: String? = null,
    val typeDisp: String? = null,
)
```

- [ ] **Step 2: Add the API method**

In `YahooFinanceApi.kt`, append:

```kotlin
@GET("v1/finance/search")
suspend fun search(
    @Query("q") query: String,
    @Query("quotesCount") quotesCount: Int = 10,
    @Query("newsCount") newsCount: Int = 0,
): Response<SearchResponse>
```

Add `import retrofit2.Response` if missing.

- [ ] **Step 3: Create the domain model**

```kotlin
// app/src/main/java/com/atlasfpt/domain/model/TickerSearchResult.kt
package com.atlasfpt.domain.model

data class TickerSearchResult(
    val symbol: String,
    val displayName: String,
    val exchange: String?,
    val typeLabel: String?,  // e.g. "ETF", "Stock", "Crypto"
)

sealed interface SearchResult {
    data object Empty : SearchResult            // query was blank
    data object Loading : SearchResult
    data class Success(val items: List<TickerSearchResult>) : SearchResult
    data object NoMatches : SearchResult        // API returned 0 items
    data class Error(val reason: String) : SearchResult
}
```

- [ ] **Step 4: Extend `PriceSource`**

```kotlin
// app/src/main/java/com/atlasfpt/data/network/PriceSource.kt
package com.atlasfpt.data.network

import com.atlasfpt.domain.model.QuoteResult
import com.atlasfpt.domain.model.SearchResult

interface PriceSource {
    suspend fun fetchQuote(ticker: String): QuoteResult
    suspend fun searchTickers(query: String): SearchResult
}
```

- [ ] **Step 5: Implement `searchTickers` on `YahooPriceSource`**

Append inside the `YahooPriceSource` class:

```kotlin
override suspend fun searchTickers(query: String): SearchResult {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return SearchResult.Empty
    return runCatching {
        val response = api.search(trimmed)
        if (!response.isSuccessful) {
            SearchResult.Error("HTTP ${response.code()}")
        } else {
            val items = response.body()?.quotes.orEmpty().mapNotNull { q ->
                val symbol = q.symbol ?: return@mapNotNull null
                val name = q.longname ?: q.shortname ?: symbol
                TickerSearchResult(
                    symbol = symbol,
                    displayName = name,
                    exchange = q.exchange,
                    typeLabel = q.typeDisp ?: q.quoteType,
                )
            }
            if (items.isEmpty()) SearchResult.NoMatches else SearchResult.Success(items)
        }
    }.getOrElse { t ->
        runCatching { android.util.Log.w("YahooPriceSource", "searchTickers($query) failed", t) }
        SearchResult.Error(t.message ?: t.javaClass.simpleName)
    }
}
```

Add the imports:
```kotlin
import com.atlasfpt.domain.model.SearchResult
import com.atlasfpt.domain.model.TickerSearchResult
```

- [ ] **Step 6: Compile + commit**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

```bash
git add app/src/main/java/com/atlasfpt/data/network/SearchResponse.kt \
        app/src/main/java/com/atlasfpt/data/network/YahooFinanceApi.kt \
        app/src/main/java/com/atlasfpt/data/network/PriceSource.kt \
        app/src/main/java/com/atlasfpt/data/network/impl/YahooPriceSource.kt \
        app/src/main/java/com/atlasfpt/domain/model/TickerSearchResult.kt
git commit -m "feat(#5): add ticker search via Yahoo Finance v1/finance/search"
```

---

### Task 3: Repository + use case + ViewModel state

**Files:**
- Modify: `app/src/main/java/com/atlasfpt/data/repository/PriceRepository.kt`
- Create: `app/src/main/java/com/atlasfpt/domain/usecase/SearchTickersUseCase.kt`
- Modify: `app/src/main/java/com/atlasfpt/ui/feature/assets/financial/add/AddFinancialAssetViewModel.kt`

- [ ] **Step 1: Repo passthrough**

In `PriceRepository.kt`, add (next to `validateTicker`):

```kotlin
suspend fun searchTickers(query: String): SearchResult = source.searchTickers(query)
```

Add `import com.atlasfpt.domain.model.SearchResult`.

- [ ] **Step 2: Use case**

```kotlin
// app/src/main/java/com/atlasfpt/domain/usecase/SearchTickersUseCase.kt
package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.PriceRepository
import com.atlasfpt.domain.model.SearchResult
import javax.inject.Inject

class SearchTickersUseCase @Inject constructor(
    private val priceRepository: PriceRepository,
) {
    suspend operator fun invoke(query: String): SearchResult = priceRepository.searchTickers(query)
}
```

- [ ] **Step 3: Extend the ViewModel**

Replace `AddFinancialAssetViewModel.kt` with the version below. Key changes:
- Inject `SearchTickersUseCase`.
- Add `searchResults: SearchResult` to `AddFinancialAssetUiState`.
- Add a `private val searchQuery = MutableStateFlow("")` flow; subscribe in `init` with `debounce(300)` → `flatMapLatest { ... }`.
- `onTicker(raw)` updates both the form's `ticker` AND `searchQuery`, then schedules the validation (unchanged).
- New public `onSearchResultSelected(item: TickerSearchResult)` — sets ticker + clears search results (so the dropdown closes) and triggers validation.

```kotlin
package com.atlasfpt.ui.feature.assets.financial.add

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlasfpt.domain.model.FinancialLot
import com.atlasfpt.domain.model.QuoteResult
import com.atlasfpt.domain.model.SearchResult
import com.atlasfpt.domain.model.TickerQuote
import com.atlasfpt.domain.model.TickerSearchResult
import com.atlasfpt.domain.usecase.SaveFinancialAssetUseCase
import com.atlasfpt.domain.usecase.SearchTickersUseCase
import com.atlasfpt.domain.usecase.ValidateTickerUseCase
import com.atlasfpt.util.parseDecimal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed interface TickerState {
    data object Idle : TickerState
    data object Validating : TickerState
    data class Valid(val quote: TickerQuote) : TickerState
    data class Invalid(val reason: String) : TickerState
    data class Error(val reason: String) : TickerState
}

data class AddFinancialFormErrors(
    val ticker: String? = null,
    val name: String? = null,
    val purchaseDate: String? = null,
    val quantity: String? = null,
    val pricePerUnit: String? = null,
) {
    val hasAny: Boolean get() = listOf(ticker, name, purchaseDate, quantity, pricePerUnit).any { it != null }
}

data class AddFinancialAssetUiState(
    val ticker: String = "",
    val tickerState: TickerState = TickerState.Idle,
    val searchResults: SearchResult = SearchResult.Empty,
    val name: String = "",
    val purchaseDate: LocalDate? = null,
    val quantity: String = "",
    val pricePerUnit: String = "",
    val notes: String = "",
    val currencyCode: String = "",
    val formErrors: AddFinancialFormErrors = AddFinancialFormErrors(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSave: Boolean get() = !isLoading && !formErrors.hasAny && tickerState is TickerState.Valid
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class AddFinancialAssetViewModel @Inject constructor(
    @Suppress("UNUSED_PARAMETER") savedStateHandle: SavedStateHandle,
    private val validateTicker: ValidateTickerUseCase,
    private val searchTickers: SearchTickersUseCase,
    private val saveAsset: SaveFinancialAssetUseCase,
) : ViewModel() {

    private val _form = MutableStateFlow(AddFinancialAssetUiState())
    private val searchQuery = MutableStateFlow("")
    private var nameUserEdited = false
    private var validationJob: Job? = null

    init {
        viewModelScope.launch {
            searchQuery
                .debounce(300)
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        flowOf(SearchResult.Empty)
                    } else {
                        flow {
                            emit(SearchResult.Loading)
                            emit(searchTickers(query))
                        }
                    }
                }
                .onEach { result -> _form.update { it.copy(searchResults = result) } }
                .collect { /* terminal */ }
        }
    }

    val uiState: StateFlow<AddFinancialAssetUiState> = _form
        .map { it.copy(formErrors = computeErrors(it)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AddFinancialAssetUiState().let {
                it.copy(formErrors = computeErrors(it))
            },
        )

    fun onTicker(raw: String) {
        val normalized = raw.trim().uppercase()
        _form.update {
            it.copy(
                ticker = normalized,
                tickerState = if (normalized.isBlank()) TickerState.Idle else TickerState.Validating,
            )
        }
        searchQuery.value = normalized
        validationJob?.cancel()
        if (normalized.isBlank()) return
        validationJob = viewModelScope.launch {
            delay(500) // debounce
            when (val result = validateTicker(normalized)) {
                is QuoteResult.Success -> {
                    val quote = result.quote
                    _form.update { current ->
                        current.copy(
                            tickerState = TickerState.Valid(quote),
                            currencyCode = quote.currencyCode,
                            name = if (!nameUserEdited) quote.displayName else current.name,
                            pricePerUnit = if (current.pricePerUnit.isBlank())
                                String.format(java.util.Locale.US, "%.2f", quote.price)
                            else current.pricePerUnit,
                        )
                    }
                }
                is QuoteResult.NotFound ->
                    _form.update {
                        it.copy(
                            tickerState = TickerState.Invalid(
                                "Ticker not found. Try the symbol exactly as on Yahoo Finance (e.g. AAPL, VWCE.DE, BTC-USD).",
                            ),
                        )
                    }
                is QuoteResult.Error ->
                    _form.update {
                        it.copy(
                            tickerState = TickerState.Error("Couldn't reach price source. Check your connection."),
                        )
                    }
            }
        }
    }

    fun onSearchResultSelected(item: TickerSearchResult) {
        // Fill the field, close the dropdown, run validation
        _form.update { it.copy(searchResults = SearchResult.Empty) }
        searchQuery.value = ""
        onTicker(item.symbol)
    }

    fun onSearchDismissed() { _form.update { it.copy(searchResults = SearchResult.Empty) } }

    fun onName(v: String) {
        nameUserEdited = true
        _form.update { it.copy(name = v) }
    }

    fun onPurchaseDate(v: LocalDate?) = _form.update { it.copy(purchaseDate = v) }
    fun onQuantity(v: String) = _form.update { it.copy(quantity = v) }
    fun onPricePerUnit(v: String) = _form.update { it.copy(pricePerUnit = v) }
    fun onNotes(v: String) = _form.update { it.copy(notes = v) }

    fun clearErrorMessage() = _form.update { it.copy(errorMessage = null) }

    fun save() {
        val s = _form.value
        if (computeErrors(s).hasAny || s.tickerState !is TickerState.Valid) return
        val quote = s.tickerState.quote
        val lot = FinancialLot(
            id = 0L,
            purchaseDate = s.purchaseDate!!,
            quantity = parseDecimal(s.quantity)!!,
            pricePerUnit = parseDecimal(s.pricePerUnit)!!,
        )
        _form.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                saveAsset(s.name.trim(), s.notes.trim().takeIf { it.isNotBlank() }, quote, lot)
                _form.update { it.copy(isLoading = false, isSaved = true) }
            } catch (t: IllegalStateException) {
                _form.update {
                    it.copy(isLoading = false, errorMessage = "An asset for this ticker already exists.")
                }
            } catch (t: Throwable) {
                _form.update {
                    it.copy(isLoading = false, errorMessage = "Couldn't save asset. Try again.")
                }
            }
        }
    }

    private fun computeErrors(s: AddFinancialAssetUiState): AddFinancialFormErrors {
        val today = LocalDate.now()
        val tickerErr = when (s.tickerState) {
            is TickerState.Valid -> null
            TickerState.Idle -> "Ticker is required"
            TickerState.Validating -> "Validating…"
            is TickerState.Invalid -> s.tickerState.reason
            is TickerState.Error -> s.tickerState.reason
        }
        val nameErr = if (s.name.isBlank()) "Name is required" else null
        val dateErr = when {
            s.purchaseDate == null -> "Purchase date is required"
            s.purchaseDate.isAfter(today) -> "Cannot be in the future"
            else -> null
        }
        val qtyErr = run {
            val v = parseDecimal(s.quantity)
            if (s.quantity.isBlank() || v == null || v <= 0.0) "Quantity must be greater than 0" else null
        }
        val priceErr = run {
            val v = parseDecimal(s.pricePerUnit)
            if (s.pricePerUnit.isBlank() || v == null || v <= 0.0) "Price must be greater than 0" else null
        }
        return AddFinancialFormErrors(tickerErr, nameErr, dateErr, qtyErr, priceErr)
    }
}
```

- [ ] **Step 4: Compile + tests + commit**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (no existing test constructs `AddFinancialAssetViewModel` directly, so the new constructor param is safe — verify by grepping).

```bash
git add app/src/main/java/com/atlasfpt/data/repository/PriceRepository.kt \
        app/src/main/java/com/atlasfpt/domain/usecase/SearchTickersUseCase.kt \
        app/src/main/java/com/atlasfpt/ui/feature/assets/financial/add/AddFinancialAssetViewModel.kt
git commit -m "feat(#5): wire SearchTickersUseCase + debounced search flow into ViewModel"
```

---

### Task 4: UI — search dropdown under the ticker field

**Files:**
- Modify: `app/src/main/java/com/atlasfpt/ui/feature/assets/financial/add/AddFinancialAssetScreen.kt`

- [ ] **Step 1: Render the dropdown**

Inside the existing ticker `item { Column { ... } }` block, after the `OutlinedTextField` and BEFORE the existing `when (ts) { ... }` for `tickerState`, insert the dropdown:

```kotlin
val results = state.searchResults
when (results) {
    SearchResult.Empty -> Unit
    SearchResult.Loading -> {
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Searching…", style = MaterialTheme.typography.bodySmall)
        }
    }
    is SearchResult.Success -> {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        ) {
            Column {
                results.items.take(8).forEach { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.onSearchResultSelected(item) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(item.symbol, style = MaterialTheme.typography.titleSmall)
                        val subtitle = listOfNotNull(item.displayName, item.exchange, item.typeLabel)
                            .joinToString(" · ")
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    SearchResult.NoMatches -> Text(
        "No matches found.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    is SearchResult.Error -> Text(
        "Couldn't search. Check your connection.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 8.dp),
    )
}
```

Add the imports the new block needs (most already present):
```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import com.atlasfpt.domain.model.SearchResult
```

The existing `Row`, `Column`, `Spacer`, `Text`, `CircularProgressIndicator`, `Modifier`, `dp`, `MaterialTheme`, `OutlinedTextField`, `HorizontalDivider`, `Surface` should all already be available via the wildcard import at the top (`androidx.compose.foundation.layout.*`, `androidx.compose.material3.*`). If `HorizontalDivider` or `Surface` doesn't resolve, add them.

- [ ] **Step 2: Build + tests**

Run: `./gradlew :app:assembleDebug && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/atlasfpt/ui/feature/assets/financial/add/AddFinancialAssetScreen.kt
git commit -m "feat(#5): render ticker search dropdown below the field"
```

---

### Task 5: Install, smoke, push, PR

- [ ] **Step 1: Install on emulator**

```bash
/Users/pedrobatista/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
/Users/pedrobatista/Library/Android/sdk/platform-tools/adb shell am start -n com.atlasfpt/.MainActivity
```

- [ ] **Step 2: Smoke**

Navigate to Assets → "+" → Financial. Type `CSPX` and confirm:
- After ~300ms, a dropdown of candidate symbols appears beneath the field.
- Each row shows symbol + name + exchange + type.
- Tapping a row fills the ticker field, closes the dropdown, and "Checking…" → "✓ …" appears.
- Manual entry still works (type `AAPL`, dropdown shows matches OR no-matches, but the validation triggers from the field text independently).
- Disconnect network → typing produces "Couldn't search…" instead of crashing.

- [ ] **Step 3: Push + PR**

```bash
git push -u origin feat/5-ticker-search
gh pr create --title "feat(#5): ticker search-as-you-type dropdown on Add Financial Asset" --body "$(cat <<'EOF'
## Summary
Adds a search-as-you-type dropdown under the ticker field on `AddFinancialAssetScreen`, mirroring the Yahoo Finance search UX. As the user types (debounced 300ms), suggestions appear; selecting one fills the field and triggers the existing validation pipeline.

- Network: new `v1/finance/search` call on `YahooFinanceApi` + DTO + impl in `YahooPriceSource`.
- Domain: `TickerSearchResult` model + `SearchResult` sealed sum type covering Empty / Loading / Success / NoMatches / Error.
- Use case: `SearchTickersUseCase` wraps the repo passthrough.
- ViewModel: debounced `searchQuery` flow drives `searchResults`; selection routes through existing `onTicker(...)` to keep the validation flow intact.
- UI: dropdown rendered as a `Surface` below the field, with loading / no-matches / error variants.

Closes #5.

## Test plan
- [x] `./gradlew :app:testDebugUnitTest` — green
- [x] `./gradlew :app:assembleDebug` — green
- [ ] Manual: type `CSPX`, confirm dropdown shows multiple listings; tap one to autofill; verify the existing "Checking… → ✓" validation still fires.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Out of Scope

- Reuse of the dropdown on `AddLot` — leave as a follow-up.
- Caching search results across queries.
- Persisting recent searches.
