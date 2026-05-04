# Delete Transaction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a delete action to the edit transaction screen, gated behind a confirmation dialog.

**Architecture:** The `DeleteTransactionUseCase` already exists. The ViewModel gains a cached reference to the loaded transaction plus three new functions. The screen adds a trash icon (edit mode only), a confirmation `AlertDialog`, and a `LaunchedEffect` that pops the back stack on deletion — mirroring the existing save pattern.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, Hilt, JUnit 4, MockK, kotlinx-coroutines-test, Turbine

---

## File Map

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | Add test library versions and entries |
| `app/build.gradle.kts` | Add `testImplementation` dependencies |
| `app/src/test/java/com/spendtrack/util/MainDispatcherRule.kt` | **Create** — test utility for setting Main dispatcher |
| `app/src/test/java/com/spendtrack/ui/feature/addtransaction/AddTransactionViewModelDeleteTest.kt` | **Create** — unit tests for delete behaviour |
| `app/src/main/java/com/spendtrack/ui/feature/addtransaction/AddTransactionViewModel.kt` | Add state fields, inject use case, add delete functions |
| `app/src/main/java/com/spendtrack/ui/feature/addtransaction/AddTransactionScreen.kt` | Add trash icon, AlertDialog, LaunchedEffect |

---

### Task 1: Add test dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add versions and library entries to `gradle/libs.versions.toml`**

In the `[versions]` block add:
```toml
junit = "4.13.2"
mockk = "1.13.12"
coroutines-test = "1.9.0"
turbine = "1.1.0"
```

In the `[libraries]` block add:
```toml
junit = { group = "junit", name = "junit", version.ref = "junit" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines-test" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
```

- [ ] **Step 2: Add `testImplementation` entries to `app/build.gradle.kts`**

Add inside the `dependencies` block:
```kotlin
testImplementation(libs.junit)
testImplementation(libs.mockk)
testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.turbine)
```

- [ ] **Step 3: Sync Gradle**

Run: `./gradlew :app:dependencies --configuration testDebugRuntimeClasspath 2>&1 | grep -E "junit|mockk|turbine|coroutines-test"`

Expected: each library appears in the output.

---

### Task 2: Create MainDispatcherRule

**Files:**
- Create: `app/src/test/java/com/spendtrack/util/MainDispatcherRule.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.spendtrack.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    val testDispatcher = UnconfinedTestDispatcher()

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
```

---

### Task 3: Write failing ViewModel tests

**Files:**
- Create: `app/src/test/java/com/spendtrack/ui/feature/addtransaction/AddTransactionViewModelDeleteTest.kt`

- [ ] **Step 1: Create the test file**

```kotlin
package com.spendtrack.ui.feature.addtransaction

import app.cash.turbine.test
import com.spendtrack.data.repository.CategoryRepository
import com.spendtrack.data.repository.TransactionRepository
import com.spendtrack.data.settings.AppSettings
import com.spendtrack.data.settings.SettingsRepository
import com.spendtrack.domain.model.Category
import com.spendtrack.domain.model.CategoryType
import com.spendtrack.domain.model.Transaction
import com.spendtrack.domain.model.TransactionType
import com.spendtrack.domain.usecase.DeleteTransactionUseCase
import com.spendtrack.domain.usecase.SaveTransactionUseCase
import com.spendtrack.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class AddTransactionViewModelDeleteTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val saveTransaction: SaveTransactionUseCase = mockk(relaxed = true)
    private val categoryRepository: CategoryRepository = mockk()
    private val transactionRepository: TransactionRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val deleteTransaction: DeleteTransactionUseCase = mockk(relaxed = true)

    private lateinit var viewModel: AddTransactionViewModel

    private val fakeCategory = Category(
        id = 1L, name = "Food", iconRes = "", color = 0, type = CategoryType.EXPENSE
    )
    private val fakeTransaction = Transaction(
        id = 42L,
        amount = 10.0,
        type = TransactionType.EXPENSE,
        category = fakeCategory,
        date = LocalDate.of(2026, 1, 1),
        note = null,
        photoUri = null,
        labels = emptyList(),
        recurringRuleId = null,
        isScheduled = false
    )

    @Before
    fun setup() {
        every { categoryRepository.observeAll() } returns flowOf(emptyList())
        every { settingsRepository.settings } returns flowOf(AppSettings())
        coEvery { transactionRepository.getById(42L) } returns fakeTransaction
        viewModel = AddTransactionViewModel(
            saveTransaction,
            categoryRepository,
            transactionRepository,
            settingsRepository,
            deleteTransaction
        )
    }

    @Test
    fun `onDeleteRequested sets showDeleteConfirmation true`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial state
            viewModel.onDeleteRequested()
            assertTrue(awaitItem().showDeleteConfirmation)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onDeleteDismissed sets showDeleteConfirmation false`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onDeleteRequested()
            awaitItem() // showDeleteConfirmation = true
            viewModel.onDeleteDismissed()
            assertFalse(awaitItem().showDeleteConfirmation)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete calls use case and sets isDeleted`() = runTest {
        viewModel.loadTransaction(42L)
        viewModel.uiState.test {
            awaitItem()
            viewModel.delete()
            assertTrue(awaitItem().isDeleted)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { deleteTransaction(fakeTransaction) }
    }
}
```

- [ ] **Step 2: Run tests and confirm they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.spendtrack.ui.feature.addtransaction.AddTransactionViewModelDeleteTest" 2>&1 | tail -20`

Expected: compilation error — `AddTransactionViewModel` constructor does not accept `DeleteTransactionUseCase`, and `showDeleteConfirmation`/`isDeleted` fields do not exist.

---

### Task 4: Implement ViewModel changes

**Files:**
- Modify: `app/src/main/java/com/spendtrack/ui/feature/addtransaction/AddTransactionViewModel.kt`

- [ ] **Step 1: Add two fields to `AddTransactionUiState`**

Add after `val showCategoryPicker: Boolean = false`:
```kotlin
val showDeleteConfirmation: Boolean = false,
val isDeleted: Boolean = false,
```

- [ ] **Step 2: Update `AddTransactionViewModel` — inject use case, cache loaded transaction, add functions**

Replace the class declaration and constructor:
```kotlin
@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val saveTransaction: SaveTransactionUseCase,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val deleteTransaction: DeleteTransactionUseCase
) : ViewModel() {
```

Add a private field after `private var editingTransactionId: Long = 0L`:
```kotlin
private var loadedTransaction: Transaction? = null
```

In `loadTransaction()`, add one line after `val tx = transactionRepository.getById(id) ?: return@launch`:
```kotlin
loadedTransaction = tx
```

Add three new functions after `save()`:
```kotlin
fun onDeleteRequested() { _form.update { it.copy(showDeleteConfirmation = true) } }

fun onDeleteDismissed() { _form.update { it.copy(showDeleteConfirmation = false) } }

fun delete() {
    val tx = loadedTransaction ?: return
    viewModelScope.launch {
        deleteTransaction(tx)
        _form.update { it.copy(isDeleted = true) }
    }
}
```

- [ ] **Step 3: Run tests and confirm they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.spendtrack.ui.feature.addtransaction.AddTransactionViewModelDeleteTest" 2>&1 | tail -20`

Expected:
```
BUILD SUCCESSFUL
3 tests completed, 0 failed
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/spendtrack/ui/feature/addtransaction/AddTransactionViewModel.kt \
        app/src/test/java/com/spendtrack/ui/feature/addtransaction/AddTransactionViewModelDeleteTest.kt \
        app/src/test/java/com/spendtrack/util/MainDispatcherRule.kt \
        gradle/libs.versions.toml \
        app/build.gradle.kts
git commit -m "feat: add delete functions to AddTransactionViewModel with tests"
```

---

### Task 5: Add delete UI to AddTransactionScreen

**Files:**
- Modify: `app/src/main/java/com/spendtrack/ui/feature/addtransaction/AddTransactionScreen.kt`

- [ ] **Step 1: Add trash icon import**

Add to the import block:
```kotlin
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
```

- [ ] **Step 2: Add `LaunchedEffect` for `isDeleted`**

Add after the existing `LaunchedEffect(uiState.isSaved)` block:
```kotlin
LaunchedEffect(uiState.isDeleted) {
    if (uiState.isDeleted) navController.popBackStack()
}
```

- [ ] **Step 3: Add trash icon to `TopAppBar`**

Add an `actions` parameter to the existing `TopAppBar` call:
```kotlin
TopAppBar(
    title = {
        Text(if (transactionId != null && transactionId != 0L) "Edit Transaction" else "Add Transaction")
    },
    navigationIcon = {
        IconButton(onClick = { navController.popBackStack() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
    },
    actions = {
        if (transactionId != null && transactionId != 0L) {
            IconButton(onClick = viewModel::onDeleteRequested) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete transaction")
            }
        }
    }
)
```

- [ ] **Step 4: Add confirmation `AlertDialog`**

Add at the bottom of `AddTransactionScreen`, after the `if (showDatePicker)` block:
```kotlin
if (uiState.showDeleteConfirmation) {
    AlertDialog(
        onDismissRequest = viewModel::onDeleteDismissed,
        title = { Text("Delete transaction?") },
        text = { Text("This cannot be undone.") },
        confirmButton = {
            TextButton(onClick = viewModel::delete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::onDeleteDismissed) {
                Text("Cancel")
            }
        }
    )
}
```

- [ ] **Step 5: Build**

Run: `./gradlew assembleDebug 2>&1 | tail -10`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Install and verify manually**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.spendtrack/.MainActivity
```

Open any existing transaction → confirm trash icon appears in top bar → tap it → confirm dialog appears → tap Delete → confirm transaction is gone from the timeline.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/spendtrack/ui/feature/addtransaction/AddTransactionScreen.kt
git commit -m "feat: add delete button and confirmation dialog to edit transaction screen"
```
