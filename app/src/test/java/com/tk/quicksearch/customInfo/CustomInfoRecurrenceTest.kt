package com.tk.quicksearch.customInfo

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomInfoRecurrenceTest {
    private val zone = ZoneId.of("America/New_York")

    private fun millis(dateTime: String): Long = LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli()

    private fun next(repeat: CustomInfoRepeat, anchor: String, after: String): LocalDateTime {
        val result = repeat.nextRunAfter(millis(anchor), millis(after), zone)
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(result), zone)
    }

    @Test
    fun beforeAnchorReturnsAnchor() {
        assertEquals(
            LocalDateTime.parse("2026-10-01T08:00"),
            next(CustomInfoRepeat.DAILY, "2026-10-01T08:00", "2026-09-27T12:00"),
        )
    }

    @Test
    fun atAnchorMovesToNextRun() {
        assertEquals(
            LocalDateTime.parse("2026-10-02T08:00"),
            next(CustomInfoRepeat.DAILY, "2026-10-01T08:00", "2026-10-01T08:00"),
        )
    }

    @Test
    fun missedRunsAreSkipped() {
        assertEquals(
            LocalDateTime.parse("2026-10-22T08:00"),
            next(CustomInfoRepeat(CustomInfoRepeatUnit.DAY, 3), "2026-10-01T08:00", "2026-10-19T09:00"),
        )
    }

    @Test
    fun weeklyKeepsWeekdayAndTime() {
        assertEquals(
            LocalDateTime.parse("2026-10-15T08:00"),
            next(CustomInfoRepeat(CustomInfoRepeatUnit.WEEK, 2), "2026-10-01T08:00", "2026-10-02T08:00"),
        )
    }

    @Test
    fun monthlyOnThe31stClampsAndReturns() {
        val repeat = CustomInfoRepeat.MONTHLY
        assertEquals(
            LocalDateTime.parse("2027-02-28T08:00"),
            next(repeat, "2027-01-31T08:00", "2027-01-31T09:00"),
        )
        assertEquals(
            LocalDateTime.parse("2027-03-31T08:00"),
            next(repeat, "2027-01-31T08:00", "2027-02-28T09:00"),
        )
    }

    @Test
    fun yearlyOnLeapDay() {
        assertEquals(
            LocalDateTime.parse("2029-02-28T08:00"),
            next(CustomInfoRepeat.YEARLY, "2028-02-29T08:00", "2028-03-01T08:00"),
        )
    }

    @Test
    fun dailyHoldsLocalTimeAcrossDaylightSaving() {
        // US clocks fall back on 2026-11-01.
        assertEquals(
            LocalDateTime.parse("2026-11-02T08:00"),
            next(CustomInfoRepeat.DAILY, "2026-10-30T08:00", "2026-11-01T09:00"),
        )
    }

    @Test
    fun dailyInSpringForwardGapShiftsOnlyThatDay() {
        // US clocks spring forward on 2027-03-14; 02:30 doesn't exist that day.
        val repeat = CustomInfoRepeat.DAILY
        assertEquals(
            LocalDateTime.parse("2027-03-14T03:30"),
            next(repeat, "2027-03-10T02:30", "2027-03-13T03:00"),
        )
        assertEquals(
            LocalDateTime.parse("2027-03-15T02:30"),
            next(repeat, "2027-03-10T02:30", "2027-03-14T03:30"),
        )
    }

    @Test
    fun everyTwoMonthsFromThe31stClampsEachRun() {
        val repeat = CustomInfoRepeat(CustomInfoRepeatUnit.MONTH, 2)
        assertEquals(
            LocalDateTime.parse("2027-04-30T08:00"),
            next(repeat, "2026-12-31T08:00", "2027-02-28T09:00"),
        )
        assertEquals(
            LocalDateTime.parse("2027-06-30T08:00"),
            next(repeat, "2026-12-31T08:00", "2027-04-30T08:00"),
        )
        assertEquals(
            LocalDateTime.parse("2027-08-31T08:00"),
            next(repeat, "2026-12-31T08:00", "2027-06-30T08:00"),
        )
    }

    @Test
    fun runJustBeforeNextOccurrenceReturnsThatOccurrence() {
        assertEquals(
            LocalDateTime.parse("2026-10-08T08:00"),
            next(CustomInfoRepeat.WEEKLY, "2026-10-01T08:00", "2026-10-08T07:59"),
        )
    }

    @Test
    fun longAfterAnchorStillLandsOnSchedule() {
        assertEquals(
            LocalDateTime.parse("2036-10-01T08:00"),
            next(CustomInfoRepeat(CustomInfoRepeatUnit.YEAR, 5), "2026-10-01T08:00", "2031-10-01T08:00"),
        )
        assertEquals(
            LocalDateTime.parse("2031-10-02T08:00"),
            next(CustomInfoRepeat.DAILY, "2026-10-01T08:00", "2031-10-01T08:00"),
        )
    }

    @Test
    fun oneTimeItemHasNoNextRun() {
        val item = CustomInfoItem(
            id = 1,
            title = "t",
            prompt = "p",
            providerId = com.tk.quicksearch.tools.aiSearch.AiSearchLlmProviderId.GEMINI,
            modelId = "m",
            webSearch = false,
            thinking = false,
            dueMillis = millis("2026-10-01T08:00"),
            sendNotification = false,
        )
        assertEquals(null, item.nextDueAfter(millis("2026-10-01T08:01")))
    }
}
