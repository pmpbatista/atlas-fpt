package com.atlasfpt.ui.feature.addtransaction

import com.atlasfpt.data.repository.CategoryRepository
import com.atlasfpt.data.repository.PersonRepository
import com.atlasfpt.data.repository.TransactionRepository
import com.atlasfpt.data.settings.AppSettings
import com.atlasfpt.data.settings.SettingsRepository
import com.atlasfpt.domain.model.Category
import com.atlasfpt.domain.model.CategoryType
import com.atlasfpt.domain.model.Transaction
import com.atlasfpt.domain.model.TransactionType
import com.atlasfpt.domain.usecase.DeleteTransactionUseCase
import com.atlasfpt.domain.usecase.SaveTransactionUseCase
import com.atlasfpt.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AddTransactionViewModelRecurringRuleIdTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val saveTransaction: SaveTransactionUseCase = mockk()
    private val categoryRepository: CategoryRepository = mockk()
    private val transactionRepository: TransactionRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val deleteTransaction: DeleteTransactionUseCase = mockk(relaxed = true)
    private val personRepository: PersonRepository = mockk()

    private lateinit var viewModel: AddTransactionViewModel

    private val fakeCategory = Category(
        id = 1L, name = "Internet", iconRes = "", color = 0, type = CategoryType.EXPENSE
    )

    @Before
    fun setup() {
        every { categoryRepository.observeAll() } returns flowOf(emptyList())
        every { settingsRepository.settings } returns MutableStateFlow(AppSettings())
        every { personRepository.observeAll() } returns flowOf(emptyList())
        viewModel = AddTransactionViewModel(
            saveTransaction,
            categoryRepository,
            transactionRepository,
            settingsRepository,
            deleteTransaction,
            personRepository
        )
    }

    @Test
    fun `save preserves recurringRuleId when editing a recurring transaction`() = runTest {
        val recurringTx = Transaction(
            id = 42L,
            amount = 48.14,
            type = TransactionType.EXPENSE,
            category = fakeCategory,
            date = LocalDate.of(2026, 1, 15),
            note = null,
            photoUri = null,
            labels = emptyList(),
            persons = emptyList(),
            recurringRuleId = 99L,
            isScheduled = true
        )
        coEvery { transactionRepository.getById(42L) } returns recurringTx
        val txSlot = slot<Transaction>()
        coEvery { saveTransaction(capture(txSlot)) } returns 1L

        viewModel.loadTransaction(42L)
        advanceUntilIdle()
        viewModel.save()
        advanceUntilIdle()

        assertEquals(99L, txSlot.captured.recurringRuleId)
        assertEquals(42L, txSlot.captured.id)
    }

    @Test
    fun `save uses null recurringRuleId in add mode`() = runTest {
        val txSlot = slot<Transaction>()
        coEvery { saveTransaction(capture(txSlot)) } returns 1L

        viewModel.onCategorySelected(fakeCategory)
        viewModel.onDigit(1)
        viewModel.save()
        advanceUntilIdle()

        assertNull(txSlot.captured.recurringRuleId)
    }
}
