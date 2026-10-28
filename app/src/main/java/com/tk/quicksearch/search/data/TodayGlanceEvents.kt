package com.tk.quicksearch.search.data

import com.tk.quicksearch.search.data.preferences.CalendarPreferences
import com.tk.quicksearch.search.models.CalendarEventInfo
import java.time.LocalDate
import java.time.ZoneId

/** All-day events join At a Glance at this hour rather than at midnight. */
private const val ALL_DAY_EVENTS_START_HOUR = 6

/** A timed event dismissed earlier comes back this long before it starts. */
private const val TODAY_EVENT_RESHOW_LEAD_MILLIS = 30L * 60L * 1000L

/**
 * Today's events for At a Glance, shared by home and the widget: not excluded or done, not yet
 * ended, and past [todayEventVisibleFromMillis]. [hidden] holds the ones that show later today.
 * Only the widget dismisses timed events early, so only it applies those snoozes.
 */
internal class TodayGlanceEvents(
    val visible: List<CalendarEventInfo>,
    val hidden: List<CalendarEventInfo>,
    private val visibleFrom: Map<Long, Long>,
) {
    /** When the next hidden event shows, or null when none will today. */
    val nextVisibleAtMillis: Long? get() = hidden.mapNotNull { visibleFrom[it.eventId] }.minOrNull()
}

internal fun loadTodayGlanceEvents(
    repository: CalendarRepository,
    preferences: CalendarPreferences,
    nowMillis: Long = System.currentTimeMillis(),
    applySnoozes: Boolean = true,
): TodayGlanceEvents {
    val excluded = preferences.getExcludedEventIds()
    val archived = preferences.getArchivedTodayEventIds()
    val snoozed = if (applySnoozes) preferences.getSnoozedTodayEvents() else emptyMap()
    val events =
        repository.getTodayEvents()
            .filterNot { it.eventId in excluded || it.eventId in archived }
            .distinctBy { it.eventId }
            .sortedBy { it.startMillis }
            .filter { it.allDay || it.endMillis >= nowMillis }
    val visibleFrom = events.associate { it.eventId to todayEventVisibleFromMillis(it, snoozed) }
    val (visible, hidden) = events.partition { (visibleFrom[it.eventId] ?: 0L) <= nowMillis }
    return TodayGlanceEvents(visible, hidden, visibleFrom)
}

/** All-day events show from [ALL_DAY_EVENTS_START_HOUR]; a dismissed timed event shows again when its snooze ends. */
private fun todayEventVisibleFromMillis(
    event: CalendarEventInfo,
    snoozed: Map<Long, Long>,
): Long =
    if (event.allDay) {
        LocalDate.now().atTime(ALL_DAY_EVENTS_START_HOUR, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } else {
        snoozed[event.eventId] ?: 0L
    }

/**
 * Dismisses today's event [eventId] from At a Glance. A timed event dismissed more than 30 minutes
 * before it starts comes back then; dismissing it again, or dismissing an all-day event, hides it
 * for the rest of the day.
 */
internal fun dismissTodayGlanceEvent(
    repository: CalendarRepository,
    preferences: CalendarPreferences,
    eventId: Long,
    nowMillis: Long = System.currentTimeMillis(),
) {
    val event = loadTodayGlanceEvents(repository, preferences, nowMillis).visible.firstOrNull { it.eventId == eventId }
    val reshowAt = event?.takeUnless { it.allDay }?.let { it.startMillis - TODAY_EVENT_RESHOW_LEAD_MILLIS }
    if (reshowAt != null && nowMillis < reshowAt) {
        preferences.snoozeTodayEvent(eventId, reshowAt)
    } else {
        preferences.archiveTodayEvent(eventId)
    }
}
