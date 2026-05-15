package com.atlasfpt.domain.usecase

import com.atlasfpt.domain.model.FxRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConvertCurrencyUseCaseTest {

    private val convert = ConvertCurrencyUseCase()
    private val rates = mapOf(
        "USD" to FxRate("USD", unitsPerEur = 1.10, fetchedAt = 1L),
        "GBP" to FxRate("GBP", unitsPerEur = 0.85, fetchedAt = 1L),
    )

    @Test
    fun `identity returns amount unchanged`() {
        assertEquals(100.0, convert(100.0, "EUR", "EUR", rates)!!, 0.0001)
        assertEquals(100.0, convert(100.0, "USD", "USD", rates)!!, 0.0001)
    }

    @Test
    fun `EUR to foreign multiplies by unitsPerEur`() {
        assertEquals(110.0, convert(100.0, "EUR", "USD", rates)!!, 0.0001)
    }

    @Test
    fun `foreign to EUR divides by unitsPerEur`() {
        assertEquals(100.0, convert(110.0, "USD", "EUR", rates)!!, 0.0001)
    }

    @Test
    fun `foreign to foreign routes via EUR`() {
        // 110 USD -> 100 EUR -> 85 GBP
        assertEquals(85.0, convert(110.0, "USD", "GBP", rates)!!, 0.0001)
    }

    @Test
    fun `missing source rate returns null`() {
        assertNull(convert(100.0, "JPY", "EUR", rates))
    }

    @Test
    fun `missing target rate returns null`() {
        assertNull(convert(100.0, "EUR", "JPY", rates))
    }
}
