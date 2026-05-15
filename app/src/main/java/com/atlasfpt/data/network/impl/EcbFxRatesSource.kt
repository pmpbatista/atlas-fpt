package com.atlasfpt.data.network.impl

import android.util.Log
import com.atlasfpt.data.network.EcbFxRatesApi
import com.atlasfpt.data.network.FxRatesSource
import com.atlasfpt.domain.model.FxFetchResult
import com.atlasfpt.domain.model.FxRate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EcbFxRatesSource @Inject constructor(
    private val api: EcbFxRatesApi,
) : FxRatesSource {

    override suspend fun fetch(): FxFetchResult = runCatching {
        val response = api.fetchDaily()
        if (!response.isSuccessful) {
            return@runCatching FxFetchResult.Error("HTTP ${response.code()}")
        }
        val body = response.body()?.string()
            ?: return@runCatching FxFetchResult.Error("Empty response")
        parse(body, fetchedAt = System.currentTimeMillis())
            ?: FxFetchResult.Error("Unrecognised XML shape")
    }.getOrElse { t ->
        runCatching { Log.w("EcbFxRatesSource", "fetch failed", t) }
        FxFetchResult.Error(t.message ?: t.javaClass.simpleName)
    }

    /**
     * The ECB feed is shallow and well-formed: a regex over the `<Cube ...>` tags is sufficient
     * and avoids pulling in an Android-only XmlPullParser implementation (which the JVM unit-test
     * runner doesn't ship). Format-stability has been good for over a decade.
     */
    internal fun parse(xml: String, fetchedAt: Long): FxFetchResult? {
        val asOf = TIME_REGEX.find(xml)?.groupValues?.getOrNull(1) ?: return null
        val rates = RATE_REGEX.findAll(xml).mapNotNull { match ->
            val code = match.groupValues.getOrNull(1) ?: return@mapNotNull null
            val rate = match.groupValues.getOrNull(2)?.toDoubleOrNull() ?: return@mapNotNull null
            if (rate <= 0.0) return@mapNotNull null
            FxRate(currencyCode = code, unitsPerEur = rate, fetchedAt = fetchedAt)
        }.toList()
        if (rates.isEmpty()) return null
        return FxFetchResult.Success(rates = rates, asOfDate = asOf)
    }

    private companion object {
        val TIME_REGEX = Regex("""<Cube\s+time=['"]([0-9]{4}-[0-9]{2}-[0-9]{2})['"]""")
        val RATE_REGEX = Regex("""<Cube\s+currency=['"]([A-Z]{3})['"]\s+rate=['"]([^'"]+)['"]\s*/?>""")
    }
}
