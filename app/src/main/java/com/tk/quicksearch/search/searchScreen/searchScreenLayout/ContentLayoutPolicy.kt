package com.tk.quicksearch.search.searchScreen.searchScreenLayout

import com.tk.quicksearch.search.core.SearchSection

internal fun shouldSkipRegularCalendarSectionForStandaloneTodayEvents(
    section: SearchSection,
    todayCalendarEventsCount: Int,
    pinnedCalendarEventsCount: Int,
): Boolean =
    section == SearchSection.CALENDAR &&
        todayCalendarEventsCount > 0 &&
        pinnedCalendarEventsCount == 0
