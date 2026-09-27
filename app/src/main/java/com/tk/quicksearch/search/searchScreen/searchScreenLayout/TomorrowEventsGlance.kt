package com.tk.quicksearch.search.searchScreen.searchScreenLayout

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tk.quicksearch.R
import com.tk.quicksearch.search.core.intentHelpers.IntentHelpers
import com.tk.quicksearch.search.data.CalendarRepository
import com.tk.quicksearch.search.data.preferences.CalendarPreferences
import com.tk.quicksearch.search.data.preferences.GlancePreferences
import com.tk.quicksearch.search.models.CalendarEventInfo
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Tomorrow's all-day events appear from this hour of the evening until midnight. */
private const val TOMORROW_EVENTS_START_HOUR = 21

/** Tomorrow's all-day calendar events for the home At a Glance card, shown in the evening. */
internal class TomorrowEventsGlance(
    val events: List<CalendarEventInfo>,
    val open: (CalendarEventInfo) -> Unit,
    /** Hides the event for tonight; it still shows as one of today's events tomorrow. */
    val dismiss: (CalendarEventInfo) -> Unit,
)

/**
 * Reads tomorrow's all-day events from [TOMORROW_EVENTS_START_HOUR] until midnight while [enabled],
 * today's events are on and calendar access is granted, skipping excluded and dismissed events. It
 * reads again on resume and when the evening starts or ends while the screen stays open.
 */
@Composable
internal fun rememberTomorrowEventsGlance(enabled: Boolean): TomorrowEventsGlance {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember(appContext) { CalendarRepository(appContext) }
    val calendarPreferences = remember(appContext) { CalendarPreferences(appContext) }
    val glancePreferences = remember(appContext) { GlancePreferences(appContext) }
    val refreshKey = rememberResumeRefreshKey()
    var events by remember { mutableStateOf<List<CalendarEventInfo>>(emptyList()) }

    LaunchedEffect(enabled, refreshKey) {
        while (true) {
            val now = LocalDateTime.now()
            val tomorrow = now.toLocalDate().plusDays(1)
            events =
                if (
                    enabled &&
                    now.hour >= TOMORROW_EVENTS_START_HOUR &&
                    calendarPreferences.getShowTodayEvents() &&
                    repository.hasPermission()
                ) {
                    withContext(Dispatchers.IO) {
                        val excluded = calendarPreferences.getExcludedEventIds()
                        val dismissed = glancePreferences.getDismissedTomorrowEvents(tomorrow.toString())
                        repository.getAllDayEventsOn(tomorrow)
                            .filterNot { it.eventId in excluded || it.eventId in dismissed }
                            .distinctBy { it.eventId }
                    }
                } else {
                    emptyList()
                }
            delay(millisUntilNextBoundary(now))
        }
    }

    return TomorrowEventsGlance(
        events = events,
        open = { event ->
            IntentHelpers.openCalendarEvent(
                context = appContext as Application,
                eventId = event.eventId,
                calendarPackageName = calendarPreferences.getDefaultCalendarPackage(),
            )
        },
        dismiss = { event ->
            glancePreferences.dismissTomorrowEvent(LocalDate.now().plusDays(1).toString(), event.eventId)
            events = events.filterNot { it.eventId == event.eventId }
        },
    )
}

/** Time until the evening window opens (today at the start hour) or closes (midnight). */
private fun millisUntilNextBoundary(now: LocalDateTime): Long {
    val next =
        if (now.hour < TOMORROW_EVENTS_START_HOUR) {
            now.toLocalDate().atTime(LocalTime.of(TOMORROW_EVENTS_START_HOUR, 0))
        } else {
            now.toLocalDate().plusDays(1).atStartOfDay()
        }
    val zone = ZoneId.systemDefault()
    val millis = next.atZone(zone).toInstant().toEpochMilli() - now.atZone(zone).toInstant().toEpochMilli()
    // A second past the boundary so the next read lands on its far side.
    return millis.coerceAtLeast(0L) + 1_000L
}

@Composable
internal fun TomorrowEventRow(
    event: CalendarEventInfo,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlanceStatusRow(
        icon = {
            Icon(
                imageVector = Icons.Rounded.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        title = event.title,
        subtitle = stringResource(R.string.calendar_relative_tomorrow),
        onClick = onClick,
        onDismiss = onDismiss,
    )
}
