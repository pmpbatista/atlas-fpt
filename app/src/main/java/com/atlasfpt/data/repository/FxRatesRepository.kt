package com.atlasfpt.data.repository

import com.atlasfpt.data.db.dao.FxRateDao
import com.atlasfpt.data.db.entity.FxRateEntity
import com.atlasfpt.data.network.FxRatesSource
import com.atlasfpt.domain.model.FxFetchResult
import com.atlasfpt.domain.model.FxRate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FxRatesRepository @Inject constructor(
    private val dao: FxRateDao,
    private val source: FxRatesSource,
) {

    fun observeRates(): Flow<Map<String, FxRate>> = dao.observeAll().map { rows ->
        rows.associate { row ->
            row.currencyCode to FxRate(
                currencyCode = row.currencyCode,
                unitsPerEur = row.unitsPerEur,
                fetchedAt = row.fetchedAt,
            )
        }
    }

    suspend fun getRate(code: String): FxRate? = withContext(Dispatchers.IO) {
        dao.getByCode(code)?.let {
            FxRate(currencyCode = it.currencyCode, unitsPerEur = it.unitsPerEur, fetchedAt = it.fetchedAt)
        }
    }

    suspend fun refresh(): FxFetchResult = withContext(Dispatchers.IO) {
        when (val result = source.fetch()) {
            is FxFetchResult.Success -> {
                dao.upsertAll(result.rates.map {
                    FxRateEntity(
                        currencyCode = it.currencyCode,
                        unitsPerEur = it.unitsPerEur,
                        fetchedAt = it.fetchedAt,
                    )
                })
                result
            }
            is FxFetchResult.Error -> result
        }
    }
}
