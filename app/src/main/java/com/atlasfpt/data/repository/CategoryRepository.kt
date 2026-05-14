package com.atlasfpt.data.repository

import com.atlasfpt.data.db.dao.CategoryDao
import com.atlasfpt.data.db.entity.toDomain
import com.atlasfpt.data.db.entity.toEntity
import com.atlasfpt.domain.model.Category
import com.atlasfpt.domain.model.CategoryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(private val dao: CategoryDao) {

    fun observeAll(): Flow<List<Category>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeByType(type: CategoryType): Flow<List<Category>> =
        dao.observeByType(type).map { list -> list.map { it.toDomain() } }

    suspend fun save(category: Category): Long = withContext(Dispatchers.IO) {
        dao.insert(category.toEntity())
    }

    suspend fun update(category: Category) = withContext(Dispatchers.IO) {
        dao.update(category.toEntity())
    }

    suspend fun delete(category: Category) = withContext(Dispatchers.IO) {
        dao.delete(category.toEntity())
    }
}
