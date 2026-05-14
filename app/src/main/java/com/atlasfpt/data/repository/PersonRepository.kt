package com.atlasfpt.data.repository

import com.atlasfpt.data.db.dao.PersonDao
import com.atlasfpt.data.db.entity.toDomain
import com.atlasfpt.data.db.entity.toEntity
import com.atlasfpt.domain.model.Person
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonRepository @Inject constructor(private val dao: PersonDao) {

    fun observeAll(): Flow<List<Person>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun save(person: Person): Long = withContext(Dispatchers.IO) {
        dao.insert(person.toEntity())
    }

    suspend fun rename(person: Person, newName: String) = withContext(Dispatchers.IO) {
        dao.update(person.copy(name = newName).toEntity())
    }

    suspend fun delete(person: Person) = withContext(Dispatchers.IO) {
        dao.delete(person.toEntity())
    }

    suspend fun countTransactions(personId: Long): Int = withContext(Dispatchers.IO) {
        dao.countTransactions(personId)
    }
}
