package com.tk.quicksearch.search.searchScreen.searchScreenLayout

import com.tk.quicksearch.search.core.SearchSection
import com.tk.quicksearch.search.core.ItemPriorityConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentLayoutPolicyTest {
    @Test
    fun homeLayoutPlacesAppsThenHistoryThenPinnedSections() {
        assertEquals(
            listOf(
                ItemPriorityConfig.ItemType.ERROR_BANNER,
                ItemPriorityConfig.ItemType.APPS_SECTION,
                ItemPriorityConfig.ItemType.RECENT_QUERIES,
                ItemPriorityConfig.ItemType.APP_SHORTCUTS_SECTION,
                ItemPriorityConfig.ItemType.CONTACTS_SECTION,
                ItemPriorityConfig.ItemType.FILES_SECTION,
                ItemPriorityConfig.ItemType.CALENDAR_SECTION,
                ItemPriorityConfig.ItemType.NOTES_SECTION,
                ItemPriorityConfig.ItemType.SETTINGS_SECTION,
                ItemPriorityConfig.ItemType.APP_SETTINGS_SECTION,
            ),
            homeLayoutOrder(ItemPriorityConfig.APP_OPEN_STATE_LAYOUT, isReversed = false),
        )
    }

    @Test
    fun oneHandedHomeLayoutReversesEveryHomeItem() {
        val regularOrder = homeLayoutOrder(ItemPriorityConfig.APP_OPEN_STATE_LAYOUT, isReversed = false)

        assertEquals(
            regularOrder.reversed(),
            homeLayoutOrder(ItemPriorityConfig.APP_OPEN_STATE_LAYOUT, isReversed = true),
        )
        assertTrue(shouldRenderStandaloneTodayAgendaBeforeApps(isReversed = true))
        assertFalse(shouldRenderStandaloneTodayAgendaBeforeApps(isReversed = false))
    }

    @Test
    fun searchHistoryTitleIsOnlyShownWithStandaloneTodayAgenda() {
        assertTrue(shouldShowSearchHistoryTitle(hasStandaloneTodayAgenda = true))
        assertFalse(shouldShowSearchHistoryTitle(hasStandaloneTodayAgenda = false))
    }

    @Test
    fun regularCalendarSlotIsSkippedWhenTodayEventsHaveNoPinnedCalendarEvents() {
        assertTrue(
            shouldSkipRegularCalendarSectionForStandaloneTodayEvents(
                section = SearchSection.CALENDAR,
                todayCalendarEventsCount = 1,
                pinnedCalendarEventsCount = 0,
            ),
        )
    }

    @Test
    fun regularCalendarSlotStaysForPinnedCalendarEvents() {
        assertFalse(
            shouldSkipRegularCalendarSectionForStandaloneTodayEvents(
                section = SearchSection.CALENDAR,
                todayCalendarEventsCount = 1,
                pinnedCalendarEventsCount = 1,
            ),
        )
    }
}
