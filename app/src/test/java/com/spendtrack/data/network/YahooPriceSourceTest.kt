package com.spendtrack.data.network

import com.spendtrack.data.network.impl.YahooPriceSource
import com.spendtrack.domain.model.QuoteResult
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class YahooPriceSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: YahooPriceSource

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (compatible; SpendTrack/1.0)")
                        .build()
                )
            }
            .build()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(YahooFinanceApi::class.java)
        source = YahooPriceSource(api)
    }

    @After
    fun tearDown() { server.shutdown() }

    @Test
    fun `success maps meta to TickerQuote`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"chart":{"result":[{"meta":{"symbol":"AAPL","currency":"USD","shortName":"Apple Inc.","longName":"Apple Inc.","regularMarketPrice":234.56,"regularMarketTime":1736186400}}],"error":null}}
        """.trimIndent()))

        val result = source.fetchQuote("AAPL")

        assertTrue(result is QuoteResult.Success)
        val q = (result as QuoteResult.Success).quote
        assertEquals("AAPL", q.ticker)
        assertEquals("Apple Inc.", q.displayName)
        assertEquals("USD", q.currencyCode)
        assertEquals(234.56, q.price, 0.0001)
    }

    @Test
    fun `chart_error returns NotFound`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"chart":{"result":null,"error":{"code":"Not Found","description":"No data found"}}}
        """.trimIndent()))

        val result = source.fetchQuote("ZZZZZZ")
        assertTrue(result is QuoteResult.NotFound)
    }

    @Test
    fun `404 returns NotFound`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"chart":{"result":null,"error":{"code":"Not Found"}}}"""))
        val result = source.fetchQuote("BADTICKER")
        assertTrue(result is QuoteResult.NotFound)
    }

    @Test
    fun `5xx returns Error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val result = source.fetchQuote("AAPL")
        assertTrue(result is QuoteResult.Error)
    }

    @Test
    fun `malformed JSON returns Error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
        val result = source.fetchQuote("AAPL")
        assertTrue(result is QuoteResult.Error)
    }

    @Test
    fun `empty result list returns NotFound`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"chart":{"result":[],"error":null}}"""))
        val result = source.fetchQuote("AAPL")
        assertTrue(result is QuoteResult.NotFound)
    }

    @Test
    fun `request includes User-Agent header`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"chart":{"result":[{"meta":{"symbol":"AAPL","currency":"USD","shortName":"A","regularMarketPrice":1.0}}],"error":null}}
        """.trimIndent()))

        source.fetchQuote("AAPL")
        val recorded = server.takeRequest()
        val ua = recorded.getHeader("User-Agent")
        assertNotNull(ua)
        assertTrue("expected SpendTrack UA, got: $ua", ua!!.contains("SpendTrack"))
    }
}
