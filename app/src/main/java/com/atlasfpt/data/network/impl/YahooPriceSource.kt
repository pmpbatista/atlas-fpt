package com.atlasfpt.data.network.impl

import android.util.Log
import com.atlasfpt.data.network.ChartResponse
import com.atlasfpt.data.network.PriceSource
import com.atlasfpt.data.network.YahooFinanceApi
import com.atlasfpt.domain.model.QuoteResult
import com.atlasfpt.domain.model.SearchResult
import com.atlasfpt.domain.model.TickerQuote
import com.atlasfpt.domain.model.TickerSearchResult
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

    override suspend fun searchTickers(query: String): SearchResult {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return SearchResult.Empty
        return runCatching {
            val response = api.search(trimmed)
            if (!response.isSuccessful) {
                SearchResult.Error("HTTP ${response.code()}")
            } else {
                val items = response.body()?.quotes.orEmpty().mapNotNull { q ->
                    val symbol = q.symbol ?: return@mapNotNull null
                    val name = q.longname ?: q.shortname ?: symbol
                    TickerSearchResult(
                        symbol = symbol,
                        displayName = name,
                        exchange = q.exchange,
                        typeLabel = q.typeDisp ?: q.quoteType,
                    )
                }
                if (items.isEmpty()) SearchResult.NoMatches else SearchResult.Success(items)
            }
        }.getOrElse { t ->
            runCatching { Log.w("YahooPriceSource", "searchTickers($query) failed", t) }
            SearchResult.Error(t.message ?: t.javaClass.simpleName)
        }
    }
}
