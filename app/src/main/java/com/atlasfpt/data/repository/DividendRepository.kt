package com.atlasfpt.data.repository

import com.atlasfpt.data.db.dao.DividendDao
import com.atlasfpt.data.db.entity.toDomain
import com.atlasfpt.data.db.entity.toEntity
import com.atlasfpt.domain.model.Dividend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DividendRepository @Inject constructor(
    private val dao: DividendDao,
) {
    suspend fun listForAsset(assetId: Long): List<Dividend> = withContext(Dispatchers.IO) {
        dao.listForAsset(assetId).map { it.toDomain() }
    }

    suspend fun getById(id: Long): Dividend? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    suspend fun add(assetId: Long, dividend: Dividend): Long = withContext(Dispatchers.IO) {
        dao.insert(dividend.copy(id = 0).toEntity(assetId))
    }

    suspend fun update(assetId: Long, dividend: Dividend) = withContext(Dispatchers.IO) {
        dao.update(dividend.toEntity(assetId))
    }

    suspend fun delete(dividend: Dividend, assetId: Long) = withContext(Dispatchers.IO) {
        dao.delete(dividend.toEntity(assetId))
    }
}
