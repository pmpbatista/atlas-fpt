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
