package com.atlasfpt.data.repository

import com.atlasfpt.data.db.dao.AssetDao
import com.atlasfpt.domain.model.AssetListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetRepository @Inject constructor(private val dao: AssetDao) {

    fun observeAssetList(): Flow<List<AssetListItem>> = dao.observeAssetList()

    suspend fun deleteById(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }
}
