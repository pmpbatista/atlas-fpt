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
    fun `returns all transactions grouped by day`() = runTest {
        every { repo.observeAll() } returns flowOf(listOf(tx(1, 10.0, listOf(maria)), tx(2, 20.0, emptyList())))
        every { repo.observeScheduled() } returns flowOf(emptyList())

        val sut = GetTimelineUseCase(repo)

        sut().test {
            val data = awaitItem()
            assertEquals(2, data.days.first().rows.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
