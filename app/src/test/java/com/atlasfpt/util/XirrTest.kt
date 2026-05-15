package com.atlasfpt.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

class XirrTest {

    @Test
    fun `invest 100 today and receive 110 in one year is 10 percent`() {
        val start = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2027, 1, 1)
        val r = Xirr.solve(listOf(start to -100.0, end to 110.0))
        assertNotNull(r)
        assertEquals(0.10, r!!, 0.005)
    }

    @Test
    fun `invest 100 today and receive 100 in one year is zero`() {
        val start = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2027, 1, 1)
        val r = Xirr.solve(listOf(start to -100.0, end to 100.0))
        assertNotNull(r)
        assertEquals(0.0, r!!, 1e-4)
    }

    @Test
    fun `invest 100 and receive 121 in two years is approximately 10 percent`() {
        val start = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2028, 1, 1)
        val r = Xirr.solve(listOf(start to -100.0, end to 121.0))
        assertNotNull(r)
        assertEquals(0.10, r!!, 0.005)
    }

    @Test
    fun `no sign change yields null`() {
        val start = LocalDate.of(2026, 1, 1)
        val r = Xirr.solve(listOf(start to -100.0, start.plusYears(1) to -10.0))
        assertNull(r)
    }

    @Test
    fun `empty or single flow yields null`() {
        assertNull(Xirr.solve(emptyList()))
        assertNull(Xirr.solve(listOf(LocalDate.now() to -1.0)))
    }
}
