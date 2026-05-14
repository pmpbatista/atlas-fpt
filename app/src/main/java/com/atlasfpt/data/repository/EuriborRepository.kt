package com.atlasfpt.data.repository

import android.content.Context
import com.atlasfpt.domain.model.EuriborRate
import com.atlasfpt.domain.model.ReferenceRate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class EuriborCache(val byTenor: Map<ReferenceRate, EuriborRate> = emptyMap()) {
    fun get(tenor: ReferenceRate): EuriborRate? = byTenor[tenor]
}

@Singleton
class EuriborRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs = context.getSharedPreferences("atlas_euribor", Context.MODE_PRIVATE)
    private val _cache = MutableStateFlow(loadCache())
    val cache: StateFlow<EuriborCache> = _cache.asStateFlow()

    fun put(rate: EuriborRate) {
        prefs.edit()
            .putString(keyValue(rate.tenor), rate.value.toString())
            .putLong(keyAt(rate.tenor), rate.asOf.toEpochMilli())
            .putString(keySource(rate.tenor), rate.source.name)
            .apply()
        _cache.value = loadCache()
    }

    private fun loadCache(): EuriborCache {
        val map = ReferenceRate.values().mapNotNull { tenor ->
            val value = prefs.getString(keyValue(tenor), null)?.toDoubleOrNull() ?: return@mapNotNull null
            if (!prefs.contains(keyAt(tenor))) return@mapNotNull null
            val atMillis = prefs.getLong(keyAt(tenor), 0L)
            val sourceName = prefs.getString(keySource(tenor), EuriborRate.Source.ECB.name) ?: EuriborRate.Source.ECB.name
            tenor to EuriborRate(
                tenor = tenor,
                value = value,
                asOf = Instant.ofEpochMilli(atMillis),
                source = runCatching { EuriborRate.Source.valueOf(sourceName) }.getOrDefault(EuriborRate.Source.ECB),
            )
        }.toMap()
        return EuriborCache(map)
    }

    private fun keyValue(tenor: ReferenceRate) = "${tenor.name}_value"
    private fun keyAt(tenor: ReferenceRate) = "${tenor.name}_at"
    private fun keySource(tenor: ReferenceRate) = "${tenor.name}_source"
}
