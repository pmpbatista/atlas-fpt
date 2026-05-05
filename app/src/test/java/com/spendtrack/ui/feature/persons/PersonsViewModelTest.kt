package com.spendtrack.ui.feature.persons

import app.cash.turbine.test
import com.spendtrack.data.repository.PersonRepository
import com.spendtrack.domain.model.Person
import com.spendtrack.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersonsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val personRepository: PersonRepository = mockk(relaxed = true)
    private val alice = Person(1L, "Alice")
    private val bob = Person(2L, "Bob")

    private lateinit var viewModel: PersonsViewModel

    @Before
    fun setup() {
        every { personRepository.observeAll() } returns flowOf(listOf(alice, bob))
        viewModel = PersonsViewModel(personRepository)
    }

    @Test
    fun `persons populated from repository`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(listOf(alice, bob), state.persons)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onShowAddDialog sets showAddDialog true`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onShowAddDialog()
            assertTrue(awaitItem().showAddDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onDismissAddDialog sets showAddDialog false`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onShowAddDialog()
            awaitItem()
            viewModel.onDismissAddDialog()
            assertFalse(awaitItem().showAddDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addPerson calls repository and dismisses dialog`() = runTest {
        coEvery { personRepository.save(any()) } returns 3L
        viewModel.onShowAddDialog()

        viewModel.addPerson("Charlie")
        advanceUntilIdle()

        coVerify { personRepository.save(Person(0L, "Charlie")) }
        viewModel.uiState.test {
            assertFalse(awaitItem().showAddDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addPerson trims whitespace`() = runTest {
        viewModel.addPerson("  Dave  ")
        advanceUntilIdle()
        coVerify { personRepository.save(Person(0L, "Dave")) }
    }

    @Test
    fun `onRequestDelete sets deleteTarget and count`() = runTest {
        coEvery { personRepository.countTransactions(1L) } returns 3

        viewModel.onRequestDelete(alice)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(alice, state.deleteTarget)
            assertEquals(3, state.deleteTransactionCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onDismissDelete clears deleteTarget`() = runTest {
        coEvery { personRepository.countTransactions(1L) } returns 0
        viewModel.onRequestDelete(alice)
        advanceUntilIdle()

        viewModel.onDismissDelete()

        viewModel.uiState.test {
            assertNull(awaitItem().deleteTarget)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onConfirmDelete calls repository and clears target`() = runTest {
        coEvery { personRepository.countTransactions(1L) } returns 0
        viewModel.onRequestDelete(alice)
        advanceUntilIdle()

        viewModel.onConfirmDelete()
        advanceUntilIdle()

        coVerify { personRepository.delete(alice) }
        viewModel.uiState.test {
            assertNull(awaitItem().deleteTarget)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
