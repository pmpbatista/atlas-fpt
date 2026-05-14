package com.atlasfpt.data.network.impl

import android.util.Log
import com.atlasfpt.data.network.ChartResponse
import com.atlasfpt.data.network.PriceSource
import com.atlasfpt.data.network.YahooFinanceApi
import com.atlasfpt.domain.model.QuoteResult
import com.atlasfpt.domain.model.TickerQuote
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YahooPriceSource @Inject constructor(
    private val api: YahooFinanceApi,
) : PriceSource {

    override suspend fun fetchQuote(ticker: String): QuoteResult {
        return runCatching {
            val response = api.getChart(ticker)
            when {
                response.code() == 404 -> QuoteResult.NotFound
                !response.isSuccessful -> QuoteResult.Error("HTTP ${response.code()}")
                else -> {
                    val body: ChartResponse? = response.body()
                    val result = body?.chart?.result
                    val error = body?.chart?.error
                    when {
                        error != null -> QuoteResult.NotFound
                        result.isNullOrEmpty() -> QuoteResult.NotFound
                        else -> {
                            val meta = result.first().meta
                            val price = meta.regularMarketPrice
                            val currency = meta.currency
                            val name = meta.longName ?: meta.shortName
                            if (price == null || currency == null || name == null) {
                                QuoteResult.NotFound
                            } else {
                                QuoteResult.Success(
                                    TickerQuote(
                                        ticker = meta.symbol,
                                        displayName = name,
                                        currencyCode = currency,
                                        price = price,
                                        asOf = meta.regularMarketTime?.let { Instant.ofEpochSecond(it) }
                                            ?: Instant.now(),
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }.getOrElse { t ->
            runCatching { Log.w("YahooPriceSource", "fetchQuote($ticker) failed", t) }
            QuoteResult.Error(t.message ?: t.javaClass.simpleName)
        }
    }
}
