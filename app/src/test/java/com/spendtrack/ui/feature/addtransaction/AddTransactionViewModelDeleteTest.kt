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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
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
        every { settingsRepository.settings } returns MutableStateFlow(AppSettings())
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
        advanceUntilIdle()
        viewModel.uiState.test {
            awaitItem()
            viewModel.delete()
            assertTrue(awaitItem().isDeleted)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { deleteTransaction(fakeTransaction) }
    }
}
