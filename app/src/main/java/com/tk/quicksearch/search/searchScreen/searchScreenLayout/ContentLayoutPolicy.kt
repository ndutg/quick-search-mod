package com.tk.quicksearch.search.searchScreen.searchScreenLayout

import com.tk.quicksearch.search.core.SearchSection
import com.tk.quicksearch.search.core.ItemPriorityConfig
import com.tk.quicksearch.search.core.SearchSectionRegistry

internal fun homeLayoutOrder(
    baseLayoutOrder: List<ItemPriorityConfig.ItemType>,
    isReversed: Boolean,
): List<ItemPriorityConfig.ItemType> {
    val logicalOrder =
        buildList {
            add(ItemPriorityConfig.ItemType.ERROR_BANNER)
            add(ItemPriorityConfig.ItemType.APPS_SECTION)
            add(ItemPriorityConfig.ItemType.RECENT_QUERIES)
            addAll(
                baseLayoutOrder.filter { itemType ->
                    SearchSectionRegistry.sectionForItemType(itemType)
                        ?.let { it != SearchSection.APPS } == true
                },
            )
        }
    return if (isReversed) logicalOrder.reversed() else logicalOrder
}

internal fun shouldRenderStandaloneTodayAgendaBeforeApps(isReversed: Boolean): Boolean = isReversed

internal fun shouldShowSearchHistoryTitle(hasStandaloneTodayAgenda: Boolean): Boolean =
    hasStandaloneTodayAgenda

internal fun shouldSkipRegularCalendarSectionForStandaloneTodayEvents(
    section: SearchSection,
    todayCalendarEventsCount: Int,
    pinnedCalendarEventsCount: Int,
): Boolean =
    section == SearchSection.CALENDAR &&
        todayCalendarEventsCount > 0 &&
        pinnedCalendarEventsCount == 0
