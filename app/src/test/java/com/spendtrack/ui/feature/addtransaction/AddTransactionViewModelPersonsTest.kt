package com.spendtrack.ui.feature.addtransaction

import app.cash.turbine.test
import com.spendtrack.data.repository.CategoryRepository
import com.spendtrack.data.repository.PersonRepository
import com.spendtrack.data.repository.TransactionRepository
import com.spendtrack.data.settings.AppSettings
import com.spendtrack.data.settings.SettingsRepository
import com.spendtrack.domain.model.Person
import com.spendtrack.domain.usecase.DeleteTransactionUseCase
import com.spendtrack.domain.usecase.SaveTransactionUseCase
import com.spendtrack.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddTransactionViewModelPersonsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val saveTransaction: SaveTransactionUseCase = mockk(relaxed = true)
    private val categoryRepository: CategoryRepository = mockk()
    private val transactionRepository: TransactionRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk()
    private val deleteTransaction: DeleteTransactionUseCase = mockk(relaxed = true)
    private val personRepository: PersonRepository = mockk()

    private val alice = Person(1L, "Alice")
    private val bob = Person(2L, "Bob")

    private lateinit var viewModel: AddTransactionViewModel

    @Before
    fun setup() {
        every { categoryRepository.observeAll() } returns flowOf(emptyList())
        every { settingsRepository.settings } returns MutableStateFlow(AppSettings())
        every { personRepository.observeAll() } returns flowOf(listOf(alice, bob))
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
    fun `availablePersons populated from repository`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.availablePersons.containsAll(listOf(alice, bob)))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onPersonAdded adds person to state`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onPersonAdded(alice)
            val state = awaitItem()
            assertTrue(state.persons.contains(alice))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onPersonAdded is idempotent`() = runTest {
        viewModel.onPersonAdded(alice)
        viewModel.onPersonAdded(alice)
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.persons.count { it.id == alice.id } == 1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onPersonRemoved removes person from state`() = runTest {
        viewModel.onPersonAdded(alice)
        viewModel.uiState.test {
            awaitItem()
            viewModel.onPersonRemoved(alice)
            val state = awaitItem()
            assertFalse(state.persons.contains(alice))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onShowPersonPicker sets showPersonPicker true`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onShowPersonPicker()
            assertTrue(awaitItem().showPersonPicker)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onDismissPersonPicker sets showPersonPicker false`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onShowPersonPicker()
            awaitItem()
            viewModel.onDismissPersonPicker()
            assertFalse(awaitItem().showPersonPicker)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
