package com.tk.quicksearch.search.other

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.tk.quicksearch.search.core.ScreenTimeState
import java.util.Calendar
import java.util.TimeZone

class OtherSearchItemRegistryTest {
    @Test
    fun screenTimeUsesOneStablePinnedItemKey() {
        assertEquals("other:screen_time", OtherSearchItemId.SCREEN_TIME.pinnedItemKey)
        assertTrue(
            OtherSearchItemRegistry.isPinned(
                OtherSearchItemId.SCREEN_TIME,
                listOf("other:screen_time"),
            ),
        )
    }

    @Test
    fun pinnedScreenTimeLoadsOnHomeButNotForUnrelatedSearches() {
        val pinnedOrder = listOf(OtherSearchItemId.SCREEN_TIME.pinnedItemKey)

        assertTrue(
            OtherSearchItemRegistry.shouldLoad(
                OtherSearchItemId.SCREEN_TIME,
                query = "",
                pinnedItemOrder = pinnedOrder,
            ),
        )
        assertFalse(
            OtherSearchItemRegistry.shouldLoad(
                OtherSearchItemId.SCREEN_TIME,
                query = "weather",
                pinnedItemOrder = pinnedOrder,
            ),
        )
    }

    @Test
    fun screenTimeRendersForMatchingSearchOrPinnedHomeOnly() {
        val pinnedOrder = listOf(OtherSearchItemId.SCREEN_TIME.pinnedItemKey)

        assertTrue(
            OtherSearchItemRegistry.shouldRenderScreenTime(
                query = "screen time",
                pinnedItemOrder = emptyList(),
                state = ScreenTimeState.Available(0L, emptyList()),
            ),
        )
        assertTrue(
            OtherSearchItemRegistry.shouldRenderScreenTime(
                query = "",
                pinnedItemOrder = pinnedOrder,
                state = ScreenTimeState.Available(0L, emptyList()),
            ),
        )
        assertFalse(
            OtherSearchItemRegistry.shouldRenderScreenTime(
                query = "weather",
                pinnedItemOrder = pinnedOrder,
                state = ScreenTimeState.Available(0L, emptyList()),
            ),
        )
        assertFalse(
            OtherSearchItemRegistry.shouldRenderScreenTime(
                query = "screen time",
                pinnedItemOrder = pinnedOrder,
                state = ScreenTimeState.Hidden,
            ),
        )
    }

    @Test
    fun matchingScreenTimeProducesOneTopMatchCandidate() {
        assertEquals(
            listOf(OtherSearchItemId.SCREEN_TIME),
            OtherSearchItemRegistry.visibleSearchItemIds(
                query = "screen time",
                pinnedItemOrder = emptyList(),
                screenTimeState = ScreenTimeState.Available(0L, emptyList()),
            ),
        )
    }

    @Test
    fun loadingScreenTimeDoesNotRenderOrEnterTopMatches() {
        assertFalse(
            OtherSearchItemRegistry.shouldRenderScreenTime(
                query = "screen time",
                pinnedItemOrder = emptyList(),
                state = ScreenTimeState.Loading,
            ),
        )
        assertTrue(
            OtherSearchItemRegistry.visibleSearchItemIds(
                query = "screen time",
                pinnedItemOrder = emptyList(),
                screenTimeState = ScreenTimeState.Loading,
            ).isEmpty(),
        )
    }

    @Test
    fun screenTimeMatchesUsefulPartialAndFullQueries() {
        assertTrue(OtherSearchItemRegistry.matchesScreenTime("screen"))
        assertTrue(OtherSearchItemRegistry.matchesScreenTime("Screen Time"))
        assertTrue(OtherSearchItemRegistry.matchesScreenTime("phone usage today"))
    }

    @Test
    fun screenTimeDoesNotMatchBlankOrUnrelatedQueries() {
        assertFalse(OtherSearchItemRegistry.matchesScreenTime(""))
        assertFalse(OtherSearchItemRegistry.matchesScreenTime("weather"))
    }

    @Test
    fun screenTimeDayStartsAtMidnightInCurrentTimeZone() {
        val timeZone = TimeZone.getTimeZone("America/New_York")
        val now =
            Calendar.getInstance(timeZone).apply {
                set(2026, Calendar.AUGUST, 14, 17, 42, 19)
                set(Calendar.MILLISECOND, 321)
            }.timeInMillis
        val expected =
            Calendar.getInstance(timeZone).apply {
                set(2026, Calendar.AUGUST, 14, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

        assertEquals(expected, startOfCurrentDayMillis(now, timeZone))
    }

    @Test
    fun screenTimeUsesOnlyForegroundAppTimeWhileScreenIsInteractive() {
        val hour = 60L * 60L * 1000L
        val events =
            listOf(
                DeviceUsageEvent.ScreenState(-hour, isInteractive = true),
                DeviceUsageEvent.ActivityState(0L, "app.one", "one", isForeground = true),
                DeviceUsageEvent.ScreenState(hour, isInteractive = false),
                DeviceUsageEvent.ScreenState(2 * hour, isInteractive = true),
                DeviceUsageEvent.ActivityState(3 * hour, "app.one", "one", isForeground = false),
            )

        assertEquals(
            2 * hour,
            calculateForegroundAppUsageDurationMillis(
                startMillis = 0L,
                endMillis = 4 * hour,
                events = events,
            ),
        )
    }

    @Test
    fun screenTimeTracksDurationForEachForegroundApp() {
        val hour = 60L * 60L * 1000L
        val usage =
            calculateForegroundAppUsage(
                startMillis = 0L,
                endMillis = 4 * hour,
                events =
                    listOf(
                        DeviceUsageEvent.ScreenState(-hour, isInteractive = true),
                        DeviceUsageEvent.ActivityState(0L, "app.one", "one", isForeground = true),
                        DeviceUsageEvent.ActivityState(2 * hour, "app.two", "two", isForeground = true),
                        DeviceUsageEvent.ActivityState(3 * hour, "launcher", "home", isForeground = true),
                    ),
                excludedPackages = setOf("launcher"),
            )

        assertEquals(3 * hour, usage.totalDurationMillis)
        assertEquals(2 * hour, usage.durationByPackage["app.one"])
        assertEquals(hour, usage.durationByPackage["app.two"])
        assertFalse(usage.durationByPackage.containsKey("launcher"))
    }

    @Test
    fun screenTimeInfersStateBeforeFirstTransition() {
        val hour = 60L * 60L * 1000L

        assertEquals(
            hour,
            calculateForegroundAppUsageDurationMillis(
                startMillis = 0L,
                endMillis = 3 * hour,
                events =
                    listOf(
                        DeviceUsageEvent.ScreenState(-hour, isInteractive = true),
                        DeviceUsageEvent.ActivityState(
                            hour,
                            "app.one",
                            "one",
                            isForeground = false,
                        ),
                    ),
            ),
        )
    }

    @Test
    fun screenTimeExcludesLauncherSessions() {
        val hour = 60L * 60L * 1000L
        val events =
            listOf(
                DeviceUsageEvent.ScreenState(-hour, isInteractive = true),
                DeviceUsageEvent.ActivityState(0L, "launcher", "home", isForeground = true),
                DeviceUsageEvent.ActivityState(hour, "launcher", "home", isForeground = false),
                DeviceUsageEvent.ActivityState(hour, "app.one", "one", isForeground = true),
            )

        assertEquals(
            hour,
            calculateForegroundAppUsageDurationMillis(
                startMillis = 0L,
                endMillis = 2 * hour,
                events = events,
                excludedPackages = setOf("launcher"),
            ),
        )
    }

    @Test
    fun screenTimeDoesNotCarryStaleActivityStateAcrossMidnight() {
        val hour = 60L * 60L * 1000L
        val events =
            listOf(
                DeviceUsageEvent.ScreenState(-hour, isInteractive = true),
                DeviceUsageEvent.ActivityState(-hour, "stale.app", "stale", isForeground = true),
                DeviceUsageEvent.ActivityState(hour, "app.one", "one", isForeground = true),
                DeviceUsageEvent.ActivityState(2 * hour, "app.one", "one", isForeground = false),
            )

        assertEquals(
            hour,
            calculateForegroundAppUsageDurationMillis(
                startMillis = 0L,
                endMillis = 3 * hour,
                events = events,
            ),
        )
    }

    @Test
    fun launcherResumeReplacesPreviouslyForegroundApp() {
        val hour = 60L * 60L * 1000L
        val events =
            listOf(
                DeviceUsageEvent.ScreenState(-hour, isInteractive = true),
                DeviceUsageEvent.ActivityState(0L, "app.one", "one", isForeground = true),
                DeviceUsageEvent.ActivityState(hour, "launcher", "home", isForeground = true),
            )

        assertEquals(
            hour,
            calculateForegroundAppUsageDurationMillis(
                startMillis = 0L,
                endMillis = 3 * hour,
                events = events,
                excludedPackages = setOf("launcher"),
            ),
        )
    }

    @Test
    fun screenOffClearsStaleForegroundActivity() {
        val hour = 60L * 60L * 1000L
        val events =
            listOf(
                DeviceUsageEvent.ScreenState(-hour, isInteractive = true),
                DeviceUsageEvent.ActivityState(0L, "app.one", "one", isForeground = true),
                DeviceUsageEvent.ScreenState(hour, isInteractive = false),
                DeviceUsageEvent.ScreenState(2 * hour, isInteractive = true),
            )

        assertEquals(
            hour,
            calculateForegroundAppUsageDurationMillis(
                startMillis = 0L,
                endMillis = 3 * hour,
                events = events,
            ),
        )
    }

    @Test
    fun screenTimeNeverExceedsElapsedTime() {
        val hour = 60L * 60L * 1000L
        val duplicateTransitions =
            listOf(
                DeviceUsageEvent.ScreenState(-hour, isInteractive = true),
                DeviceUsageEvent.ActivityState(0L, "app.one", "one", isForeground = true),
                DeviceUsageEvent.ActivityState(hour, "app.one", "one", isForeground = true),
                DeviceUsageEvent.ActivityState(2 * hour, "app.one", "one", isForeground = true),
            )

        assertEquals(
            3 * hour,
            calculateForegroundAppUsageDurationMillis(
                startMillis = 0L,
                endMillis = 3 * hour,
                events = duplicateTransitions,
            ),
        )
    }
}
