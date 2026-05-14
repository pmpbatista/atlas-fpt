# Live Euribor Fetching (#31) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fetch Euribor 1M/3M/6M/12M live from the **ECB Data Portal**, cache per tenor, refresh from the existing `RefreshPricesWorker`. Real-estate detail surfaces the effective rate (`reference + spread`) plus a timestamp, and an Edit dialog lets the user override the cached value when the live fetch is unavailable.

**Design decisions (agreed):**
- **Source:** ECB Data Portal SDW (`data-api.ecb.europa.eu`), CSV format. Series codes: `D.U2.EUR.RT.MM.EURIBOR{N}MD_.HSTA` for 1M/3M/6M and `D.U2.EUR.RT.MM.EURIBOR1YD_.HSTA` for 12M.
- **Manual override:** an Edit dialog on the real-estate detail screen — tapping the Euribor row opens a value + date input. Same flow whether seeding for the first time or correcting a stale auto-fetch.
- **Display:** real-estate detail only.

**Architecture:**
- New `EuriborSource` interface (parallel to `PriceSource`) and `EcbEuriborSource` implementation that hits the ECB CSV endpoint with manual parsing (no Retrofit converter for CSV).
- New `EuriborRepository` (`@Singleton`) backed by `SharedPreferences` (separate file `atlas_euribor` to keep keys isolated). Exposes `StateFlow<EuriborCache>` plus `setManual(tenor, value, asOf)`.
- New `RefreshEuriborUseCase` invoked by the existing `RefreshPricesWorker` after the price refresh — independent error handling per tenor.
- `RealEstateDetailViewModel` injects `EuriborRepository`, exposes the matching `EuriborRate?` for the asset's `referenceRate`, plus a setter for manual override.
- `RealEstateDetailScreen` renders the Euribor info inside `DebtSection` (when `interestType == VARIABLE`), and opens a `ManualEuriborDialog` on tap.

**Tech Stack:** Kotlin 2.0.21, Retrofit 2 (raw `ResponseBody`), kotlinx.coroutines, Compose Material 3, SharedPreferences.

---

### Task 1: Branch (done)

Branch `feat/31-euribor-live-fetch` is checked out from `6589f1a`.

---

### Task 2: Network — ECB Data Portal API + DI + DTO-free CSV parsing

**Files:**
- Create: `app/src/main/java/com/atlasfpt/data/network/EcbDataApi.kt`
- Modify: `app/src/main/java/com/atlasfpt/di/NetworkModule.kt` (add a named ECB Retrofit instance OR a second OkHttp-backed Retrofit)
- Create: `app/src/main/java/com/atlasfpt/data/network/EuriborSource.kt`
- Create: `app/src/main/java/com/atlasfpt/data/network/impl/EcbEuriborSource.kt`
- Create: `app/src/main/java/com/atlasfpt/domain/model/EuriborRate.kt`
- Create: `app/src/main/java/com/atlasfpt/domain/model/EuriborFetchResult.kt`

- [ ] **Step 1: Domain models**

`app/src/main/java/com/atlasfpt/domain/model/EuriborRate.kt`:

```kotlin
package com.atlasfpt.domain.model

import java.time.Instant

/** A snapshot of one Euribor tenor. */
data class EuriborRate(
    val tenor: ReferenceRate,
    val value: Double,        // already in percent, e.g. 3.123 means 3.123%
    val asOf: Instant,        // the date the rate is from (TIME_PERIOD)
    val source: Source,
) {
    enum class Source { ECB, MANUAL }
}
```

`app/src/main/java/com/atlasfpt/domain/model/EuriborFetchResult.kt`:

```kotlin
package com.atlasfpt.domain.model

sealed interface EuriborFetchResult {
    data class Success(val rate: EuriborRate) : EuriborFetchResult
    data class Error(val reason: String) : EuriborFetchResult
}
```

- [ ] **Step 2: API + source interface**

`app/src/main/java/com/atlasfpt/data/network/EcbDataApi.kt`:

```kotlin
package com.atlasfpt.data.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface EcbDataApi {
    /**
     * ECB SDW data endpoint. `key` is the full series key (e.g. "D.U2.EUR.RT.MM.EURIBOR3MD_.HSTA")
     * within the dataset "FM" (financial markets). Returns CSV which we parse manually.
     */
    @GET("service/data/FM/{key}")
    suspend fun fetchSeries(
        @Path("key") key: String,
        @Query("format") format: String = "csvdata",
        @Query("lastNObservations") lastN: Int = 1,
    ): Response<ResponseBody>
}
```

`app/src/main/java/com/atlasfpt/data/network/EuriborSource.kt`:

```kotlin
package com.atlasfpt.data.network

import com.atlasfpt.domain.model.EuriborFetchResult
import com.atlasfpt.domain.model.ReferenceRate

interface EuriborSource {
    suspend fun fetch(tenor: ReferenceRate): EuriborFetchResult
}
```

- [ ] **Step 3: ECB CSV parser + implementation**

`app/src/main/java/com/atlasfpt/data/network/impl/EcbEuriborSource.kt`:

```kotlin
package com.atlasfpt.data.network.impl

import android.util.Log
import com.atlasfpt.data.network.EcbDataApi
import com.atlasfpt.data.network.EuriborSource
import com.atlasfpt.domain.model.EuriborFetchResult
import com.atlasfpt.domain.model.EuriborRate
import com.atlasfpt.domain.model.ReferenceRate
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EcbEuriborSource @Inject constructor(
    private val api: EcbDataApi,
) : EuriborSource {

    override suspend fun fetch(tenor: ReferenceRate): EuriborFetchResult {
        val key = ecbKey(tenor)
        return runCatching {
            val response = api.fetchSeries(key)
            if (!response.isSuccessful) {
                EuriborFetchResult.Error("HTTP ${response.code()}")
            } else {
                val body = response.body()?.string().orEmpty()
                parse(tenor, body)
                    ?: EuriborFetchResult.Error("Unrecognised CSV shape")
            }
        }.getOrElse { t ->
            runCatching { Log.w("EcbEuriborSource", "fetch($tenor) failed", t) }
            EuriborFetchResult.Error(t.message ?: t.javaClass.simpleName)
        }
    }

    /**
     * ECB SDW CSV has a header line followed by one row per observation.
     * We need the columns `TIME_PERIOD` (date) and `OBS_VALUE` (rate).
     * Column count varies, so we look up indices from the header rather than positional.
     */
    private fun parse(tenor: ReferenceRate, csv: String): EuriborFetchResult? {
        val lines = csv.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.size < 2) return null
        val header = splitCsvLine(lines.first())
        val timeIdx = header.indexOf("TIME_PERIOD").takeIf { it >= 0 } ?: return null
        val valueIdx = header.indexOf("OBS_VALUE").takeIf { it >= 0 } ?: return null
        val row = splitCsvLine(lines.last())
        val date = row.getOrNull(timeIdx)?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        } ?: return null
        val value = row.getOrNull(valueIdx)?.toDoubleOrNull() ?: return null
        return EuriborFetchResult.Success(
            EuriborRate(
                tenor = tenor,
                value = value,
                asOf = date.atStartOfDay(ZoneOffset.UTC).toInstant(),
                source = EuriborRate.Source.ECB,
            ),
        )
    }

    /** Split a CSV line on commas, respecting quoted strings. */
    private fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> { out.add(buf.toString()); buf.clear() }
                else -> buf.append(ch)
            }
        }
        out.add(buf.toString())
        return out
    }

    private fun ecbKey(tenor: ReferenceRate): String = when (tenor) {
        ReferenceRate.EURIBOR_1M -> "D.U2.EUR.RT.MM.EURIBOR1MD_.HSTA"
        ReferenceRate.EURIBOR_3M -> "D.U2.EUR.RT.MM.EURIBOR3MD_.HSTA"
        ReferenceRate.EURIBOR_6M -> "D.U2.EUR.RT.MM.EURIBOR6MD_.HSTA"
        ReferenceRate.EURIBOR_12M -> "D.U2.EUR.RT.MM.EURIBOR1YD_.HSTA"
    }
}
```

- [ ] **Step 4: Wire DI for the ECB Retrofit instance**

Modify `app/src/main/java/com/atlasfpt/di/NetworkModule.kt` to add a second Retrofit instance for the ECB base URL. The existing module already provides one Retrofit pinned to Yahoo; we need a separate one for `https://data-api.ecb.europa.eu/`.

Add to `NetworkModule`:

```kotlin
private const val ECB_BASE_URL = "https://data-api.ecb.europa.eu/"

@Provides
@Singleton
@javax.inject.Named("ecb")
fun provideEcbRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
    .baseUrl(ECB_BASE_URL)
    .client(client)
    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
    .build()

@Provides
@Singleton
fun provideEcbDataApi(@javax.inject.Named("ecb") retrofit: Retrofit): com.atlasfpt.data.network.EcbDataApi =
    retrofit.create(com.atlasfpt.data.network.EcbDataApi::class.java)
```

And rename the existing Yahoo provider to be qualified too:

```kotlin
@Provides
@Singleton
@javax.inject.Named("yahoo")
fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
    .baseUrl(YAHOO_BASE_URL)
    ...
```

And update `provideYahooFinanceApi`:

```kotlin
@Provides
@Singleton
fun provideYahooFinanceApi(@javax.inject.Named("yahoo") retrofit: Retrofit): YahooFinanceApi =
    retrofit.create(YahooFinanceApi::class.java)
```

Add to `NetworkBindings`:

```kotlin
@Binds
@Singleton
abstract fun bindEuriborSource(impl: EcbEuriborSource): EuriborSource
```

Import: `import com.atlasfpt.data.network.EuriborSource` and `import com.atlasfpt.data.network.impl.EcbEuriborSource` at the top of the file.

- [ ] **Step 5: Compile + commit**

```bash
./gradlew :app:compileDebugKotlin
```

```bash
git add app/src/main/java/com/atlasfpt/data/network/EcbDataApi.kt \
        app/src/main/java/com/atlasfpt/data/network/EuriborSource.kt \
        app/src/main/java/com/atlasfpt/data/network/impl/EcbEuriborSource.kt \
        app/src/main/java/com/atlasfpt/domain/model/EuriborRate.kt \
        app/src/main/java/com/atlasfpt/domain/model/EuriborFetchResult.kt \
        app/src/main/java/com/atlasfpt/di/NetworkModule.kt
git commit -m "feat(#31): ECB Data Portal source for Euribor rates"
```

---

### Task 3: Repository + use case + worker integration

**Files:**
- Create: `app/src/main/java/com/atlasfpt/data/repository/EuriborRepository.kt`
- Create: `app/src/main/java/com/atlasfpt/domain/usecase/RefreshEuriborUseCase.kt`
- Create: `app/src/main/java/com/atlasfpt/domain/usecase/SetEuriborManualUseCase.kt`
- Modify: `app/src/main/java/com/atlasfpt/data/worker/RefreshPricesWorker.kt`

- [ ] **Step 1: Repository**

```kotlin
// app/src/main/java/com/atlasfpt/data/repository/EuriborRepository.kt
package com.atlasfpt.data.repository

import android.content.Context
import com.atlasfpt.domain.model.EuriborRate
import com.atlasfpt.domain.model.ReferenceRate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class EuriborCache(val byTenor: Map<ReferenceRate, EuriborRate> = emptyMap()) {
    fun get(tenor: ReferenceRate): EuriborRate? = byTenor[tenor]
}

@Singleton
class EuriborRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs = context.getSharedPreferences("atlas_euribor", Context.MODE_PRIVATE)
    private val _cache = MutableStateFlow(loadCache())
    val cache: StateFlow<EuriborCache> = _cache.asStateFlow()

    fun put(rate: EuriborRate) {
        prefs.edit()
            .putString(keyValue(rate.tenor), rate.value.toString())
            .putLong(keyAt(rate.tenor), rate.asOf.toEpochMilli())
            .putString(keySource(rate.tenor), rate.source.name)
            .apply()
        _cache.value = loadCache()
    }

    private fun loadCache(): EuriborCache {
        val map = ReferenceRate.values().mapNotNull { tenor ->
            val value = prefs.getString(keyValue(tenor), null)?.toDoubleOrNull() ?: return@mapNotNull null
            val atMillis = if (prefs.contains(keyAt(tenor))) prefs.getLong(keyAt(tenor), 0L) else return@mapNotNull null
            val sourceName = prefs.getString(keySource(tenor), EuriborRate.Source.ECB.name) ?: EuriborRate.Source.ECB.name
            tenor to EuriborRate(
                tenor = tenor,
                value = value,
                asOf = Instant.ofEpochMilli(atMillis),
                source = runCatching { EuriborRate.Source.valueOf(sourceName) }.getOrDefault(EuriborRate.Source.ECB),
            )
        }.toMap()
        return EuriborCache(map)
    }

    private fun keyValue(tenor: ReferenceRate) = "${tenor.name}_value"
    private fun keyAt(tenor: ReferenceRate) = "${tenor.name}_at"
    private fun keySource(tenor: ReferenceRate) = "${tenor.name}_source"
}
```

- [ ] **Step 2: Use cases**

```kotlin
// app/src/main/java/com/atlasfpt/domain/usecase/RefreshEuriborUseCase.kt
package com.atlasfpt.domain.usecase

import com.atlasfpt.data.network.EuriborSource
import com.atlasfpt.data.repository.EuriborRepository
import com.atlasfpt.domain.model.EuriborFetchResult
import com.atlasfpt.domain.model.ReferenceRate
import javax.inject.Inject

class RefreshEuriborUseCase @Inject constructor(
    private val source: EuriborSource,
    private val repository: EuriborRepository,
) {
    /**
     * Fetches all four tenors. Successful tenors are committed to the cache;
     * failures are silently dropped so a single network blip doesn't lose
     * the others.
     */
    suspend operator fun invoke() {
        ReferenceRate.values().forEach { tenor ->
            when (val result = source.fetch(tenor)) {
                is EuriborFetchResult.Success -> repository.put(result.rate)
                is EuriborFetchResult.Error -> Unit  // keep prior cache
            }
        }
    }
}
```

```kotlin
// app/src/main/java/com/atlasfpt/domain/usecase/SetEuriborManualUseCase.kt
package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.EuriborRepository
import com.atlasfpt.domain.model.EuriborRate
import com.atlasfpt.domain.model.ReferenceRate
import java.time.Instant
import javax.inject.Inject

class SetEuriborManualUseCase @Inject constructor(
    private val repository: EuriborRepository,
) {
    operator fun invoke(tenor: ReferenceRate, value: Double, asOf: Instant) {
        repository.put(
            EuriborRate(tenor = tenor, value = value, asOf = asOf, source = EuriborRate.Source.MANUAL),
        )
    }
}
```

- [ ] **Step 3: Hook Euribor refresh into `RefreshPricesWorker`**

Modify `app/src/main/java/com/atlasfpt/data/worker/RefreshPricesWorker.kt` to inject `RefreshEuriborUseCase` and invoke it after the price refresh. Update the worker's `doWork()`:

```kotlin
@HiltWorker
class RefreshPricesWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val priceRepository: PriceRepository,
    private val settingsRepository: SettingsRepository,
    private val refreshEuribor: RefreshEuriborUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!settingsRepository.settings.value.backgroundRefreshEnabled) {
            return Result.success()
        }
        return try {
            val result = priceRepository.refreshAll()
            if (result.succeeded > 0) {
                settingsRepository.setLastPriceRefreshAt(System.currentTimeMillis())
            }
            // Always run Euribor refresh — independent of price-refresh outcome.
            runCatching { refreshEuribor() }
            when {
                result.failed == 0 -> Result.success()
                result.succeeded > 0 -> Result.success()
                else -> Result.retry()
            }
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "refresh_prices"
    }
}
```

Add: `import com.atlasfpt.domain.usecase.RefreshEuriborUseCase`.

- [ ] **Step 4: Build + tests**

```bash
./gradlew :app:assembleDebug && ./gradlew :app:testDebugUnitTest
```

If any worker-related test breaks because of the new constructor param, escalate — there's no existing test for `RefreshPricesWorker` (it was added in #30 without unit tests, intentionally).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/atlasfpt/data/repository/EuriborRepository.kt \
        app/src/main/java/com/atlasfpt/domain/usecase/RefreshEuriborUseCase.kt \
        app/src/main/java/com/atlasfpt/domain/usecase/SetEuriborManualUseCase.kt \
        app/src/main/java/com/atlasfpt/data/worker/RefreshPricesWorker.kt
git commit -m "feat(#31): EuriborRepository + manual/auto use cases + worker integration"
```

---

### Task 4: ViewModel + Real-Estate detail UI

**Files:**
- Modify: `app/src/main/java/com/atlasfpt/ui/feature/assets/realestate/detail/RealEstateDetailViewModel.kt`
- Modify: `app/src/main/java/com/atlasfpt/ui/feature/assets/realestate/detail/RealEstateDetailScreen.kt`

- [ ] **Step 1: Extend the ViewModel**

Replace the file with:

```kotlin
package com.atlasfpt.ui.feature.assets.realestate.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlasfpt.data.repository.EuriborRepository
import com.atlasfpt.data.repository.TransactionRepository
import com.atlasfpt.data.settings.SettingsRepository
import com.atlasfpt.domain.model.EuriborRate
import com.atlasfpt.domain.model.RealEstateAsset
import com.atlasfpt.domain.model.ReferenceRate
import com.atlasfpt.domain.model.Transaction
import com.atlasfpt.domain.usecase.GetRealEstateUseCase
import com.atlasfpt.domain.usecase.SetEuriborManualUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class RealEstateDetailUiState(
    val asset: RealEstateAsset? = null,
    val loadError: Boolean = false,
    val linkedTransactions: List<Transaction> = emptyList(),
    val currencySymbol: String = "€",
    val euribor: EuriborRate? = null,           // matching tenor, or null if unknown
) {
    val equity: Double? get() = asset?.let { it.currentValue - (it.outstandingDebt ?: 0.0) }

    /** value already in percent (e.g. 3.62 means 3.62%) when both euribor and spread are known. */
    val effectiveRate: Double?
        get() {
            val a = asset ?: return null
            val euri = euribor?.value ?: return null
            val spread = a.spread ?: 0.0
            return euri + spread
        }
}

@HiltViewModel
class RealEstateDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getRealEstate: GetRealEstateUseCase,
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val euriborRepository: EuriborRepository,
    private val setEuriborManual: SetEuriborManualUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(RealEstateDetailUiState())
    val uiState: StateFlow<RealEstateDetailUiState> = _state.asStateFlow()

    init {
        val id = savedStateHandle.get<String>("assetId")?.toLongOrNull()
        if (id == null) {
            _state.update { it.copy(loadError = true) }
        } else {
            viewModelScope.launch {
                val asset = runCatching { getRealEstate(id) }.getOrNull()
                if (asset == null) {
                    _state.update { it.copy(loadError = true) }
                } else {
                    _state.update { it.copy(asset = asset) }
                }
            }
            viewModelScope.launch {
                transactionRepository.observeByAssetId(id).collect { txs ->
                    _state.update { it.copy(linkedTransactions = txs) }
                }
            }
            viewModelScope.launch {
                settingsRepository.settings.collect { s ->
                    _state.update { it.copy(currencySymbol = s.currencySymbol) }
                }
            }
            viewModelScope.launch {
                euriborRepository.cache.collect { cache ->
                    val tenor = _state.value.asset?.referenceRate
                    _state.update { it.copy(euribor = tenor?.let(cache::get)) }
                }
            }
        }
    }

    fun onManualEuriborSet(tenor: ReferenceRate, value: Double, asOf: Instant) {
        setEuriborManual(tenor, value, asOf)
    }
}
```

- [ ] **Step 2: Render the Euribor block in `DebtSection` + add Edit dialog**

In `RealEstateDetailScreen.kt`, find the existing `DebtSection`:

```kotlin
@Composable
private fun DebtSection(asset: RealEstateAsset) {
    Column {
        Text("Debt", style = MaterialTheme.typography.titleSmall)
        if (asset.debtAmount == null) {
            Text("Bought outright")
            return@Column
        }
        Text("Initial: ${CurrencyFormatter.formatAbsoluteForCurrency(asset.debtAmount, asset.currencyCode)}")
        asset.outstandingDebt?.let {
            Text("Outstanding: ${CurrencyFormatter.formatAbsoluteForCurrency(it, asset.currencyCode)}")
        }
        Text(describeInterest(asset))
        asset.creditEndDate?.let {
            Text("End date: $it")
            Text(monthsRemaining(it))
        }
    }
}
```

The screen needs to pass the `EuriborRate?` + an edit callback through. Update the call site in the parent `LazyColumn`:

```kotlin
item {
    Spacer(Modifier.height(16.dp))
    DebtSection(
        asset = asset,
        euribor = state.euribor,
        effectiveRate = state.effectiveRate,
        onSetManual = { value, asOf ->
            asset.referenceRate?.let { tenor ->
                viewModel.onManualEuriborSet(tenor, value, asOf)
            }
        },
    )
}
```

And replace `DebtSection` with:

```kotlin
@Composable
private fun DebtSection(
    asset: RealEstateAsset,
    euribor: EuriborRate?,
    effectiveRate: Double?,
    onSetManual: (Double, Instant) -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }

    Column {
        Text("Debt", style = MaterialTheme.typography.titleSmall)
        if (asset.debtAmount == null) {
            Text("Bought outright")
            return@Column
        }
        Text("Initial: ${CurrencyFormatter.formatAbsoluteForCurrency(asset.debtAmount, asset.currencyCode)}")
        asset.outstandingDebt?.let {
            Text("Outstanding: ${CurrencyFormatter.formatAbsoluteForCurrency(it, asset.currencyCode)}")
        }
        Text(describeInterest(asset))
        if (asset.interestType == com.atlasfpt.domain.model.InterestType.VARIABLE && asset.referenceRate != null) {
            EuriborRow(
                tenor = asset.referenceRate,
                euribor = euribor,
                effectiveRate = effectiveRate,
                onClick = { dialogOpen = true },
            )
        }
        asset.creditEndDate?.let {
            Text("End date: $it")
            Text(monthsRemaining(it))
        }
    }

    if (dialogOpen && asset.referenceRate != null) {
        ManualEuriborDialog(
            tenor = asset.referenceRate,
            current = euribor,
            onDismiss = { dialogOpen = false },
            onSave = { value, asOf ->
                onSetManual(value, asOf)
                dialogOpen = false
            },
        )
    }
}

@Composable
private fun EuriborRow(
    tenor: ReferenceRate,
    euribor: EuriborRate?,
    effectiveRate: Double?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            if (euribor != null) {
                Text(
                    text = "${tenor.label} ${formatPercent(euribor.value)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val srcLabel = when (euribor.source) {
                    EuriborRate.Source.ECB -> "ECB"
                    EuriborRate.Source.MANUAL -> "manual"
                }
                Text(
                    text = "$srcLabel · ${relativeTimeString(euribor.asOf.toEpochMilli())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "${tenor.label} — no value yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Tap to enter manually",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (effectiveRate != null) {
            Text(
                text = "Effective ${formatPercent(effectiveRate)}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ManualEuriborDialog(
    tenor: ReferenceRate,
    current: EuriborRate?,
    onDismiss: () -> Unit,
    onSave: (Double, Instant) -> Unit,
) {
    var valueText by remember { mutableStateOf(current?.value?.toString().orEmpty()) }
    var dateText by remember {
        mutableStateOf(
            current?.asOf?.let { java.time.LocalDate.ofInstant(it, java.time.ZoneOffset.UTC).toString() }
                ?: java.time.LocalDate.now().toString()
        )
    }
    val parsedValue = valueText.replace(',', '.').toDoubleOrNull()
    val parsedDate = runCatching { java.time.LocalDate.parse(dateText) }.getOrNull()
    val canSave = parsedValue != null && parsedDate != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set ${tenor.label} manually") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text("Rate (%)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("As-of date (YYYY-MM-DD)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        parsedValue!!,
                        parsedDate!!.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
```

Add the imports at the top of the file:

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.atlasfpt.domain.model.EuriborRate
import com.atlasfpt.domain.model.ReferenceRate
import com.atlasfpt.util.formatPercent
import java.time.Instant
```

(Some imports may already be present — only add what's missing.)

- [ ] **Step 3: Build + test**

```bash
./gradlew :app:assembleDebug && ./gradlew :app:testDebugUnitTest
```

If `RealEstateDetailViewModelTest` exists and constructs the VM directly, update its setup to mock `EuriborRepository` and `SetEuriborManualUseCase`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/atlasfpt/ui/feature/assets/realestate/detail/RealEstateDetailViewModel.kt \
        app/src/main/java/com/atlasfpt/ui/feature/assets/realestate/detail/RealEstateDetailScreen.kt
# also add any test files you had to touch
git commit -m "feat(#31): surface Euribor + manual override on real-estate detail"
```

---

### Task 5: Install, smoke, push, PR

- [ ] **Step 1: Install on emulator**

```bash
/Users/pedrobatista/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
/Users/pedrobatista/Library/Android/sdk/platform-tools/adb shell am start -n com.atlasfpt/.MainActivity
```

- [ ] **Step 2: Smoke**

Open a real-estate property with `interestType = VARIABLE` and a `referenceRate`. Confirm:
- Initially: "Euribor 3M — no value yet" + "Tap to enter manually"
- Tap row → dialog with value + date fields → save → Euribor + effective rate appear
- Source label reads "manual" with the entered date
- Force the worker (`adb shell cmd jobscheduler run -f com.atlasfpt 0`) → after a moment, source label flips to "ECB" with today's date (assuming network is OK)
- Disconnect network → manual override still saves; ECB fetch fails silently, cached value (manual or last-ECB) persists

- [ ] **Step 3: Push + PR**

```bash
git push -u origin feat/31-euribor-live-fetch
gh pr create --title "feat(#31): live Euribor fetching from ECB + manual override" --body "$(cat <<'EOF'
## Summary
Fetches Euribor 1M/3M/6M/12M live from the ECB Data Portal (CSV format) and surfaces the effective rate on the real-estate detail screen. Tap the Euribor row to enter a manual override when the live fetch is unavailable.

- **Network:** new `EcbDataApi` Retrofit interface; named DI scopes (\`@Named("yahoo")\`, \`@Named("ecb")\`) keep the two Retrofit instances isolated.
- **Source:** \`EcbEuriborSource\` parses ECB SDW CSV (header-index-aware so column order changes don't break parsing).
- **Repository:** \`EuriborRepository\` caches one record per tenor in a dedicated \`atlas_euribor\` SharedPreferences file; exposes \`StateFlow<EuriborCache>\`.
- **Worker:** \`RefreshPricesWorker\` now also invokes \`RefreshEuriborUseCase\` after the price refresh — independent error handling per tenor.
- **UI:** real-estate detail's \`DebtSection\` shows the matching Euribor + source + as-of timestamp + effective rate (\`euribor + spread\`). Tap-to-edit \`AlertDialog\` lets the user set the rate manually.

Closes #31.

## Source choice
ECB Data Portal SDW REST API (\`https://data-api.ecb.europa.eu/service/data/FM/{key}?format=csvdata&lastNObservations=1\`). Public, no auth, stable. Series keys per tenor encoded in \`EcbEuriborSource.ecbKey()\`.

## Test plan
- [x] \`./gradlew :app:testDebugUnitTest\` — green
- [x] \`./gradlew :app:assembleDebug\` — green
- [x] Installed on emulator
- [ ] **Manual verification (please confirm):**
  - Variable-rate property shows "Euribor — no value yet" initially
  - Manual dialog saves a value; effective rate appears
  - Force worker run (\`adb shell cmd jobscheduler run -f com.atlasfpt 0\`) — source flips to "ECB" with today's date
  - Network off → manual override still works; ECB fetch fails silently

## Out of scope
- Display on the assets list (per design decision)
- Historical Euribor charts
- Configurable refresh interval

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Out of Scope

- Showing the effective rate on the assets list (decided detail-only).
- Historical Euribor charts.
- Configurable fetch interval (rides on the daily price-refresh job).
