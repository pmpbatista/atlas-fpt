package com.atlasfpt.data.repository

import androidx.room.withTransaction
import com.atlasfpt.data.db.AppDatabase
import com.atlasfpt.data.db.dao.AssetDao
import com.atlasfpt.data.db.dao.RealEstateDao
import com.atlasfpt.data.db.entity.toAssetEntity
import com.atlasfpt.data.db.entity.toDetailsEntity
import com.atlasfpt.data.db.entity.toRealEstateDomain
import com.atlasfpt.domain.model.RealEstateAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealEstateRepository @Inject constructor(
    private val db: AppDatabase,
    private val assetDao: AssetDao,
    private val realEstateDao: RealEstateDao,
) {

    suspend fun getById(id: Long): RealEstateAsset? = withContext(Dispatchers.IO) {
        realEstateDao.getWithDetails(id)?.toRealEstateDomain()
    }

    /**
     * Inserts (when [asset].id == 0L) or updates the parent + details rows in a single
     * Room transaction. Bumps `currentValueUpdatedAt` to "now" on insert, or on update only
     * when `currentValue` changed compared to the stored row.
     */
    suspend fun save(asset: RealEstateAsset): Long = withContext(Dispatchers.IO) {
        db.withTransaction {
            val nowMillis = Instant.now().toEpochMilli()
            if (asset.id == 0L) {
                val parentId = assetDao.insert(asset.toAssetEntity(nowMillis))
                realEstateDao.insertDetails(asset.toDetailsEntity(parentId))
                parentId
            } else {
                val existing = assetDao.getById(asset.id)
                    ?: throw IllegalStateException("Asset ${asset.id} no longer exists")
                val updatedAt =
                    if (existing.currentValue != asset.currentValue) nowMillis
                    else existing.currentValueUpdatedAt
                assetDao.update(asset.toAssetEntity(updatedAt))
                realEstateDao.updateDetails(asset.toDetailsEntity(asset.id))
                asset.id
            }
        }
    }
}
