package com.spendtrack.data.repository

import android.util.Log
import com.spendtrack.data.db.dao.FinancialDao
import com.spendtrack.data.network.PriceSource
import com.spendtrack.domain.model.QuoteResult
import com.spendtrack.domain.model.TickerQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class RefreshResult(val succeeded: Int, val failed: Int)

@Singleton
class PriceRepository @Inject constructor(
    private val source: PriceSource,
    private val financialDao: FinancialDao,
    private val financialRepository: FinancialRepository,
) {
    private val cache = ConcurrentHashMap<String, CachedQuote>()
    private val refreshMutex = Mutex()
    private val ttl: Duration = Duration.ofMinutes(15)

    suspend fun getQuote(ticker: String, force: Boolean = false): TickerQuote? = withContext(Dispatchers.IO) {
        if (!force) cache[ticker]?.takeIf { it.isFresh(ttl) }?.let { return@withContext it.quote }
        when (val res = source.fetchQuote(ticker)) {
            is QuoteResult.Success -> {
                cache[ticker] = CachedQuote(res.quote, Instant.now())
                applyToHoldingIfTracked(ticker, res.quote.price)
                res.quote
            }
            QuoteResult.NotFound, is QuoteResult.Error -> {
                fallbackQuote(ticker)
            }
        }
    }

    /** Always hits network — for the add-asset flow. Returns the raw QuoteResult. */
    suspend fun validateTicker(ticker: String): QuoteResult = source.fetchQuote(ticker)

    /**
     * Force-refreshes every tracked financial asset. Returns success/failure counts.
     * Uses a Mutex so concurrent calls share an in-flight execution.
     */
    suspend fun refreshAll(): RefreshResult = refreshMutex.withLock {
        val tickers = financialDao.getAllTickers()
        if (tickers.isEmpty()) return RefreshResult(0, 0)
        val semaphore = Semaphore(3)
        var succeeded = 0
        var failed = 0
        coroutineScope {
            tickers.map { (assetId, ticker) ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        when (val res = source.fetchQuote(ticker)) {
                            is QuoteResult.Success -> {
                                cache[ticker] = CachedQuote(res.quote, Instant.now())
                                financialRepository.applyPriceUpdate(assetId, res.quote.price)
                                true
                            }
                            else -> false
                        }
                    }
                }
            }.forEach { deferred ->
                if (deferred.await()) succeeded++ else failed++
            }
        }
        RefreshResult(succeeded, failed)
    }

    private suspend fun applyToHoldingIfTracked(ticker: String, price: Double) {
        val tickers = financialDao.getAllTickers()
        val assetId = tickers.firstOrNull { it.ticker == ticker }?.assetId ?: return
        runCatching { financialRepository.applyPriceUpdate(assetId, price) }
            .onFailure { runCatching { Log.w("PriceRepository", "applyPriceUpdate failed", it) } }
    }

    private suspend fun fallbackQuote(ticker: String): TickerQuote? {
        val assetId = financialDao.getAllTickers()
            .firstOrNull { it.ticker == ticker }?.assetId ?: return null
        val holding = financialDao.getHolding(assetId) ?: return null
        val price = holding.latestPrice ?: return null
        val at = holding.latestPriceAt ?: return null
        return TickerQuote(
            ticker = holding.ticker,
            displayName = holding.displayName,
            currencyCode = "", // unknown at this layer; consumers don't use it for fallback
            price = price,
            asOf = Instant.ofEpochMilli(at),
        )
    }

    private data class CachedQuote(val quote: TickerQuote, val cachedAt: Instant) {
        fun isFresh(ttl: Duration): Boolean = Duration.between(cachedAt, Instant.now()) < ttl
    }
}
