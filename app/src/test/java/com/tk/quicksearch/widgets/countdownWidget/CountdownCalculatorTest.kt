package com.tk.quicksearch.widgets.countdownWidget

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class CountdownCalculatorTest {
    private val today = LocalDate.of(2026, 9, 29)

    private fun compute(
        format: CountdownFormat,
        target: LocalDate,
        nowMillis: Long = 0L,
        startMillis: Long = 0L,
    ) = CountdownCalculator.compute(format, today, target, nowMillis, startMillis, ZoneOffset.UTC)

    private fun relative(
        isFuture: Boolean,
        vararg amounts: Pair<Double, CountdownUnit>,
    ) = CountdownValue.Relative(amounts.map { CountdownAmount(it.first, it.second) }, isFuture)

    @Test
    fun `same day is today in every relative format`() {
        listOf(CountdownFormat.DAYS, CountdownFormat.YEARS, CountdownFormat.DETAILED).forEach {
            assertEquals(CountdownValue.Today, compute(it, today))
        }
    }

    @Test
    fun `days count toward future and past dates`() {
        assertEquals(relative(true, 45.0 to CountdownUnit.DAY), compute(CountdownFormat.DAYS, today.plusDays(45)))
        assertEquals(relative(false, 3.0 to CountdownUnit.DAY), compute(CountdownFormat.DAYS, today.minusDays(3)))
    }

    @Test
    fun `weeks months and years use one decimal`() {
        assertEquals(relative(true, 6.4 to CountdownUnit.WEEK), compute(CountdownFormat.WEEKS, today.plusDays(45)))
        // Sep 29 to Mar 29 is six months; the extra 15 days are about half of April.
        assertEquals(
            relative(true, 6.5 to CountdownUnit.MONTH),
            compute(CountdownFormat.MONTHS, LocalDate.of(2027, 4, 13)),
        )
        assertEquals(
            relative(false, 1.5 to CountdownUnit.YEAR),
            compute(CountdownFormat.YEARS, LocalDate.of(2025, 3, 30)),
        )
    }

    @Test
    fun `a few days never round down to zero months`() {
        assertEquals(relative(true, 0.1 to CountdownUnit.MONTH), compute(CountdownFormat.MONTHS, today.plusDays(1)))
    }

    @Test
    fun `detailed shows the two largest units and drops zeros`() {
        assertEquals(
            relative(true, 6.0 to CountdownUnit.MONTH, 5.0 to CountdownUnit.DAY),
            compute(CountdownFormat.DETAILED, LocalDate.of(2027, 4, 3)),
        )
        assertEquals(
            relative(false, 2.0 to CountdownUnit.YEAR, 6.0 to CountdownUnit.MONTH),
            compute(CountdownFormat.DETAILED, LocalDate.of(2024, 3, 20)),
        )
        assertEquals(
            relative(true, 2.0 to CountdownUnit.YEAR),
            compute(CountdownFormat.DETAILED, LocalDate.of(2028, 10, 4)),
        )
    }

    @Test
    fun `progress runs from the start time to the target midnight`() {
        val target = today.plusDays(10)
        val end = target.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val start = end - 1_000L
        assertEquals(
            CountdownValue.Progress(0.25),
            compute(CountdownFormat.PROGRESS, target, nowMillis = start + 250L, startMillis = start),
        )
        assertEquals(
            CountdownValue.Progress(1.0),
            compute(CountdownFormat.PROGRESS, target, nowMillis = end + 5L, startMillis = start),
        )
    }
}
