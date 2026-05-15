package com.atlasfpt.data.network

import com.atlasfpt.data.network.impl.EcbFxRatesSource
import com.atlasfpt.domain.model.FxFetchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EcbFxRatesSourceTest {

    private val source = EcbFxRatesSource(api = StubApi())

    @Test
    fun `parse extracts time and rates from canonical ECB XML`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gesmes:Envelope xmlns:gesmes="http://www.gesmes.org/xml/2002-08-01" xmlns="http://www.ecb.int/vocabulary/2002-08-01/eurofxref">
              <gesmes:subject>Reference rates</gesmes:subject>
              <gesmes:Sender><gesmes:name>European Central Bank</gesmes:name></gesmes:Sender>
              <Cube>
                <Cube time='2026-05-14'>
                  <Cube currency='USD' rate='1.0780'/>
                  <Cube currency='GBP' rate='0.8500'/>
                  <Cube currency='BRL' rate='5.5400'/>
                </Cube>
              </Cube>
            </gesmes:Envelope>
        """.trimIndent()

        val result = source.parse(xml, fetchedAt = 42L)
        assertTrue(result is FxFetchResult.Success)
        val success = result as FxFetchResult.Success

        assertEquals("2026-05-14", success.asOfDate)
        assertEquals(3, success.rates.size)
        val byCode = success.rates.associateBy { it.currencyCode }
        assertEquals(1.0780, byCode.getValue("USD").unitsPerEur, 0.0001)
        assertEquals(0.85, byCode.getValue("GBP").unitsPerEur, 0.0001)
        assertEquals(5.54, byCode.getValue("BRL").unitsPerEur, 0.0001)
        assertEquals(42L, byCode.getValue("USD").fetchedAt)
    }

    @Test
    fun `parse rejects empty document`() {
        val result = source.parse("<root/>", fetchedAt = 0L)
        assertEquals(null, result)
    }

    @Test
    fun `parse ignores invalid rate values`() {
        val xml = """
            <Cube>
              <Cube time='2026-05-14'>
                <Cube currency='USD' rate='not-a-number'/>
                <Cube currency='GBP' rate='0.85'/>
              </Cube>
            </Cube>
        """.trimIndent()
        val result = source.parse(xml, fetchedAt = 0L) as FxFetchResult.Success
        assertEquals(1, result.rates.size)
        assertEquals("GBP", result.rates.single().currencyCode)
    }

    private class StubApi : EcbFxRatesApi {
        override suspend fun fetchDaily() = throw UnsupportedOperationException("network not used")
    }
}
