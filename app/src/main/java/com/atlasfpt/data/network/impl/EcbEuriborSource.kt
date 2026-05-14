package com.atlasfpt.data.network.impl

import android.util.Log
import com.atlasfpt.data.network.EcbDataApi
import com.atlasfpt.data.network.EuriborSource
import com.atlasfpt.domain.model.EuriborFetchResult
import com.atlasfpt.domain.model.EuriborRate
import com.atlasfpt.domain.model.ReferenceRate
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EcbEuriborSource @Inject constructor(
    private val api: EcbDataApi,
) : EuriborSource {

    override suspend fun fetch(tenor: ReferenceRate): EuriborFetchResult {
        val key = ecbKey(tenor)
        return runCatching {
            val response = api.fetchSeries(key)
            if (!response.isSuccessful) {
                EuriborFetchResult.Error("HTTP ${response.code()}")
            } else {
                val body = response.body()?.string().orEmpty()
                parse(tenor, body) ?: EuriborFetchResult.Error("Unrecognised CSV shape")
            }
        }.getOrElse { t ->
            runCatching { Log.w("EcbEuriborSource", "fetch($tenor) failed", t) }
            EuriborFetchResult.Error(t.message ?: t.javaClass.simpleName)
        }
    }

    private fun parse(tenor: ReferenceRate, csv: String): EuriborFetchResult? {
        val lines = csv.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.size < 2) return null
        val header = splitCsvLine(lines.first())
        val timeIdx = header.indexOf("TIME_PERIOD").takeIf { it >= 0 } ?: return null
        val valueIdx = header.indexOf("OBS_VALUE").takeIf { it >= 0 } ?: return null
        val row = splitCsvLine(lines.last())
        val date = row.getOrNull(timeIdx)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val value = row.getOrNull(valueIdx)?.toDoubleOrNull() ?: return null
        return EuriborFetchResult.Success(
            EuriborRate(
                tenor = tenor,
                value = value,
                asOf = date.atStartOfDay(ZoneOffset.UTC).toInstant(),
                source = EuriborRate.Source.ECB,
            ),
        )
    }

    private fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> { out.add(buf.toString()); buf.clear() }
                else -> buf.append(ch)
            }
        }
        out.add(buf.toString())
        return out
    }

    private fun ecbKey(tenor: ReferenceRate): String = when (tenor) {
        ReferenceRate.EURIBOR_1M -> "D.U2.EUR.RT.MM.EURIBOR1MD_.HSTA"
        ReferenceRate.EURIBOR_3M -> "D.U2.EUR.RT.MM.EURIBOR3MD_.HSTA"
        ReferenceRate.EURIBOR_6M -> "D.U2.EUR.RT.MM.EURIBOR6MD_.HSTA"
        ReferenceRate.EURIBOR_12M -> "D.U2.EUR.RT.MM.EURIBOR1YD_.HSTA"
    }
}
