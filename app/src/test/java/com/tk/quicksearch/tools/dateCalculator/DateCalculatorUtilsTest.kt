package com.tk.quicksearch.tools.dateCalculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DateCalculatorUtilsTest {

    @Test
    fun parseTimeDiffQuery_returnsSameDayDuration() {
        val result = DateCalculatorUtils.parseTimeDiffQuery("9:00 AM to 6:00 PM")
        assertNotNull(result)
        assertEquals("9 hours", result?.label)
    }

    @Test
    fun parseTimeDiffQuery_returnsOvernightForwardDuration() {
        val result = DateCalculatorUtils.parseTimeDiffQuery("6:00 PM to 9:00 AM")
        assertNotNull(result)
        assertEquals("15 hours", result?.label)
    }

    @Test
    fun parseTimeDiffQuery_returnsZeroForEqualTimes() {
        val result = DateCalculatorUtils.parseTimeDiffQuery("6:00 PM to 6:00 PM")
        assertNotNull(result)
        assertEquals("0 minutes", result?.label)
    }

    @Test
    fun parseTimeArithmeticQuery_readsBareDurationAsFromNow() {
        val bare = DateCalculatorUtils.parseTimeArithmeticQuery("5min")
        val explicit = DateCalculatorUtils.parseTimeArithmeticQuery("5 minutes from now")
        assertNotNull(bare)
        assertEquals(explicit?.label, bare?.label)
        assertEquals(true, bare?.isAbsolute)
    }

    @Test
    fun parseTimeArithmeticQuery_readsBareCompoundAndHourAlias() {
        assertEquals(
            DateCalculatorUtils.parseTimeArithmeticQuery("2 hours 30 minutes from now")?.label,
            DateCalculatorUtils.parseTimeArithmeticQuery("2 hours 30 minutes")?.label,
        )
        assertEquals(
            DateCalculatorUtils.parseTimeArithmeticQuery("3 hours from now")?.label,
            DateCalculatorUtils.parseTimeArithmeticQuery("3h")?.label,
        )
    }

    @Test
    fun parseTimeArithmeticQuery_acceptsInPrefixAndBareSeconds() {
        assertNotNull(DateCalculatorUtils.parseTimeArithmeticQuery("in 45 minutes"))
        assertNotNull(DateCalculatorUtils.parseTimeArithmeticQuery("30 sec"))
    }

    @Test
    fun parseTimeArithmeticQuery_ignoresNonDurationQueries() {
        assertNull(DateCalculatorUtils.parseTimeArithmeticQuery("5"))
        assertNull(DateCalculatorUtils.parseTimeArithmeticQuery("5 m"))
        assertNull(DateCalculatorUtils.parseTimeArithmeticQuery("5pm"))
        assertNull(DateCalculatorUtils.parseTimeArithmeticQuery("hours"))
        assertNull(DateCalculatorUtils.parseTimeArithmeticQuery("3 hours after 5pm"))
    }

    @Test
    fun parseRelativeDateQuery_readsBareDurationAsFutureDate() {
        assertEquals(
            LocalDate.now().plusWeeks(2),
            DateCalculatorUtils.parseRelativeDateQuery("2 weeks"),
        )
        assertEquals(
            LocalDate.now().plusMonths(3),
            DateCalculatorUtils.parseRelativeDateQuery("3 months"),
        )
        assertEquals(
            LocalDate.now().minusDays(10),
            DateCalculatorUtils.parseRelativeDateQuery("10 days ago"),
        )
    }

    @Test
    fun parseRelativeDateQuery_ignoresNonDurationQueries() {
        assertNull(DateCalculatorUtils.parseRelativeDateQuery("march 12"))
        assertNull(DateCalculatorUtils.parseRelativeDateQuery("5 days from march 2"))
        assertNull(DateCalculatorUtils.parseRelativeDateQuery("2020"))
    }

    /** Mirrors the parser precedence [DateCalculatorHandler] relies on for bare durations. */
    @Test
    fun bareDuration_onlyMatchesTheArithmeticParser() {
        listOf("5min", "3h", "2 hours 30 minutes").forEach { query ->
            assertNull(DateCalculatorUtils.parseTimeDiffQuery(query))
            assertNull(DateCalculatorUtils.parseTimeOffsetQuery(query))
            assertNull(DateCalculatorUtils.parseAbsoluteTimeQuery(query))
            assertNull(DateCalculatorUtils.parseDateQuery(query))
            assertNotNull(DateCalculatorUtils.parseTimeArithmeticQuery(query))
        }
        listOf("2 weeks", "3 months").forEach { query ->
            assertNull(DateCalculatorUtils.parseTimeArithmeticQuery(query))
            assertNull(DateCalculatorUtils.parseDateDiffQuery(query))
            assertNull(DateCalculatorUtils.parseOffsetFromDateQuery(query))
            assertNull(DateCalculatorUtils.parseDateQuery(query))
            assertNotNull(DateCalculatorUtils.parseRelativeDateQuery(query))
        }
    }
}
