package com.atlasfpt.data.repository

import app.cash.turbine.test
import com.atlasfpt.data.db.dao.PersonDao
import com.atlasfpt.data.db.entity.PersonEntity
import com.atlasfpt.domain.model.Person
import com.atlasfpt.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersonRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dao: PersonDao = mockk()
    private val repo = PersonRepository(dao)

    @Test
    fun `observeAll returns mapped persons`() = runTest {
        every { dao.observeAll() } returns flowOf(listOf(PersonEntity(1L, "Alice")))

        repo.observeAll().test {
            assertEquals(listOf(Person(1L, "Alice")), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `save delegates to dao`() = runTest {
        coEvery { dao.insert(PersonEntity(0L, "Bob")) } returns 5L

        val result = repo.save(Person(0L, "Bob"))

        coVerify { dao.insert(PersonEntity(0L, "Bob")) }
        assertEquals(5L, result)
    }

    @Test
    fun `delete delegates to dao`() = runTest {
        coEvery { dao.delete(PersonEntity(1L, "Alice")) } returns Unit

        repo.delete(Person(1L, "Alice"))

        coVerify { dao.delete(PersonEntity(1L, "Alice")) }
    }

    @Test
    fun `countTransactions delegates to dao`() = runTest {
        coEvery { dao.countTransactions(1L) } returns 3

        val result = repo.countTransactions(1L)

        assertEquals(3, result)
    }
}
