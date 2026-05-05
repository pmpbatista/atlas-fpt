package com.spendtrack.data.repository

import com.spendtrack.data.db.dao.TransactionDao
import com.spendtrack.data.db.entity.PersonEntity
import com.spendtrack.data.db.entity.TransactionEntity
import com.spendtrack.data.db.entity.TransactionPersonCrossRef
import com.spendtrack.data.db.entity.TransactionWithDetails
import com.spendtrack.domain.model.Category
import com.spendtrack.domain.model.CategoryType
import com.spendtrack.domain.model.Person
import com.spendtrack.domain.model.Transaction
import com.spendtrack.domain.model.TransactionType
import com.spendtrack.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionRepositoryPersonsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dao: TransactionDao = mockk(relaxed = true)
    private val repo = TransactionRepository(dao)

    private val fakeCategory = Category(1L, "Food", "", 0, CategoryType.EXPENSE)
    private fun makeTransaction(id: Long, persons: List<Person>) = Transaction(
        id = id,
        amount = 10.0,
        type = TransactionType.EXPENSE,
        category = fakeCategory,
        date = LocalDate.of(2026, 1, 1),
        note = null,
        photoUri = null,
        labels = emptyList(),
        persons = persons,
        recurringRuleId = null,
        isScheduled = false
    )

    @Test
    fun `save inserts person cross-refs after insert`() = runTest {
        coEvery { dao.insert(any()) } returns 10L
        val alice = Person(1L, "Alice")
        val bob = Person(2L, "Bob")

        repo.save(makeTransaction(0L, listOf(alice, bob)))

        coVerify { dao.deleteAllPersonsForTransaction(10L) }
        coVerify { dao.insertPersonCrossRef(TransactionPersonCrossRef(10L, 1L)) }
        coVerify { dao.insertPersonCrossRef(TransactionPersonCrossRef(10L, 2L)) }
    }

    @Test
    fun `save replaces person cross-refs on update`() = runTest {
        val alice = Person(1L, "Alice")

        repo.save(makeTransaction(5L, listOf(alice)))

        coVerify { dao.deleteAllPersonsForTransaction(5L) }
        coVerify { dao.insertPersonCrossRef(TransactionPersonCrossRef(5L, 1L)) }
    }

    @Test
    fun `save with no persons clears existing cross-refs`() = runTest {
        repo.save(makeTransaction(5L, emptyList()))

        coVerify { dao.deleteAllPersonsForTransaction(5L) }
        coVerify(exactly = 0) { dao.insertPersonCrossRef(any()) }
    }
}
