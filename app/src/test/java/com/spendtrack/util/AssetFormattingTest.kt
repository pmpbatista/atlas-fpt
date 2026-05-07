package com.spendtrack.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class AssetFormattingTest {

    @Test
    fun `parseDecimal accepts dot decimal separator`() {
        assertEquals(1234.56, parseDecimal("1234.56")!!, 0.0001)
    }

    @Test
    fun `parseDecimal accepts comma decimal separator`() {
        assertEquals(1234.56, parseDecimal("1234,56")!!, 0.0001)
    }

    @Test
    fun `parseDecimal trims whitespace`() {
        assertEquals(42.0, parseDecimal("  42 ")!!, 0.0001)
    }

    @Test
    fun `parseDecimal returns null for empty string`() {
        assertNull(parseDecimal(""))
    }

    @Test
    fun `parseDecimal returns null for non-numeric`() {
        assertNull(parseDecimal("abc"))
    }

    @Test
    fun `parseDecimal accepts negative values`() {
        assertEquals(-0.5, parseDecimal("-0,5")!!, 0.0001)
    }

    @Test
    fun `relativeTimeString returns today for now`() {
        val now = Instant.parse("2026-05-06T12:00:00Z")
        val result = relativeTimeString(now.toEpochMilli(), now)
        assertEquals("today", result)
    }

    @Test
    fun `relativeTimeString returns yesterday for 1 day ago`() {
        // Both instants are at midnight UTC; the local-date diff is timezone-stable
        // because they're exactly 24h apart.
        val now = LocalDate.of(2026, 5, 6).atStartOfDay(ZoneOffset.UTC).toInstant()
        val past = LocalDate.of(2026, 5, 5).atStartOfDay(ZoneOffset.UTC).toInstant()
        assertEquals("yesterday", relativeTimeString(past.toEpochMilli(), now))
    }

    @Test
    fun `relativeTimeString returns months ago for 90 days`() {
        val now = LocalDate.of(2026, 5, 6).atStartOfDay(ZoneOffset.UTC).toInstant()
        val past = LocalDate.of(2026, 2, 5).atStartOfDay(ZoneOffset.UTC).toInstant()
        val result = relativeTimeString(past.toEpochMilli(), now)
        // 90 days / 30 = 3 months
        assertEquals("3 months ago", result)
    }

    @Test
    fun `relativeTimeString returns over a year ago for 400 days`() {
        val now = LocalDate.of(2026, 5, 6).atStartOfDay(ZoneOffset.UTC).toInstant()
        val past = LocalDate.of(2025, 4, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val result = relativeTimeString(past.toEpochMilli(), now)
        assertEquals("over a year ago", result)
    }

    @Test
    fun `monthsRemaining returns credit ended when past`() {
        val today = LocalDate.of(2026, 5, 6)
        val end = LocalDate.of(2025, 1, 1)
        assertEquals("Credit ended", monthsRemaining(end, today))
    }

    @Test
    fun `monthsRemaining returns months for under a year`() {
        val today = LocalDate.of(2026, 5, 6)
        val end = LocalDate.of(2026, 11, 6)
        assertEquals("6 months remaining", monthsRemaining(end, today))
    }

    @Test
    fun `monthsRemaining returns years and months for over a year`() {
        val today = LocalDate.of(2026, 5, 6)
        val end = LocalDate.of(2030, 8, 6)
        assertEquals("4 years 3 months remaining", monthsRemaining(end, today))
    }

    @Test
    fun `monthsRemaining returns whole years when no remainder`() {
        val today = LocalDate.of(2026, 5, 6)
        val end = LocalDate.of(2030, 5, 6)
        assertEquals("4 years remaining", monthsRemaining(end, today))
    }

    @Test
    fun `formatPercent uses comma decimal separator`() {
        assertEquals("3,20%", formatPercent(3.2))
        assertEquals("-0,50%", formatPercent(-0.5))
    }

    // Helper to construct RealEstateAsset for describeInterest tests
    private fun realEstate(
        interestType: com.spendtrack.domain.model.InterestType? = null,
        fixedRate: Double? = null,
        referenceRate: com.spendtrack.domain.model.ReferenceRate? = null,
        spread: Double? = null
    ) = com.spendtrack.domain.model.RealEstateAsset(
        id = 1L,
        name = "x",
        currencyCode = "EUR",
        currentValue = 100_000.0,
        currentValueUpdatedAt = java.time.Instant.parse("2026-01-01T00:00:00Z"),
        purchaseDate = java.time.LocalDate.of(2020, 1, 1),
        notes = null,
        cost = 100_000.0,
        investedCapital = 100_000.0,
        debtAmount = if (interestType != null) 50_000.0 else null,
        outstandingDebt = if (interestType != null) 50_000.0 else null,
        interestType = interestType,
        fixedRate = fixedRate,
        referenceRate = referenceRate,
        spread = spread,
        creditEndDate = if (interestType != null) java.time.LocalDate.of(2050, 1, 1) else null,
        district = "Lisboa",
        council = "Lisboa",
        parish = "Alvalade",
        sizeM2 = 85.0,
        energyRating = com.spendtrack.domain.model.EnergyRating.B
    )

    @Test
    fun `describeInterest returns Bought outright for cash purchase`() {
        val asset = realEstate(interestType = null)
        assertEquals("Bought outright", describeInterest(asset))
    }

    @Test
    fun `describeInterest formats FIXED rate`() {
        val asset = realEstate(
            interestType = com.spendtrack.domain.model.InterestType.FIXED,
            fixedRate = 3.2
        )
        assertEquals("Fixed 3,20%", describeInterest(asset))
    }

    @Test
    fun `describeInterest formats VARIABLE with positive spread`() {
        val asset = realEstate(
            interestType = com.spendtrack.domain.model.InterestType.VARIABLE,
            referenceRate = com.spendtrack.domain.model.ReferenceRate.EURIBOR_12M,
            spread = 1.5
        )
        val result = describeInterest(asset)
        assertEquals("Euribor 12M + 1,50%", result)
    }

    @Test
    fun `describeInterest formats VARIABLE with negative spread`() {
        val asset = realEstate(
            interestType = com.spendtrack.domain.model.InterestType.VARIABLE,
            referenceRate = com.spendtrack.domain.model.ReferenceRate.EURIBOR_12M,
            spread = -0.2
        )
        val result = describeInterest(asset)
        assertEquals("Euribor 12M − 0,20%", result)
    }

    @Test fun `formatQuantity uses no decimals for ge 100`() {
        assertEquals("100", formatQuantity(100.0))
        assertEquals("12345", formatQuantity(12345.0))
    }

    @Test fun `formatQuantity uses up to 4 decimals between 1 and 100`() {
        assertEquals("10.5", formatQuantity(10.5))
        assertEquals("1.2345", formatQuantity(1.2345))
        assertEquals("3", formatQuantity(3.0))
    }

    @Test fun `formatQuantity uses up to 6 decimals between 0_001 and 1`() {
        assertEquals("0.001234", formatQuantity(0.001234))
        assertEquals("0.5", formatQuantity(0.5))
    }

    @Test fun `formatQuantity uses up to 8 decimals below 0_001`() {
        assertEquals("0.00000012", formatQuantity(0.00000012))
    }

    @Test fun `formatSignedCurrency positive`() {
        assertEquals("+ €123,45", formatSignedCurrency(123.45, "EUR"))
    }

    @Test fun `formatSignedCurrency negative`() {
        assertEquals("− €50,00", formatSignedCurrency(-50.0, "EUR"))
    }

    @Test fun `formatSignedCurrency zero treated as positive`() {
        assertEquals("+ €0,00", formatSignedCurrency(0.0, "EUR"))
    }
}
