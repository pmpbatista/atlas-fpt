# Timeline Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the flat "CashFlow header + plain list" Timeline with: sign-coloured cash-flow header, two filter chips (Wallets, Range), 6-month income/expense bar chart, "Spending Overview" pill linking to Overview, optional Scheduled rollup row, and day-grouped richer rows (wallet + note as their own lines).

**Architecture:**
- Reshape `GetTimelineUseCase` to emit the new domain shape: `headerTotal`, `bars: List<MonthBar>`, `scheduled: ScheduledRollup?`, `days: List<DayGroup>` (where each `DayGroup` carries a `TransactionRowItem` per row with `walletLabel` + `noteLine` pre-computed).
- `TimelineViewModel` exposes filter/range selection (`walletFilter`, `rangeMode`) — both default to safe values; the chips are visible but mostly inert in v1 (wallets concept not yet modelled).
- `TransactionRow` is rewritten to consume `TransactionRowItem` so the screen never re-derives wallet/note display.
- `CashFlowBarChart` is a new self-contained `Canvas`-based composable.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose, Material 3, Hilt, Room, JUnit4 + MockK + Turbine.

**Spec:** `docs/superpowers/specs/2026-05-14-timeline-redesign-design.md`

**Target visual:** `docs/design/timeline-target.jpeg`

---

### Task 1: Feature branch

**Files:** none

- [ ] **Step 1: Create the feature branch off main**

```bash
git checkout main && git pull --ff-only && git checkout -b feat/7-timeline-redesign
```

Expected: branch `feat/7-timeline-redesign` checked out from latest `origin/main`.

---

### Task 2: Domain reshape — `TimelineData` v2

**Files:**
- Modify: `app/src/main/java/com/atlasfpt/domain/usecase/GetTimelineUseCase.kt`

This task ships the new domain shape while preserving the existing `timelineItems` / `monthlySummaries` fields temporarily (the screen still reads them). The ViewModel cut-over (Task 3) drops the legacy fields.

- [ ] **Step 1: Add new model types and compute them**

Replace the contents of `GetTimelineUseCase.kt` with:

```kotlin
package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.TransactionRepository
import com.atlasfpt.domain.model.Transaction
import com.atlasfpt.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

sealed class TimelineItem {
    data class DateHeader(val date: LocalDate, val dailyTotal: Double) : TimelineItem()
    data class TransactionRow(val transaction: Transaction) : TimelineItem()
}

data class MonthBar(
    val month: YearMonth,
    val income: Double,
    val expense: Double,
    val isCurrent: Boolean
)

data class ScheduledRollup(val count: Int, val net: Double)

data class TransactionRowItem(
    val transaction: Transaction,
    val walletLabel: String,
    val noteLine: String?
)

data class DayGroup(
    val date: LocalDate,
    val net: Double,
    val rows: List<TransactionRowItem>
)

data class TimelineData(
    val headerTotal: Double = 0.0,
    val bars: List<MonthBar> = emptyList(),
    val scheduled: ScheduledRollup? = null,
    val days: List<DayGroup> = emptyList(),
    // Kept temporarily so the legacy screen still compiles until Task 6 lands:
    val totalCashFlow: Double = 0.0,
    val monthlySummaries: List<com.atlasfpt.data.db.dao.MonthlySummary> = emptyList(),
    val scheduledTransactions: List<Transaction> = emptyList(),
    val timelineItems: List<TimelineItem> = emptyList()
)

class GetTimelineUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository
) {
    operator fun invoke(): Flow<TimelineData> = combine(
        transactionRepository.observeAll(),
        transactionRepository.observeScheduled(),
        transactionRepository.observeMonthlySummaries()
    ) { all, scheduled, summaries ->
        val headerTotal = all.sumOf { tx ->
            if (tx.type == TransactionType.EXPENSE) -tx.amount else tx.amount
        }
        TimelineData(
            headerTotal = headerTotal,
            bars = buildBars(all),
            scheduled = buildScheduled(scheduled),
            days = buildDays(all),
            totalCashFlow = headerTotal,
            monthlySummaries = summaries,
            scheduledTransactions = scheduled,
            timelineItems = buildItems(all)
        )
    }

    private fun buildBars(transactions: List<Transaction>): List<MonthBar> {
        val today = YearMonth.now()
        val window = (5 downTo 0).map { today.minusMonths(it.toLong()) }
        val byMonth = transactions.groupBy { YearMonth.from(it.date) }
        return window.map { month ->
            val rows = byMonth[month].orEmpty()
            val income = rows.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val expense = rows.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            MonthBar(month, income, expense, isCurrent = month == today)
        }
    }

    private fun buildScheduled(scheduled: List<Transaction>): ScheduledRollup? {
        if (scheduled.isEmpty()) return null
        val net = scheduled.sumOf { tx ->
            if (tx.type == TransactionType.EXPENSE) -tx.amount else tx.amount
        }
        return ScheduledRollup(count = scheduled.size, net = net)
    }

    private fun buildDays(transactions: List<Transaction>): List<DayGroup> {
        return transactions.groupBy { it.date }
            .entries
            .sortedByDescending { it.key }
            .map { (date, txs) ->
                val net = txs.sumOf { tx ->
                    if (tx.type == TransactionType.EXPENSE) -tx.amount else tx.amount
                }
                DayGroup(
                    date = date,
                    net = net,
                    rows = txs.map { tx ->
                        TransactionRowItem(
                            transaction = tx,
                            walletLabel = "Wallet",
                            noteLine = tx.note?.takeIf { it.isNotBlank() }
                        )
                    }
                )
            }
    }

    private fun buildItems(transactions: List<Transaction>): List<TimelineItem> {
        val grouped = transactions.groupBy { it.date }
        return grouped.entries
            .sortedByDescending { it.key }
            .flatMap { (date, txs) ->
                val dailyTotal = txs.sumOf { tx ->
                    if (tx.type == TransactionType.EXPENSE) -tx.amount else tx.amount
                }
                listOf(TimelineItem.DateHeader(date, dailyTotal)) +
                    txs.map { TimelineItem.TransactionRow(it) }
            }
    }
}
```

- [ ] **Step 2: Build to confirm compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/atlasfpt/domain/usecase/GetTimelineUseCase.kt
git commit -m "feat(#7): reshape TimelineData with bars/scheduled/days"
```

---

### Task 3: ViewModel — filter/range state + cut over to new fields (TDD)

**Files:**
- Modify: `app/src/main/java/com/atlasfpt/ui/feature/timeline/TimelineViewModel.kt`
- Create: `app/src/test/java/com/atlasfpt/ui/feature/timeline/TimelineViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.atlasfpt.ui.feature.timeline

import app.cash.turbine.test
import com.atlasfpt.data.settings.AppSettings
import com.atlasfpt.data.settings.SettingsRepository
import com.atlasfpt.domain.model.Category
import com.atlasfpt.domain.model.CategoryType
import com.atlasfpt.domain.model.Transaction
import com.atlasfpt.domain.model.TransactionType
import com.atlasfpt.domain.usecase.DeleteTransactionUseCase
import com.atlasfpt.domain.usecase.GetTimelineUseCase
import com.atlasfpt.domain.usecase.TimelineData
import com.atlasfpt.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TimelineViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val getTimeline: GetTimelineUseCase = mockk()
    private val deleteTransaction: DeleteTransactionUseCase = mockk(relaxed = true)
    private val settings: SettingsRepository = mockk()

    @Test
    fun `onRangeModeSelected flips rangeMode`() = runTest {
        every { getTimeline() } returns flowOf(TimelineData())
        every { settings.settings } returns MutableStateFlow(AppSettings())

        val vm = TimelineViewModel(getTimeline, deleteTransaction, settings)

        vm.uiState.test {
            assertEquals(RangeMode.ByMonths, awaitItem().rangeMode)
            vm.onRangeModeSelected(RangeMode.ByWeeks)
            assertEquals(RangeMode.ByWeeks, awaitItem().rangeMode)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.atlasfpt.ui.feature.timeline.TimelineViewModelTest"`
Expected: FAIL — `RangeMode`, `onRangeModeSelected`, `rangeMode` unresolved.

- [ ] **Step 3: Implement**

Replace `TimelineViewModel.kt` with:

```kotlin
package com.atlasfpt.ui.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlasfpt.data.settings.AppSettings
import com.atlasfpt.data.settings.SettingsRepository
import com.atlasfpt.domain.model.Transaction
import com.atlasfpt.domain.usecase.DeleteTransactionUseCase
import com.atlasfpt.domain.usecase.GetTimelineUseCase
import com.atlasfpt.domain.usecase.TimelineData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val isLoading: Boolean = true
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val getTimeline: GetTimelineUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val pendingDelete = MutableStateFlow<Transaction?>(null)
    private val walletFilter = MutableStateFlow(WalletFilter.All)
    private val rangeMode = MutableStateFlow(RangeMode.ByMonths)

    val uiState: StateFlow<TimelineUiState> = combine(
        getTimeline(),
        settingsRepository.settings,
        pendingDelete,
        walletFilter,
        rangeMode
    ) { data, settings, pending, wallet, range ->
        TimelineUiState(
            timelineData = data,
            settings = settings,
            pendingDelete = pending,
            walletFilter = wallet,
            rangeMode = range,
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
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.atlasfpt.ui.feature.timeline.TimelineViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Run full unit-test target**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (all tests pass — none of the existing tests touch `TimelineViewModel`, but the screen still reads legacy fields so compile must hold).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/atlasfpt/ui/feature/timeline/TimelineViewModel.kt \
        app/src/test/java/com/atlasfpt/ui/feature/timeline/TimelineViewModelTest.kt
git commit -m "feat(#7): lift wallet/range filter selections into TimelineViewModel"
```

---

### Task 4: New `CashFlowBarChart` composable

**Files:**
- Create: `app/src/main/java/com/atlasfpt/ui/component/CashFlowBarChart.kt`

- [ ] **Step 1: Write the component**

```kotlin
package com.atlasfpt.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atlasfpt.domain.usecase.MonthBar
import com.atlasfpt.ui.theme.ExpenseColor
import com.atlasfpt.ui.theme.IncomeColor
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

private const val BAR_HEIGHT_DP = 160
private const val CURRENT_ALPHA = 1.0f
private const val PRIOR_ALPHA = 0.55f

@Composable
fun CashFlowBarChart(
    bars: List<MonthBar>,
    modifier: Modifier = Modifier
) {
    if (bars.isEmpty()) return
    val maxValue = max(
        bars.maxOf { it.income },
        bars.maxOf { it.expense }
    ).coerceAtLeast(1.0)

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT_DP.dp)
        ) {
            val w = size.width
            val h = size.height
            val gridColor = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.3f)
            val dashed = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))

            // Grid: 0 (bottom), mid, top
            listOf(0f, 0.5f, 1f).forEach { f ->
                val y = h - h * f
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f,
                    pathEffect = if (f == 0f) null else dashed
                )
            }

            val slotWidth = w / bars.size
            val barWidth = (slotWidth * 0.32f).coerceAtMost(20.dp.toPx())
            val gap = slotWidth * 0.06f
            val pairWidth = barWidth * 2 + gap

            bars.forEachIndexed { index, bar ->
                val cx = slotWidth * index + slotWidth / 2f
                val pairLeft = cx - pairWidth / 2f
                val alpha = if (bar.isCurrent) CURRENT_ALPHA else PRIOR_ALPHA

                val incomeH = (bar.income / maxValue).toFloat() * h
                drawBar(
                    color = IncomeColor.copy(alpha = alpha),
                    left = pairLeft,
                    bottom = h,
                    width = barWidth,
                    height = incomeH
                )

                val expenseH = (bar.expense / maxValue).toFloat() * h
                drawBar(
                    color = ExpenseColor.copy(alpha = alpha),
                    left = pairLeft + barWidth + gap,
                    bottom = h,
                    width = barWidth,
                    height = expenseH
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            val labelFormatter = DateTimeFormatter.ofPattern("MMM\nyyyy", Locale("pt", "PT"))
            bars.forEach { bar ->
                Box(
                    modifier = Modifier
                        .weight(1f),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        text = bar.month.atDay(1).format(labelFormatter),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBar(
    color: androidx.compose.ui.graphics.Color,
    left: Float,
    bottom: Float,
    width: Float,
    height: Float
) {
    val cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
    drawRoundRect(
        color = color,
        topLeft = Offset(left, bottom - height),
        size = Size(width, height),
        cornerRadius = cornerRadius
    )
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/atlasfpt/ui/component/CashFlowBarChart.kt
git commit -m "feat(#7): add CashFlowBarChart composable"
```

---

### Task 5: Rewrite `TransactionRow` to accept `TransactionRowItem`

**Files:**
- Modify: `app/src/main/java/com/atlasfpt/ui/component/TransactionRow.kt`

The new signature takes a `TransactionRowItem` instead of bare `Transaction`. Wallet line is always rendered (placeholder "Wallet" until wallets exist); note line is rendered only when non-null. Persons line preserved (was added in #22/#23) — keep as an optional fourth line.

- [ ] **Step 1: Replace contents**

```kotlin
package com.atlasfpt.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atlasfpt.domain.model.TransactionType
import com.atlasfpt.domain.usecase.TransactionRowItem
import com.atlasfpt.ui.theme.ExpenseColor
import com.atlasfpt.ui.theme.IncomeColor
import com.atlasfpt.util.CurrencyFormatter

@Composable
fun TransactionRow(
    item: TransactionRowItem,
    currencySymbol: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tx = item.transaction
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFB00020))
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White
                )
            }
        },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIcon(color = tx.category.color, name = tx.category.name)

            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.category.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconLine(
                    icon = Icons.Default.AccountBalanceWallet,
                    contentDescription = "Wallet",
                    text = item.walletLabel
                )
                item.noteLine?.let { note ->
                    IconLine(
                        icon = Icons.Default.EditNote,
                        contentDescription = "Note",
                        text = note
                    )
                }
                if (tx.persons.isNotEmpty()) {
                    Text(
                        text = "with " + tx.persons.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = CurrencyFormatter.format(tx.amount, currencySymbol, tx.type),
                style = MaterialTheme.typography.titleMedium,
                color = if (tx.type == TransactionType.EXPENSE) ExpenseColor else IncomeColor
            )
        }
    }
}

@Composable
private fun IconLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    text: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CategoryIcon(color: Int, name: String, modifier: Modifier = Modifier) {
    val bg = Color(color)
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bg.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = bg
        )
    }
}
```

Note: This breaks the existing `TimelineScreen` reference (`TransactionRow(transaction=...)` no longer exists). Task 6 (screen rewrite) updates the call site in the same PR. The intermediate state between Task 5 and Task 6 compiles only if you skip running `assembleDebug` between them — that's why we run **`compileDebugKotlin`** here (which will fail on the legacy call site), then immediately move to Task 6. To keep the build clean within a single task: **stage the file edit, then move to Task 6 in the SAME implementer dispatch.**

Actually, **don't split the build red across two tasks** — see the revised step below.

- [ ] **Step 2: Stage the edit but DO NOT build yet**

```bash
git add app/src/main/java/com/atlasfpt/ui/component/TransactionRow.kt
```

Do not run `compileDebugKotlin` here. The build will be green after Task 6.

- [ ] **Step 3: Commit (without rebuilding)**

```bash
git commit -m "feat(#7): rewrite TransactionRow to take TransactionRowItem"
```

The screen still references the old signature; Task 6 fixes that. Build will be verified in Task 6.

---

### Task 6: Rewrite `TimelineScreen`

**Files:**
- Modify (full rewrite of body): `app/src/main/java/com/atlasfpt/ui/feature/timeline/TimelineScreen.kt`

- [ ] **Step 1: Replace contents**

```kotlin
package com.atlasfpt.ui.feature.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.atlasfpt.domain.usecase.DayGroup
import com.atlasfpt.domain.usecase.ScheduledRollup
import com.atlasfpt.ui.component.CashFlowBarChart
import com.atlasfpt.ui.component.TransactionRow
import com.atlasfpt.ui.navigation.Screen
import com.atlasfpt.ui.theme.DateHeaderBackground
import com.atlasfpt.ui.theme.ExpenseColor
import com.atlasfpt.ui.theme.IncomeColor
import com.atlasfpt.util.CurrencyFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun TimelineScreen(
    navController: NavController,
    viewModel: TimelineViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(uiState.pendingDelete) {
        val tx = uiState.pendingDelete ?: return@LaunchedEffect
        val result = snackbarHost.showSnackbar(
            message = "Transaction deleted",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoDelete()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddTransaction.route) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add transaction")
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val data = uiState.timelineData
        val symbol = uiState.settings.currencySymbol

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                CashFlowHeader(headerTotal = data.headerTotal, currencySymbol = symbol)
            }
            item {
                FilterChipsRow(
                    rangeMode = uiState.rangeMode,
                    onRangeModeSelected = viewModel::onRangeModeSelected,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            if (data.bars.isNotEmpty()) {
                item {
                    CashFlowBarChart(
                        bars = data.bars,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            item {
                SpendingOverviewPill(
                    onClick = { navController.navigate(Screen.Overview.route) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            data.scheduled?.let { rollup ->
                item {
                    ScheduledRollupRow(rollup = rollup, currencySymbol = symbol)
                }
            }
            items(data.days, key = { "day_${it.date}" }) { day ->
                DayHeader(day = day, currencySymbol = symbol)
                day.rows.forEach { rowItem ->
                    val isPending = uiState.pendingDelete?.id == rowItem.transaction.id
                    if (!isPending) {
                        TransactionRow(
                            item = rowItem,
                            currencySymbol = symbol,
                            onClick = {
                                navController.navigate(Screen.EditTransaction.createRoute(rowItem.transaction.id))
                            },
                            onDelete = { viewModel.requestDelete(rowItem.transaction) }
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun CashFlowHeader(headerTotal: Double, currencySymbol: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = { /* search — future */ },
                enabled = false,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val color = if (headerTotal >= 0) IncomeColor else ExpenseColor
                Text(
                    text = CurrencyFormatter.formatAbsolute(abs(headerTotal), currencySymbol),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = "Cash Flow",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun FilterChipsRow(
    rangeMode: RangeMode,
    onRangeModeSelected: (RangeMode) -> Unit,
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
    }
}

@Composable
private fun SpendingOverviewPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DonutLarge,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Spending Overview",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScheduledRollupRow(rollup: ScheduledRollup, currencySymbol: String) {
    val color = if (rollup.net >= 0) IncomeColor else ExpenseColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Schedule, contentDescription = "Scheduled",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Scheduled", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${rollup.count} transactions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = CurrencyFormatter.formatAbsolute(abs(rollup.net), currencySymbol),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DayHeader(day: DayGroup, currencySymbol: String) {
    val color = if (day.net >= 0) IncomeColor else ExpenseColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DateHeaderBackground)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatDayLabel(day.date),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = CurrencyFormatter.formatAbsolute(abs(day.net), currencySymbol),
            style = MaterialTheme.typography.labelLarge,
            color = color
        )
    }
}

private fun formatDayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("pt", "PT")))
    }
}
```

- [ ] **Step 2: Build the APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. (This is the moment Task 5's row rewrite + Task 6's screen rewrite reconcile.)

- [ ] **Step 3: Run unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. `TimelineViewModelTest` + the existing `AddTransactionViewModelDeleteTest` + `OverviewViewModelTest` all green.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/atlasfpt/ui/feature/timeline/TimelineScreen.kt
git commit -m "feat(#7): rewrite TimelineScreen with header/chips/bars/pill/days"
```

---

### Task 7: Domain cleanup — drop legacy `TimelineData` fields

**Files:**
- Modify: `app/src/main/java/com/atlasfpt/domain/usecase/GetTimelineUseCase.kt`

Now that no caller reads the legacy fields, prune them.

- [ ] **Step 1: Remove deprecated fields**

In `TimelineData`, drop the four legacy fields and `buildItems(...)` helper. The cleaned data class:

```kotlin
data class TimelineData(
    val headerTotal: Double = 0.0,
    val bars: List<MonthBar> = emptyList(),
    val scheduled: ScheduledRollup? = null,
    val days: List<DayGroup> = emptyList()
)
```

Drop the `monthlySummaries` source from the `combine(...)` call as well (no longer consumed — the use case no longer needs `transactionRepository.observeMonthlySummaries()`):

```kotlin
operator fun invoke(): Flow<TimelineData> = combine(
    transactionRepository.observeAll(),
    transactionRepository.observeScheduled()
) { all, scheduled ->
    val headerTotal = all.sumOf { tx ->
        if (tx.type == TransactionType.EXPENSE) -tx.amount else tx.amount
    }
    TimelineData(
        headerTotal = headerTotal,
        bars = buildBars(all),
        scheduled = buildScheduled(scheduled),
        days = buildDays(all)
    )
}
```

Also drop the `TimelineItem` sealed class and its `DateHeader` / `TransactionRow` variants (unused after Task 6) and the `buildItems(...)` helper.

- [ ] **Step 2: Build + tests**

```bash
./gradlew :app:assembleDebug && ./gradlew :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/atlasfpt/domain/usecase/GetTimelineUseCase.kt
git commit -m "refactor(#7): drop legacy TimelineData fields and TimelineItem"
```

---

### Task 8: Install on emulator, smoke-test, push, open PR

**Files:** none

- [ ] **Step 1: Install on the running emulator**

```bash
/Users/pedrobatista/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
/Users/pedrobatista/Library/Android/sdk/platform-tools/adb shell am start -n com.atlasfpt/.MainActivity
```

- [ ] **Step 2: Manual smoke (golden path)**

Confirm:
- Cash-flow header centred, search icon top-right (disabled, no crash).
- Both filter chips render; tapping the Range chip opens a menu with "By months" / "By weeks".
- Bar chart shows up to six month pairs (income green, expense red), current month brighter.
- "Spending Overview" pill navigates to Overview tab.
- If there are scheduled transactions, the rollup row appears with the right count and net.
- Day groups show "Today"/"Yesterday"/`d de MMMM` labels with the day's net total.
- Tapping a row navigates to edit; swipe-to-delete still triggers the undo snackbar.

Note any visual mismatches against `docs/design/timeline-target.jpeg` and file follow-up issues instead of chasing pixel-perfection.

- [ ] **Step 3: Push and open the PR**

```bash
git push -u origin feat/7-timeline-redesign
gh pr create --title "feat(#7): Timeline redesign — header + bars + day groups" --body "$(cat <<'EOF'
## Summary
- Sign-coloured Cash Flow header (+ disabled search button), filter chips (Wallets stub + Range dropdown), 6-month income/expense `CashFlowBarChart`, "Spending Overview" pill linking to Overview, optional Scheduled rollup row, day-grouped rows with wallet/note as their own lines.
- Lifts `walletFilter` / `rangeMode` into `TimelineViewModel`.
- `TransactionRow` now consumes `TransactionRowItem` (walletLabel + noteLine pre-computed).
- Domain pruned: `TimelineItem` and the legacy `TimelineData` fields are gone.

Closes #7.

## Implementation
Followed the spec at `docs/superpowers/specs/2026-05-14-timeline-redesign-design.md` and the TDD breakdown in `docs/superpowers/plans/2026-05-14-timeline-redesign.md`.

## Known v1 stubs (per spec)
- Wallets concept not modelled — chip is rendered, dropdown does nothing yet (follow-up issue if you want it tracked).
- Range dropdown wires `ByWeeks` to ViewModel state but only `ByMonths` data is computed for v1.
- Search button in the header is rendered disabled.

## Test plan
- [x] `./gradlew :app:testDebugUnitTest` — green (new `TimelineViewModelTest` + existing suite)
- [x] `./gradlew :app:assembleDebug` — green
- [ ] **Manual smoke** on emulator (please verify): bar chart shape, Spending Overview navigation, Range dropdown, scheduled rollup when applicable.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Out-of-Scope (per spec)

- Search (button disabled).
- Real "By weeks" computation (only months data wired for v1).
- True multi-wallet support.
- Drill-down from Scheduled rollup row.
