# Persons Filter for Timeline + Overview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a persons filter to Timeline + Overview. A chip on each screen opens a multi-select bottom sheet; selecting one or more persons restricts the visible transactions and recomputes Overview totals/breakdowns.

**Design decisions (already agreed):**
- **UX:** A chip on both screens. Timeline gets a third chip alongside Wallets/Range; Overview gets a chip row above the segmented totals.
- **Multi-select semantics:** OR — transaction matches if any selected person is attached.
- **Empty-persons transactions:** Hidden when filter is active.

**Architecture:**
- Filter state lives in each screen's ViewModel as `selectedPersonIds: Set<Long>` (`emptySet()` = no filter, all transactions visible).
- `GetTimelineUseCase` accepts a `Set<Long>` filter; if non-empty, it pre-filters the raw transaction list before `buildBars/buildScheduled/buildDays/headerTotal`.
- `GetOverviewUseCase` accepts the same filter; if empty, it uses the current DAO-aggregate fast path; if non-empty, it switches to observing transactions in the date range and computing totals in-memory.
- A new shared `PersonsFilterChip` composable encapsulates the chip rendering + bottom-sheet trigger.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose, Material 3, Hilt, Room, JUnit4 + MockK + Turbine.

---

### Task 1: Feature branch

**Files:** none

- [ ] **Step 1: Create the feature branch off main**

```bash
git checkout main && git pull --ff-only && git checkout -b feat/24-persons-filter
```

Expected: branch `feat/24-persons-filter` checked out from latest `origin/main` (HEAD at `bbaee73` after #44).

---

### Task 2: `GetTimelineUseCase` — accept person filter (TDD)

**Files:**
- Modify: `app/src/main/java/com/atlasfpt/domain/usecase/GetTimelineUseCase.kt`
- Create: `app/src/test/java/com/atlasfpt/domain/usecase/GetTimelineUseCaseTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.atlasfpt.domain.usecase

import app.cash.turbine.test
import com.atlasfpt.data.repository.TransactionRepository
import com.atlasfpt.domain.model.Category
import com.atlasfpt.domain.model.CategoryType
import com.atlasfpt.domain.model.Person
import com.atlasfpt.domain.model.Transaction
import com.atlasfpt.domain.model.TransactionType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class GetTimelineUseCaseTest {

    private val repo: TransactionRepository = mockk()

    private val cat = Category(1L, "Food", "", 0, CategoryType.EXPENSE)
    private val maria = Person(1L, "Maria")
    private val joao = Person(2L, "João")

    private fun tx(id: Long, amount: Double, persons: List<Person>) = Transaction(
        id = id,
        amount = amount,
        type = TransactionType.EXPENSE,
        category = cat,
        date = LocalDate.now(),
        note = null,
        photoUri = null,
        labels = emptyList(),
        persons = persons,
        recurringRuleId = null,
        isScheduled = false
    )

    @Test
    fun `empty filter returns all transactions`() = runTest {
        every { repo.observeAll() } returns flowOf(listOf(tx(1, 10.0, listOf(maria)), tx(2, 20.0, emptyList())))
        every { repo.observeScheduled() } returns flowOf(emptyList())

        val sut = GetTimelineUseCase(repo)

        sut(personFilterIds = emptySet()).test {
            val data = awaitItem()
            assertEquals(2, data.days.first().rows.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `non-empty filter keeps only matching persons and drops empty-persons rows`() = runTest {
        every { repo.observeAll() } returns flowOf(
            listOf(
                tx(1, 10.0, listOf(maria)),
                tx(2, 20.0, listOf(joao)),
                tx(3, 30.0, listOf(maria, joao)),
                tx(4, 40.0, emptyList())
            )
        )
        every { repo.observeScheduled() } returns flowOf(emptyList())

        val sut = GetTimelineUseCase(repo)

        sut(personFilterIds = setOf(maria.id)).test {
            val data = awaitItem()
            val rows = data.days.flatMap { it.rows }.map { it.transaction.id }.toSet()
            // tx 1 (Maria), tx 3 (Maria+João) match; tx 2 (João only) and tx 4 (none) don't
            assertEquals(setOf(1L, 3L), rows)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run test to verify it FAILS**

Run: `./gradlew :app:testDebugUnitTest --tests "com.atlasfpt.domain.usecase.GetTimelineUseCaseTest"`
Expected: FAIL — `personFilterIds` parameter doesn't exist.

- [ ] **Step 3: Implement**

Replace `GetTimelineUseCase.invoke()` with a filtered version:

```kotlin
operator fun invoke(personFilterIds: Set<Long> = emptySet()): Flow<TimelineData> = combine(
    transactionRepository.observeAll(),
    transactionRepository.observeScheduled()
) { all, scheduled ->
    val filtered = applyPersonFilter(all, personFilterIds)
    val filteredScheduled = applyPersonFilter(scheduled, personFilterIds)
    val headerTotal = filtered.sumOf { tx ->
        if (tx.type == TransactionType.EXPENSE) -tx.amount else tx.amount
    }
    TimelineData(
        headerTotal = headerTotal,
        bars = buildBars(filtered),
        scheduled = buildScheduled(filteredScheduled),
        days = buildDays(filtered)
    )
}

private fun applyPersonFilter(transactions: List<Transaction>, ids: Set<Long>): List<Transaction> {
    if (ids.isEmpty()) return transactions
    return transactions.filter { tx -> tx.persons.any { it.id in ids } }
}
```

- [ ] **Step 4: Run test to verify it PASSES**

Run: `./gradlew :app:testDebugUnitTest --tests "com.atlasfpt.domain.usecase.GetTimelineUseCaseTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Run full unit-test target**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/atlasfpt/domain/usecase/GetTimelineUseCase.kt \
        app/src/test/java/com/atlasfpt/domain/usecase/GetTimelineUseCaseTest.kt
git commit -m "feat(#24): filter Timeline by personFilterIds"
```

---

### Task 3: `GetOverviewUseCase` — accept person filter

**Files:**
- Modify: `app/src/main/java/com/atlasfpt/domain/usecase/GetOverviewUseCase.kt`

For the filtered case, we can't use the DAO's aggregate `getCategoryTotals` (it doesn't know about persons), so we observe `observeByDateRange` and compute totals in-memory. The empty-filter case keeps the DAO fast path.

- [ ] **Step 1: Replace the use case**

```kotlin
package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.CategoryRepository
import com.atlasfpt.data.repository.TransactionRepository
import com.atlasfpt.domain.model.Category
import com.atlasfpt.domain.model.Transaction
import com.atlasfpt.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.YearMonth
import javax.inject.Inject

data class CategoryBreakdown(
    val category: Category,
    val amount: Double,
    val percentage: Float,
    val transactionCount: Int
)

data class OverviewUiState(
    val totalExpense: Double = 0.0,
    val totalIncome: Double = 0.0,
    val expenseBreakdown: List<CategoryBreakdown> = emptyList(),
    val incomeBreakdown: List<CategoryBreakdown> = emptyList(),
    val isLoading: Boolean = true
)

class GetOverviewUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) {
    operator fun invoke(month: YearMonth, personFilterIds: Set<Long> = emptySet()): Flow<OverviewUiState> {
        val from = month.atDay(1)
        val to = month.atEndOfMonth()
        return if (personFilterIds.isEmpty()) {
            daoAggregatePath(from, to)
        } else {
            inMemoryFilteredPath(from, to, personFilterIds)
        }
    }

    private fun daoAggregatePath(from: java.time.LocalDate, to: java.time.LocalDate): Flow<OverviewUiState> {
        return combine(
            transactionRepository.getCategoryTotals(TransactionType.EXPENSE, from, to),
            transactionRepository.getCategoryTotals(TransactionType.INCOME, from, to),
            categoryRepository.observeAll()
        ) { expenseTotals, incomeTotals, categories ->
            val categoryMap = categories.associateBy { it.id }
            val totalExpense = expenseTotals.sumOf { it.total }
            val totalIncome = incomeTotals.sumOf { it.total }

            fun breakdown(totals: List<com.atlasfpt.data.db.dao.CategoryTotal>, grandTotal: Double) =
                totals.mapNotNull { ct ->
                    val cat = categoryMap[ct.categoryId] ?: return@mapNotNull null
                    CategoryBreakdown(
                        category = cat,
                        amount = ct.total,
                        percentage = if (grandTotal > 0) (ct.total / grandTotal * 100).toFloat() else 0f,
                        transactionCount = ct.count
                    )
                }.sortedByDescending { it.amount }

            OverviewUiState(
                totalExpense = totalExpense,
                totalIncome = totalIncome,
                expenseBreakdown = breakdown(expenseTotals, totalExpense),
                incomeBreakdown = breakdown(incomeTotals, totalIncome),
                isLoading = false
            )
        }
    }

    private fun inMemoryFilteredPath(
        from: java.time.LocalDate,
        to: java.time.LocalDate,
        personFilterIds: Set<Long>
    ): Flow<OverviewUiState> {
        return combine(
            transactionRepository.observeByDateRange(from, to),
            categoryRepository.observeAll()
        ) { transactions, categories ->
            val filtered = transactions.filter { tx -> tx.persons.any { it.id in personFilterIds } }
            val expense = filtered.filter { it.type == TransactionType.EXPENSE }
            val income = filtered.filter { it.type == TransactionType.INCOME }
            val totalExpense = expense.sumOf { it.amount }
            val totalIncome = income.sumOf { it.amount }
            val categoryMap = categories.associateBy { it.id }

            fun aggregate(rows: List<Transaction>, grandTotal: Double): List<CategoryBreakdown> {
                return rows.groupBy { it.category.id }
                    .mapNotNull { (catId, txs) ->
                        val cat = categoryMap[catId] ?: return@mapNotNull null
                        val sum = txs.sumOf { it.amount }
                        CategoryBreakdown(
                            category = cat,
                            amount = sum,
                            percentage = if (grandTotal > 0) (sum / grandTotal * 100).toFloat() else 0f,
                            transactionCount = txs.size
                        )
                    }.sortedByDescending { it.amount }
            }

            OverviewUiState(
                totalExpense = totalExpense,
                totalIncome = totalIncome,
                expenseBreakdown = aggregate(expense, totalExpense),
                incomeBreakdown = aggregate(income, totalIncome),
                isLoading = false
            )
        }
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (The existing `OverviewViewModel` still calls `getOverview(month)` — the new param has a default of `emptySet()`, so the call site still compiles.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/atlasfpt/domain/usecase/GetOverviewUseCase.kt
git commit -m "feat(#24): filter Overview by personFilterIds (in-memory path when active)"
```

---

### Task 4: New shared `PersonsFilterChip` composable + bottom sheet

**Files:**
- Create: `app/src/main/java/com/atlasfpt/ui/component/PersonsFilterChip.kt`

- [ ] **Step 1: Write the component**

```kotlin
package com.atlasfpt.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.atlasfpt.domain.model.Person

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonsFilterChip(
    persons: List<Person>,
    selectedIds: Set<Long>,
    onSelectionChanged: (Set<Long>) -> Unit
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val label = when {
        selectedIds.isEmpty() -> "All people"
        selectedIds.size == 1 -> persons.firstOrNull { it.id in selectedIds }?.name ?: "1 person"
        else -> "${selectedIds.size} people"
    }

    AssistChip(
        onClick = { sheetOpen = true },
        label = { Text(label) },
        leadingIcon = {
            Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
    )

    if (sheetOpen) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = state
        ) {
            Text(
                text = "Filter by person",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            if (persons.isEmpty()) {
                Text(
                    text = "No persons yet — add them in Settings",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                persons.forEach { person ->
                    val isChecked = person.id in selectedIds
                    ListItem(
                        headlineContent = { Text(person.name) },
                        leadingContent = {
                            if (isChecked) {
                                Icon(Icons.Default.Check, contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary)
                            } else {
                                Icon(Icons.Default.People, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.clip(CircleShape))
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            val next = if (isChecked) selectedIds - person.id else selectedIds + person.id
                            onSelectionChanged(next)
                        }) {
                            Text(if (isChecked) "Remove" else "Add")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = {
                    onSelectionChanged(emptySet())
                }) {
                    Text("Clear filter")
                }
                TextButton(onClick = { sheetOpen = false }) {
                    Text("Done")
                }
            }
        }
    }
}
```

Note: The chip label uses singular/plural English ("All people" / "1 person" / "N people"). When `persons.isEmpty()` the sheet shows a hint pointing the user to Settings.

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/atlasfpt/ui/component/PersonsFilterChip.kt
git commit -m "feat(#24): add PersonsFilterChip with multi-select bottom sheet"
```

---

### Task 5: `OverviewViewModel` — selectedPersonIds + persons list

**Files:**
- Modify: `app/src/main/java/com/atlasfpt/ui/feature/overview/OverviewViewModel.kt`
- Modify: `app/src/test/java/com/atlasfpt/ui/feature/overview/OverviewViewModelTest.kt`

- [ ] **Step 1: Extend the test**

Append to `OverviewViewModelTest.kt`:

```kotlin
@Test
fun `onPersonsSelected updates state and re-invokes use case with filter`() = runTest {
    val month = YearMonth.now()
    io.mockk.every { getOverview(month, emptySet()) } returns flowOf(OverviewUiState(isLoading = false))
    io.mockk.every { getOverview(month, setOf(1L)) } returns flowOf(OverviewUiState(isLoading = false, totalExpense = 99.0))
    io.mockk.every { settings.settings } returns MutableStateFlow(AppSettings())
    io.mockk.every { personRepository.observeAll() } returns flowOf(emptyList())

    val vm = OverviewViewModel(getOverview, settings, personRepository)

    vm.uiState.test {
        assertEquals(emptySet<Long>(), awaitItem().selectedPersonIds)
        vm.onPersonsSelected(setOf(1L))
        // Drain emissions until total becomes 99.0
        var item = awaitItem()
        while (item.overviewUiState.totalExpense != 99.0) item = awaitItem()
        assertEquals(setOf(1L), item.selectedPersonIds)
        cancelAndIgnoreRemainingEvents()
    }
}
```

Add the test imports:
```kotlin
import com.atlasfpt.data.repository.PersonRepository
import com.atlasfpt.domain.model.Person
```

And declare in the class body:
```kotlin
private val personRepository: PersonRepository = mockk()
```

Update the existing `onSideSelected flips selectedSide` test setup to also stub `personRepository.observeAll()` and pass it into the VM constructor.

- [ ] **Step 2: Run test to verify it FAILS**

Run: `./gradlew :app:testDebugUnitTest --tests "com.atlasfpt.ui.feature.overview.OverviewViewModelTest"`
Expected: FAIL — `selectedPersonIds`, `onPersonsSelected`, constructor signature mismatch.

- [ ] **Step 3: Implement**

Replace `OverviewViewModel.kt` with:

```kotlin
package com.atlasfpt.ui.feature.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlasfpt.data.repository.PersonRepository
import com.atlasfpt.data.settings.SettingsRepository
import com.atlasfpt.domain.model.Person
import com.atlasfpt.domain.model.TransactionType
import com.atlasfpt.domain.usecase.GetOverviewUseCase
import com.atlasfpt.domain.usecase.OverviewUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import javax.inject.Inject

data class OverviewScreenState(
    val overviewUiState: OverviewUiState = OverviewUiState(),
    val selectedMonth: YearMonth = YearMonth.now(),
    val selectedSide: TransactionType = TransactionType.EXPENSE,
    val selectedPersonIds: Set<Long> = emptySet(),
    val availablePersons: List<Person> = emptyList(),
    val currencySymbol: String = "€"
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val getOverview: GetOverviewUseCase,
    private val settingsRepository: SettingsRepository,
    private val personRepository: PersonRepository
) : ViewModel() {

    val selectedMonth = MutableStateFlow(YearMonth.now())
    private val selectedSide = MutableStateFlow(TransactionType.EXPENSE)
    private val selectedPersonIds = MutableStateFlow<Set<Long>>(emptySet())

    private val overviewFlow = combine(
        selectedMonth,
        selectedPersonIds
    ) { month, ids -> month to ids }
        .flatMapLatest { (month, ids) -> getOverview(month, ids) }

    val uiState: StateFlow<OverviewScreenState> = combine(
        overviewFlow,
        selectedMonth,
        selectedSide,
        selectedPersonIds,
        personRepository.observeAll(),
        settingsRepository.settings
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        OverviewScreenState(
            overviewUiState = values[0] as OverviewUiState,
            selectedMonth = values[1] as YearMonth,
            selectedSide = values[2] as TransactionType,
            selectedPersonIds = values[3] as Set<Long>,
            availablePersons = values[4] as List<Person>,
            currencySymbol = (values[5] as com.atlasfpt.data.settings.AppSettings).currencySymbol
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OverviewScreenState()
    )

    fun previousMonth() { selectedMonth.value = selectedMonth.value.minusMonths(1) }
    fun nextMonth() { selectedMonth.value = selectedMonth.value.plusMonths(1) }
    fun onSideSelected(side: TransactionType) { selectedSide.value = side }
    fun onPersonsSelected(ids: Set<Long>) { selectedPersonIds.value = ids }
}
```

(The 6-arg `combine` lambda uses the vararg-array overload; `values[0]..values[5]` are positionally typed.)

- [ ] **Step 4: Run test to verify PASSES**

Run: `./gradlew :app:testDebugUnitTest --tests "com.atlasfpt.ui.feature.overview.OverviewViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Full unit-test target**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/atlasfpt/ui/feature/overview/OverviewViewModel.kt \
        app/src/test/java/com/atlasfpt/ui/feature/overview/OverviewViewModelTest.kt
git commit -m "feat(#24): lift selectedPersonIds into OverviewViewModel"
```

---

### Task 6: `TimelineViewModel` — selectedPersonIds + persons list

**Files:**
- Modify: `app/src/main/java/com/atlasfpt/ui/feature/timeline/TimelineViewModel.kt`
- Modify: `app/src/test/java/com/atlasfpt/ui/feature/timeline/TimelineViewModelTest.kt`

- [ ] **Step 1: Update the existing test setup + add a new test**

Update the existing test's MockK setup so the VM's added `personRepository` dep + filter-aware `getTimeline(filter)` call resolves:

```kotlin
// add field
private val personRepository: PersonRepository = mockk()

// in `onRangeModeSelected flips rangeMode`:
io.mockk.every { personRepository.observeAll() } returns flowOf(emptyList())
io.mockk.every { getTimeline(emptySet()) } returns flowOf(TimelineData())
// keep settings.settings stub
val vm = TimelineViewModel(getTimeline, deleteTransaction, settings, personRepository)
```

(That replaces the old `every { getTimeline() } returns ...`.)

Append a new test:

```kotlin
@Test
fun `onPersonsSelected re-invokes use case with new filter`() = runTest {
    io.mockk.every { personRepository.observeAll() } returns flowOf(emptyList())
    io.mockk.every { getTimeline(emptySet()) } returns flowOf(TimelineData())
    io.mockk.every { getTimeline(setOf(1L)) } returns flowOf(TimelineData(headerTotal = 7.0))
    io.mockk.every { settings.settings } returns MutableStateFlow(AppSettings())

    val vm = TimelineViewModel(getTimeline, deleteTransaction, settings, personRepository)

    vm.uiState.test {
        assertEquals(emptySet<Long>(), awaitItem().selectedPersonIds)
        vm.onPersonsSelected(setOf(1L))
        var item = awaitItem()
        while (item.timelineData.headerTotal != 7.0) item = awaitItem()
        assertEquals(setOf(1L), item.selectedPersonIds)
        cancelAndIgnoreRemainingEvents()
    }
}
```

- [ ] **Step 2: Verify FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "com.atlasfpt.ui.feature.timeline.TimelineViewModelTest"`

- [ ] **Step 3: Implement**

Replace `TimelineViewModel.kt` with:

```kotlin
package com.atlasfpt.ui.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlasfpt.data.repository.PersonRepository
import com.atlasfpt.data.settings.AppSettings
import com.atlasfpt.data.settings.SettingsRepository
import com.atlasfpt.domain.model.Person
import com.atlasfpt.domain.model.Transaction
import com.atlasfpt.domain.usecase.DeleteTransactionUseCase
import com.atlasfpt.domain.usecase.GetTimelineUseCase
import com.atlasfpt.domain.usecase.TimelineData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class WalletFilter { All }
enum class RangeMode { ByMonths, ByWeeks }

data class TimelineUiState(
    val timelineData: TimelineData = TimelineData(),
    val settings: AppSettings = AppSettings(),
    val pendingDelete: Transaction? = null,
    val walletFilter: WalletFilter = WalletFilter.All,
    val rangeMode: RangeMode = RangeMode.ByMonths,
    val selectedPersonIds: Set<Long> = emptySet(),
    val availablePersons: List<Person> = emptyList(),
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val getTimeline: GetTimelineUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val settingsRepository: SettingsRepository,
    private val personRepository: PersonRepository
) : ViewModel() {

    private val pendingDelete = MutableStateFlow<Transaction?>(null)
    private val walletFilter = MutableStateFlow(WalletFilter.All)
    private val rangeMode = MutableStateFlow(RangeMode.ByMonths)
    private val selectedPersonIds = MutableStateFlow<Set<Long>>(emptySet())

    private val timelineFlow = selectedPersonIds
        .flatMapLatest { ids -> getTimeline(ids) }

    val uiState: StateFlow<TimelineUiState> = combine(
        timelineFlow,
        settingsRepository.settings,
        pendingDelete,
        walletFilter,
        rangeMode,
        selectedPersonIds,
        personRepository.observeAll()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        TimelineUiState(
            timelineData = values[0] as TimelineData,
            settings = values[1] as AppSettings,
            pendingDelete = values[2] as Transaction?,
            walletFilter = values[3] as WalletFilter,
            rangeMode = values[4] as RangeMode,
            selectedPersonIds = values[5] as Set<Long>,
            availablePersons = values[6] as List<Person>,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TimelineUiState()
    )

    fun requestDelete(transaction: Transaction) {
        pendingDelete.value = transaction
        viewModelScope.launch {
            delay(5_000)
            val pending = pendingDelete.value
            if (pending?.id == transaction.id) {
                deleteTransaction(transaction)
                pendingDelete.value = null
            }
        }
    }

    fun undoDelete() { pendingDelete.value = null }
    fun onWalletFilterSelected(filter: WalletFilter) { walletFilter.value = filter }
    fun onRangeModeSelected(mode: RangeMode) { rangeMode.value = mode }
    fun onPersonsSelected(ids: Set<Long>) { selectedPersonIds.value = ids }
}
```

- [ ] **Step 4: Tests pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/atlasfpt/ui/feature/timeline/TimelineViewModel.kt \
        app/src/test/java/com/atlasfpt/ui/feature/timeline/TimelineViewModelTest.kt
git commit -m "feat(#24): lift selectedPersonIds into TimelineViewModel"
```

---

### Task 7: Wire the chip into `TimelineScreen`

**Files:**
- Modify: `app/src/main/java/com/atlasfpt/ui/feature/timeline/TimelineScreen.kt`

- [ ] **Step 1: Add `PersonsFilterChip` to the existing `FilterChipsRow`**

In `TimelineScreen.kt`, find `FilterChipsRow` and extend its parameters + body:

```kotlin
@Composable
private fun FilterChipsRow(
    rangeMode: RangeMode,
    onRangeModeSelected: (RangeMode) -> Unit,
    persons: List<com.atlasfpt.domain.model.Person>,
    selectedPersonIds: Set<Long>,
    onPersonsSelected: (Set<Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    var rangeMenuOpen by remember { mutableStateOf(false) }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = { /* wallet menu — future */ },
            label = { Text("All Wallets") },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
        )
        Box {
            AssistChip(
                onClick = { rangeMenuOpen = true },
                label = { Text(if (rangeMode == RangeMode.ByMonths) "By months" else "By weeks") },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
            )
            DropdownMenu(expanded = rangeMenuOpen, onDismissRequest = { rangeMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("By months") },
                    onClick = { onRangeModeSelected(RangeMode.ByMonths); rangeMenuOpen = false }
                )
                DropdownMenuItem(
                    text = { Text("By weeks") },
                    onClick = { onRangeModeSelected(RangeMode.ByWeeks); rangeMenuOpen = false }
                )
            }
        }
        com.atlasfpt.ui.component.PersonsFilterChip(
            persons = persons,
            selectedIds = selectedPersonIds,
            onSelectionChanged = onPersonsSelected
        )
    }
}
```

Update the caller in the `LazyColumn` body to pass the new params:

```kotlin
item {
    FilterChipsRow(
        rangeMode = uiState.rangeMode,
        onRangeModeSelected = viewModel::onRangeModeSelected,
        persons = uiState.availablePersons,
        selectedPersonIds = uiState.selectedPersonIds,
        onPersonsSelected = viewModel::onPersonsSelected,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    )
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/atlasfpt/ui/feature/timeline/TimelineScreen.kt
git commit -m "feat(#24): wire PersonsFilterChip into TimelineScreen FilterChipsRow"
```

---

### Task 8: Wire the chip into `OverviewScreen`

**Files:**
- Modify: `app/src/main/java/com/atlasfpt/ui/feature/overview/OverviewScreen.kt`

- [ ] **Step 1: Add a single-chip row above the SegmentedTotalsRow**

Inside the existing `Column { ... }` in `OverviewScreen`, between the `MonthSelector` and the `SegmentedTotalsRow`, insert:

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.Start
) {
    com.atlasfpt.ui.component.PersonsFilterChip(
        persons = screenState.availablePersons,
        selectedIds = screenState.selectedPersonIds,
        onSelectionChanged = viewModel::onPersonsSelected
    )
}
```

(`Row` already imported by the file. If `Arrangement` isn't imported, add it.)

- [ ] **Step 2: Build + test**

Run: `./gradlew :app:assembleDebug && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/atlasfpt/ui/feature/overview/OverviewScreen.kt
git commit -m "feat(#24): add PersonsFilterChip row to OverviewScreen"
```

---

### Task 9: Install, smoke, push, PR

- [ ] **Step 1: Install on emulator**

```bash
/Users/pedrobatista/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
/Users/pedrobatista/Library/Android/sdk/platform-tools/adb shell am start -n com.atlasfpt/.MainActivity
```

- [ ] **Step 2: Smoke**

Confirm:
- Timeline shows a third filter chip "All people"; tap → bottom sheet opens; selecting a person updates the chip label and filters bars + day groups.
- Overview chip row above totals works the same way; totals + donut + category list update.
- "Clear filter" resets to "All people" and shows everything.
- Empty-persons project (if no persons exist) shows the "No persons yet" hint in the sheet.

- [ ] **Step 3: Push + PR**

```bash
git push -u origin feat/24-persons-filter
gh pr create --title "feat(#24): filter Timeline + Overview by person" --body "$(cat <<'EOF'
## Summary
- Adds a `PersonsFilterChip` (chip + multi-select bottom sheet) shared between Timeline and Overview.
- Timeline: third chip alongside Wallets / Range.
- Overview: chip row above the segmented totals.
- Use cases (`GetTimelineUseCase`, `GetOverviewUseCase`) take an optional `personFilterIds: Set<Long>` (default empty = no filter). When non-empty: OR semantics, transactions with no persons attached are hidden.
- Overview's filtered path computes totals + breakdowns in-memory from `observeByDateRange`, preserving the DAO-aggregate fast path when no filter is active.

Closes #24.

## Test plan
- [x] `./gradlew :app:testDebugUnitTest` — green; new `GetTimelineUseCaseTest`, expanded `OverviewViewModelTest` + `TimelineViewModelTest`
- [x] `./gradlew :app:assembleDebug` — green
- [ ] Manual: pick a person on each screen, confirm Timeline bars + day groups + Overview totals/donut all update; "Clear filter" resets

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Out of Scope

- DAO-level person filtering (would require JOIN; current in-memory path is fine at typical scale).
- Filter persistence across navigation (each screen tracks its own filter for v1).
- AND semantics (OR only).
- Showing un-attributed transactions when a filter is active.
