# Overview Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Overview's TabRow with a segmented totals card + animated category donut (icons orbiting outside the ring) + category list, all driven by `selectedSide` state.

**Architecture:** Add `count` to `CategoryTotal` so the breakdown row can show `"N transactions"`. Lift `selectedSide` into `OverviewScreenState` and expose `onSideSelected()`. Extract donut into a reusable `CategoryDonut` composable. Replace `OverviewScreen` body with header → segmented card → donut → list.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose, Material 3, Hilt, Room, JUnit4 + MockK + Turbine.

**Spec:** `docs/superpowers/specs/2026-05-14-overview-redesign-design.md`

**Target visual:** `docs/design/overview-target.jpeg`

---

### Task 1: Feature branch

**Files:** none

- [ ] **Step 1: Create the feature branch off main**

```bash
git checkout main && git pull --ff-only && git checkout -b feat/6-overview-redesign
```

Expected: branch `feat/6-overview-redesign` checked out from latest `origin/main`.

---

### Task 2: Add `count` to `CategoryTotal` (DAO + repo)

**Files:**
- Modify: `app/src/main/java/com/atlasfpt/data/db/dao/TransactionDao.kt`
- Modify: `app/src/main/java/com/atlasfpt/data/repository/TransactionRepository.kt` (signature unchanged — passthrough)

- [ ] **Step 1: Extend `CategoryTotal` and update the query**

Replace the existing query + data class at the bottom of `TransactionDao.kt`:

```kotlin
@Query("""
    SELECT categoryId, SUM(amount) as total, COUNT(*) as count
    FROM transactions
    WHERE isScheduled = 0 AND type = :type AND date BETWEEN :from AND :to
    GROUP BY categoryId
""")
fun getCategoryTotals(
    type: TransactionType,
    from: LocalDate,
    to: LocalDate
): Flow<List<CategoryTotal>>
```

```kotlin
data class CategoryTotal(val categoryId: Long, val total: Double, val count: Int)
```

- [ ] **Step 2: Build to confirm Room codegen accepts the column add**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/atlasfpt/data/db/dao/TransactionDao.kt
git commit -m "feat(#6): add transaction count to CategoryTotal DAO query"
```

---

### Task 3: Plumb `transactionCount` into `CategoryBreakdown`

**Files:**
- Modify: `app/src/main/java/com/atlasfpt/domain/usecase/GetOverviewUseCase.kt`

- [ ] **Step 1: Extend `CategoryBreakdown` and populate it**

Replace the `CategoryBreakdown` data class and the `breakdown(...)` helper:

```kotlin
data class CategoryBreakdown(
    val category: Category,
    val amount: Double,
    val percentage: Float,
    val transactionCount: Int
)
```

In `invoke(...)`, update the helper:

```kotlin
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
```

- [ ] **Step 2: Build to confirm compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/atlasfpt/domain/usecase/GetOverviewUseCase.kt
git commit -m "feat(#6): expose transactionCount on CategoryBreakdown"
```

---

### Task 4: Lift `selectedSide` into ViewModel (TDD)

**Files:**
- Modify: `app/src/main/java/com/atlasfpt/ui/feature/overview/OverviewViewModel.kt`
- Create: `app/src/test/java/com/atlasfpt/ui/feature/overview/OverviewViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.atlasfpt.ui.feature.overview

import app.cash.turbine.test
import com.atlasfpt.data.settings.AppSettings
import com.atlasfpt.data.settings.SettingsRepository
import com.atlasfpt.domain.model.TransactionType
import com.atlasfpt.domain.usecase.GetOverviewUseCase
import com.atlasfpt.domain.usecase.OverviewUiState
import com.atlasfpt.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

class OverviewViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val getOverview: GetOverviewUseCase = mockk()
    private val settings: SettingsRepository = mockk()

    @Test
    fun `onSideSelected flips selectedSide`() = runTest {
        every { getOverview(any<YearMonth>()) } returns flowOf(OverviewUiState(isLoading = false))
        every { settings.settings } returns MutableStateFlow(AppSettings())

        val vm = OverviewViewModel(getOverview, settings)

        vm.uiState.test {
            assertEquals(TransactionType.EXPENSE, awaitItem().selectedSide)
            vm.onSideSelected(TransactionType.INCOME)
            assertEquals(TransactionType.INCOME, awaitItem().selectedSide)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.atlasfpt.ui.feature.overview.OverviewViewModelTest"`
Expected: FAIL — `selectedSide` does not exist on `OverviewScreenState`, `onSideSelected` not defined.

- [ ] **Step 3: Implement**

Replace `OverviewViewModel.kt` body:

```kotlin
package com.atlasfpt.ui.feature.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlasfpt.data.settings.SettingsRepository
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
    val currencySymbol: String = "€"
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val getOverview: GetOverviewUseCase,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val selectedMonth = MutableStateFlow(YearMonth.now())
    private val selectedSide = MutableStateFlow(TransactionType.EXPENSE)

    val uiState: StateFlow<OverviewScreenState> = combine(
        selectedMonth.flatMapLatest { month -> getOverview(month) },
        selectedMonth,
        selectedSide,
        settingsRepository.settings
    ) { overview, month, side, settings ->
        OverviewScreenState(
            overviewUiState = overview,
            selectedMonth = month,
            selectedSide = side,
            currencySymbol = settings.currencySymbol
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OverviewScreenState()
    )

    fun previousMonth() { selectedMonth.value = selectedMonth.value.minusMonths(1) }
    fun nextMonth() { selectedMonth.value = selectedMonth.value.plusMonths(1) }
    fun onSideSelected(side: TransactionType) { selectedSide.value = side }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.atlasfpt.ui.feature.overview.OverviewViewModelTest"`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/atlasfpt/ui/feature/overview/OverviewViewModel.kt \
        app/src/test/java/com/atlasfpt/ui/feature/overview/OverviewViewModelTest.kt
git commit -m "feat(#6): lift selectedSide into OverviewViewModel"
```

---

### Task 5: Build `CategoryDonut` reusable component

**Files:**
- Create: `app/src/main/java/com/atlasfpt/ui/component/CategoryDonut.kt`

- [ ] **Step 1: Write the component**

```kotlin
package com.atlasfpt.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.atlasfpt.domain.usecase.CategoryBreakdown
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val LABEL_VISIBILITY_THRESHOLD = 0.02f
private const val START_ANGLE_DEGREES = -90f

@Composable
fun CategoryDonut(
    slices: List<CategoryBreakdown>,
    modifier: Modifier = Modifier
) {
    if (slices.isEmpty()) return
    val total = slices.sumOf { it.amount }
    if (total <= 0.0) return

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.18f
            val outerRadius = (size.minDimension - stroke) / 2f
            val arcSize = Size(outerRadius * 2, outerRadius * 2)
            val topLeft = Offset(center.x - outerRadius, center.y - outerRadius)
            var startAngle = START_ANGLE_DEGREES
            slices.forEach { slice ->
                val fraction = (slice.amount / total).toFloat()
                val sweep = fraction * 360f
                drawArc(
                    color = Color(slice.category.color),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke)
                )
                startAngle += sweep
            }
        }

        val density = LocalDensity.current
        var cursorAngle = START_ANGLE_DEGREES
        slices.forEach { slice ->
            val fraction = (slice.amount / total).toFloat()
            val sweep = fraction * 360f
            val midAngleRad = Math.toRadians((cursorAngle + sweep / 2f).toDouble())
            cursorAngle += sweep

            if (fraction < LABEL_VISIBILITY_THRESHOLD) return@forEach

            val pctLabel = "%.1f%%".format(fraction * 100).removeSuffix(".0%") + (if (fraction * 100 % 1f == 0f) "%" else "")

            Box(
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val parentW = constraints.maxWidth
                        val parentH = constraints.maxHeight
                        val orbit = (minOf(parentW, parentH) * 0.5f) - with(density) { 12.dp.toPx() }
                        val cx = parentW / 2f + (orbit * cos(midAngleRad)).toFloat() - placeable.width / 2f
                        val cy = parentH / 2f + (orbit * sin(midAngleRad)).toFloat() - placeable.height / 2f
                        layout(placeable.width, placeable.height) {
                            placeable.place(IntOffset(cx.roundToInt(), cy.roundToInt()))
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(slice.category.color)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = slice.category.name.firstOrNull()?.uppercase() ?: "·",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val parentW = constraints.maxWidth
                        val parentH = constraints.maxHeight
                        val labelOrbit = (minOf(parentW, parentH) * 0.5f) + with(density) { 4.dp.toPx() }
                        val cx = parentW / 2f + (labelOrbit * cos(midAngleRad)).toFloat() - placeable.width / 2f
                        val cy = parentH / 2f + (labelOrbit * sin(midAngleRad)).toFloat() - placeable.height / 2f
                        layout(placeable.width, placeable.height) {
                            placeable.place(IntOffset(cx.roundToInt(), cy.roundToInt()))
                        }
                    }
            ) {
                Text(
                    text = pctLabel,
                    color = Color(slice.category.color),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
```

Note: Renders a colored circle with the category's first letter as a placeholder for the orbiting icon — the project stores `iconRes` as a Material icon name string, not a drawable id, so a textual mark is the pragmatic first cut. (Wiring real Material icons by name is a polish follow-up if the visual review asks for it.)

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/atlasfpt/ui/component/CategoryDonut.kt
git commit -m "feat(#6): add CategoryDonut composable with orbiting icons + percent labels"
```

---

### Task 6: Rewrite `OverviewScreen`

**Files:**
- Modify (full rewrite of body): `app/src/main/java/com/atlasfpt/ui/feature/overview/OverviewScreen.kt`

- [ ] **Step 1: Replace screen contents**

```kotlin
package com.atlasfpt.ui.feature.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.atlasfpt.domain.model.TransactionType
import com.atlasfpt.domain.usecase.CategoryBreakdown
import com.atlasfpt.ui.component.CategoryDonut
import com.atlasfpt.ui.component.MonthSelector
import com.atlasfpt.ui.theme.ExpenseColor
import com.atlasfpt.ui.theme.IncomeColor
import com.atlasfpt.util.CurrencyFormatter

@Composable
fun OverviewScreen(viewModel: OverviewViewModel = hiltViewModel()) {
    val screenState by viewModel.uiState.collectAsState()
    val state = screenState.overviewUiState
    val isExpense = screenState.selectedSide == TransactionType.EXPENSE
    val slices = if (isExpense) state.expenseBreakdown else state.incomeBreakdown
    val accent = if (isExpense) ExpenseColor else IncomeColor

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Categories",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        )

        MonthSelector(
            yearMonth = screenState.selectedMonth,
            onPrevious = viewModel::previousMonth,
            onNext = viewModel::nextMonth,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )

        SegmentedTotalsRow(
            selectedSide = screenState.selectedSide,
            expenseTotal = state.totalExpense,
            incomeTotal = state.totalIncome,
            currencySymbol = screenState.currencySymbol,
            onSideSelected = viewModel::onSideSelected,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (slices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No transactions this month", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                CategoryDonut(
                    slices = slices,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(24.dp)
                )
            }
            items(slices) { slice ->
                CategorySliceRow(slice = slice, currencySymbol = screenState.currencySymbol, accent = accent)
                HorizontalDivider()
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun SegmentedTotalsRow(
    selectedSide: TransactionType,
    expenseTotal: Double,
    incomeTotal: Double,
    currencySymbol: String,
    onSideSelected: (TransactionType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TotalsCard(
            label = "Expenses",
            amount = expenseTotal,
            color = ExpenseColor,
            selected = selectedSide == TransactionType.EXPENSE,
            currencySymbol = currencySymbol,
            onClick = { onSideSelected(TransactionType.EXPENSE) },
            modifier = Modifier.weight(1f)
        )
        TotalsCard(
            label = "Income",
            amount = incomeTotal,
            color = IncomeColor,
            selected = selectedSide == TransactionType.INCOME,
            currencySymbol = currencySymbol,
            onClick = { onSideSelected(TransactionType.INCOME) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TotalsCard(
    label: String,
    amount: Double,
    color: Color,
    selected: Boolean,
    currencySymbol: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                CurrencyFormatter.formatAbsolute(amount, currencySymbol),
                style = MaterialTheme.typography.titleLarge,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CategorySliceRow(slice: CategoryBreakdown, currencySymbol: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(slice.category.color)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    slice.category.name.firstOrNull()?.uppercase() ?: "·",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column {
                Text(slice.category.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${slice.transactionCount} transactions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            CurrencyFormatter.formatAbsolute(slice.amount, currencySymbol),
            style = MaterialTheme.typography.titleMedium,
            color = accent,
            fontWeight = FontWeight.SemiBold
        )
    }
}
```

- [ ] **Step 2: Build the APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/atlasfpt/ui/feature/overview/OverviewScreen.kt
git commit -m "feat(#6): rewrite OverviewScreen with segmented totals + donut + list"
```

---

### Task 7: Install on emulator, smoke-test, push, open PR

**Files:** none

- [ ] **Step 1: Install on the running emulator**

Run:
```bash
/Users/pedrobatista/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
/Users/pedrobatista/Library/Android/sdk/platform-tools/adb shell am start -n com.atlasfpt/.MainActivity
```
Expected: app launches; navigate to Overview tab.

- [ ] **Step 2: Manual smoke (golden path)**

Confirm:
- "Categories" title + month selector render
- Expenses card shows the expense total in red and is highlighted (selected)
- Income card shows the income total in green
- Tapping Income switches the donut + list to the income categories
- Donut renders with proportional slice colors
- Category list shows "$count transactions" sub-text
- Empty month falls through to "No transactions this month"

Note any visual mismatches against `docs/design/overview-target.jpeg` — file as a follow-up polish issue rather than chasing pixel-perfection in this PR.

- [ ] **Step 3: Push and open the PR**

```bash
git push -u origin feat/6-overview-redesign
gh pr create --title "feat(#6): Overview redesign — segmented totals + category donut" --body "$(cat <<'EOF'
## Summary
- Replaces the TabRow with a header ("Categories"), month selector, and a clickable segmented Expenses/Income totals card.
- Adds reusable `CategoryDonut` composable: animated donut with colored circles + percentage labels orbiting the ring.
- Category list shows transaction count per row and uses the selected side's accent color.
- Lifts `selectedSide` into `OverviewViewModel` (replaces local `var`) so the donut + list stay in sync.

Closes #6.

## Test plan
- [ ] `./gradlew :app:testDebugUnitTest` — green (new `OverviewViewModelTest`)
- [ ] Install on emulator, switch between Expenses/Income, change month
- [ ] Empty month shows "No transactions this month"
- [ ] Visual check against `docs/design/overview-target.jpeg`

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Out-of-Scope (per spec)

- Drill-down from category list rows
- Rendering real Material icons inside the orbiting circles (placeholder = first letter)
- Per-day or per-week aggregation
- Switching between absolute and percent display in the totals cards
