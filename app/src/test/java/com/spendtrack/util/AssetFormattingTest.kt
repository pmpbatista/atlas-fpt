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
}
