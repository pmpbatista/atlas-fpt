package com.atlasfpt.data.repository

import com.atlasfpt.data.db.dao.LabelDao
import com.atlasfpt.data.db.entity.toDomain
import com.atlasfpt.data.db.entity.toEntity
import com.atlasfpt.domain.model.Label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LabelRepository @Inject constructor(private val dao: LabelDao) {

    fun observeAll(): Flow<List<Label>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun save(label: Label): Long = withContext(Dispatchers.IO) {
        dao.insert(label.toEntity())
    }

    suspend fun delete(label: Label) = withContext(Dispatchers.IO) {
        dao.delete(label.toEntity())
    }
}
